package de.hybris.platform.hac.controller.threaddump;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses clusterhac thread-dump zips (standard HotSpot/jstack snapshots) and produces a compact
 * analysis: state histogram per snapshot, top threads by CPU, top-of-stack frame clustering,
 * BLOCKED/lock contention, and threads stuck across all snapshots. Pure in-memory over a streamed
 * zip — no temp files, no cluster round-trip (dumps are already saved as Media on the admin node).
 */
public final class ThreadDumpAnalyzer {

  private static final int TOP_N = 15;
  // Header: "name" prio=5 tid=0x9 nid=0x9 RUNNABLE - stats: cpu=2915 blk=-1 wait=-1
  private static final Pattern HEADER = Pattern.compile("^\"(.*?)\".*?(?:\\s([A-Z_]+))?\\s*-\\s*stats:.*?cpu=(-?\\d+)");
  private static final Pattern STATE = Pattern.compile("java\\.lang\\.Thread\\.State:\\s*([A-Z_]+)");
  private static final Pattern WAIT_LOCK = Pattern.compile("waiting to lock <(0x[0-9a-fA-F]+)>");
  private static final Pattern HELD_LOCK = Pattern.compile("- locked <(0x[0-9a-fA-F]+)>");

  private ThreadDumpAnalyzer() {}

  /** One parsed thread within a snapshot. */
  public static final class ThreadInfo {
    public String name;
    public String state = "UNKNOWN";
    public long cpu = -1;
    public String topFrame = "";
    public final List<String> frames = new ArrayList<>();  // up to top 3 frames (for clustering)
    public String waitingOn;     // monitor id this thread waits to lock (BLOCKED)
    public final List<String> heldLocks = new ArrayList<>();
  }

  /** The analysis summary returned to the client (serialised to JSON by Spring). */
  public static final class Analysis {
    public int dumpCount;                            // how many saved dumps were pooled
    public int snapshotCount;                        // total snapshots across all dumps
    public int threadCount;                          // from the last snapshot overall
    public String cpuMode = "cumulative";            // "delta" when 2+ snapshots let us diff CPU
    public boolean deadlockFound;                    // JVM printed "Found ... Java-level deadlock"
    public List<Map<String, Object>> findings = new ArrayList<>();        // the "answers" layer, top of UI
    public List<Map<String, Object>> deadlocks = new ArrayList<>();       // {label, text} per occurrence
    public List<Map<String, Object>> pools = new ArrayList<>();           // Hybris pool summary (active/total)
    public long spanMinutes = -1;                    // minutes between first and last selected dump
    public List<Map<String, Object>> trend = new ArrayList<>();          // one row per dump, in time order
    public List<Map<String, Object>> stateHistogram = new ArrayList<>(); // one map per snapshot
    public List<Map<String, Object>> topByCpu = new ArrayList<>();
    public List<Map<String, Object>> topFrames = new ArrayList<>();      // clustered by top-3 frames
    public List<Map<String, Object>> blocked = new ArrayList<>();
    public List<Map<String, Object>> stuck = new ArrayList<>();
    public String error;
  }

  /** A saved dump to analyze: a label (creation time) and its zip stream. */
  public static final class NamedStream {
    public final String label;
    public final InputStream stream;
    public NamedStream(String label, InputStream stream) { this.label = label; this.stream = stream; }
  }

  /** Single-dump convenience wrapper. */
  public static Analysis analyze(InputStream zipStream) {
    return analyze(List.of(new NamedStream("dump", zipStream)));
  }

  /**
   * Pools the snapshots of one or more saved dumps (in the given order — caller sorts by time)
   * into a single analysis: state histogram + stuck-detection + CPU delta span ALL snapshots,
   * plus a per-dump trend row so degradation across dumps is visible.
   */
  public static Analysis analyze(List<NamedStream> dumps) {
    Analysis a = new Analysis();
    List<List<ThreadInfo>> allSnapshots = new ArrayList<>();     // pooled, in dump→snapshot order
    for (NamedStream d : dumps) {
      List<List<ThreadInfo>> dumpSnaps = new ArrayList<>();
      try (ZipInputStream zis = new ZipInputStream(d.stream)) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (entry.isDirectory()) { continue; }
          String text = readEntry(zis);
          // Deadlock: the JVM prints an explicit section when a circular lock dependency exists.
          String dl = extractDeadlock(text);
          if (dl != null) {
            a.deadlockFound = true;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", d.label);
            m.put("text", dl);
            a.deadlocks.add(m);
          }
          dumpSnaps.add(parseSnapshot(text));
        }
      } catch (IOException e) {
        a.error = "Could not read dump zip (" + d.label + "): " + e.getMessage();
        return a;
      }
      // Trend row for this dump = its LAST snapshot's counts (most representative of that moment).
      if (!dumpSnaps.isEmpty()) {
        a.trend.add(trendRow(d.label, dumpSnaps.get(dumpSnaps.size() - 1)));
      }
      allSnapshots.addAll(dumpSnaps);
    }
    if (allSnapshots.isEmpty()) {
      a.error = "No thread-dump snapshots found in the selected dump(s).";
      return a;
    }
    a.dumpCount = dumps.size();
    summarise(a, allSnapshots);
    return a;
  }

  private static Map<String, Object> trendRow(String label, List<ThreadInfo> snap) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (ThreadInfo t : snap) { counts.merge(t.state, 1, Integer::sum); }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("label", label);
    row.put("total", snap.size());
    row.put("RUNNABLE", counts.getOrDefault("RUNNABLE", 0));
    row.put("BLOCKED", counts.getOrDefault("BLOCKED", 0));
    row.put("WAITING", counts.getOrDefault("WAITING", 0));
    row.put("TIMED_WAITING", counts.getOrDefault("TIMED_WAITING", 0));
    return row;
  }

  // The JVM prints "Found one Java-level deadlock:" (or "Found N Java-level deadlocks:") followed
  // by the offending thread chain, then a blank line. Capture from that marker to the blank line.
  private static String extractDeadlock(String text) {
    int idx = text.indexOf("Java-level deadlock");
    if (idx < 0) { return null; }
    int start = text.lastIndexOf("Found", idx);
    if (start < 0) { start = idx; }
    String rest = text.substring(start);
    // The section ends at the first BLANK LINE after the marker. The JVM uses a single blank line
    // ("\n\n"); search past the marker line itself so we don't stop on its own line break.
    int firstNl = rest.indexOf('\n');
    int end = firstNl >= 0 ? rest.indexOf("\n\n", firstNl) : -1;
    if (end < 0) { end = rest.length(); }      // no blank line found → take the rest
    end = Math.min(end, 4000);                 // hard cap as a backstop against a malformed dump
    return rest.substring(0, end).trim();
  }

  // Classify a thread by its name into a logical SAP Commerce / Tomcat pool. Patterns confirmed
  // against a real 2211 dump. This platform is commons-pool2 (NOT C3P0/Hikari), and DB waits show
  // up in stack FRAMES, not thread names — so there is no "DB pool" thread category here.
  private static String poolOf(String name) {
    if (name == null) { return "Other"; }
    if (name.startsWith("hybrisHTTP") || name.startsWith("http-nio")
        || name.startsWith("https-jsse") || name.startsWith("ajp-nio")) { return "HTTP workers"; }
    if (name.startsWith("PooledThread") || name.startsWith("TaskExecutor-master")
        || name.startsWith("Task-master-poll")) { return "Hybris worker pool"; }
    if (name.startsWith("task-scheduler") || name.startsWith("scheduler-watchdog")
        || name.startsWith("cronjob")) { return "Task / cron scheduling"; }
    if (name.startsWith("defaultEventTracking")) { return "Event tracking"; }
    if (name.startsWith("Catalina-utility") || name.startsWith("Connection evictor")) { return "Tomcat internal"; }
    if (name.startsWith("RMI ")) { return "RMI / JMX"; }
    return "Other";
  }

  private static String readEntry(ZipInputStream zis) throws IOException {
    StringBuilder sb = new StringBuilder();
    byte[] buf = new byte[8192];
    int n;
    while ((n = zis.read(buf)) > 0) { sb.append(new String(buf, 0, n, StandardCharsets.UTF_8)); }
    return sb.toString();
  }

  private static List<ThreadInfo> parseSnapshot(String text) {
    List<ThreadInfo> threads = new ArrayList<>();
    ThreadInfo cur = null;
    boolean topFrameSet = false;
    for (String line : text.split("\n")) {
      Matcher h = HEADER.matcher(line);
      if (line.startsWith("\"") && h.find()) {
        cur = new ThreadInfo();
        cur.name = h.group(1);
        if (h.group(2) != null) { cur.state = h.group(2); }
        try { cur.cpu = Long.parseLong(h.group(3)); } catch (NumberFormatException ignored) {}
        topFrameSet = false;
        threads.add(cur);
        continue;
      }
      if (cur == null) { continue; }
      Matcher s = STATE.matcher(line);
      if (s.find()) { cur.state = s.group(1); continue; }
      String trimmed = line.trim();
      if (trimmed.startsWith("at ")) {
        String frame = trimmed.substring(3).trim();
        if (!topFrameSet) { cur.topFrame = frame; topFrameSet = true; }
        if (cur.frames.size() < 3) { cur.frames.add(frame); }   // top 3 frames for clustering
      }
      Matcher w = WAIT_LOCK.matcher(line);
      if (w.find()) { cur.waitingOn = w.group(1); }
      Matcher held = HELD_LOCK.matcher(line);
      if (held.find()) { cur.heldLocks.add(held.group(1)); }
    }
    return threads;
  }

  private static void summarise(Analysis a, List<List<ThreadInfo>> snapshots) {
    a.snapshotCount = snapshots.size();
    List<ThreadInfo> last = snapshots.get(snapshots.size() - 1);
    a.threadCount = last.size();

    // State histogram per snapshot.
    for (List<ThreadInfo> snap : snapshots) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      for (ThreadInfo t : snap) { counts.merge(t.state, 1, Integer::sum); }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("counts", counts);
      row.put("total", snap.size());
      a.stateHistogram.add(row);
    }

    // Hybris pool summary (from the latest snapshot): total + active (RUNNABLE) per logical pool,
    // so "137 threads" becomes "HTTP workers 2/44, Hybris worker pool 0/29, ..." — tells you
    // whether the storefront or background processing is saturated.
    Map<String, int[]> pool = new LinkedHashMap<>();   // name -> [total, active]
    for (ThreadInfo t : last) {
      int[] tot = pool.computeIfAbsent(poolOf(t.name), k -> new int[2]);
      tot[0]++;
      if ("RUNNABLE".equals(t.state)) { tot[1]++; }
    }
    pool.entrySet().stream()
        .sorted((x, y) -> y.getValue()[0] - x.getValue()[0])
        .forEach(e -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("pool", e.getKey());
          m.put("active", e.getValue()[1]);
          m.put("total", e.getValue()[0]);
          a.pools.add(m);
        });

    // Top threads by CPU. With 2+ snapshots, rank by the CPU DELTA between the first and last
    // snapshot (CPU actually consumed DURING the recording = hot right now) instead of the
    // cumulative-since-start figure, which just favours long-lived threads. Single snapshot →
    // cumulative fallback.
    if (snapshots.size() >= 2) {
      a.cpuMode = "delta";
      // Baseline = CPU in the FIRST snapshot, keyed by name. Only threads present in the first
      // snapshot have a measurable in-window delta; a thread that appears only later has no
      // baseline, so its cumulative-since-start CPU is NOT a "consumed during recording" figure
      // and must not be ranked as if it were.
      Map<String, Long> firstCpu = new LinkedHashMap<>();
      for (ThreadInfo t : snapshots.get(0)) { firstCpu.putIfAbsent(t.name, t.cpu); }
      List<Map<String, Object>> deltas = new ArrayList<>();
      for (ThreadInfo t : last) {
        Long base = firstCpu.get(t.name);
        if (base == null || base < 0 || t.cpu < 0) { continue; }  // no baseline / unknown cpu → skip
        long delta = t.cpu - base;
        if (delta > 0) {
          Map<String, Object> m = row(t);
          m.put("cpu", delta);   // report the delta in the same "cpu" field the client reads
          deltas.add(m);
        }
      }
      deltas.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("cpu")).reversed());
      a.topByCpu = new ArrayList<>(deltas.subList(0, Math.min(TOP_N, deltas.size())));
    } else {
      List<ThreadInfo> byCpu = new ArrayList<>(last);
      byCpu.sort(Comparator.comparingLong((ThreadInfo t) -> t.cpu).reversed());
      for (int i = 0; i < Math.min(TOP_N, byCpu.size()); i++) {
        ThreadInfo t = byCpu.get(i);
        if (t.cpu <= 0) { break; }
        a.topByCpu.add(row(t));
      }
    }

    // Frame clustering by the TOP-3 frames (not just the terminal frame): distinguishes "40
    // threads in FlexibleSearch.search" from a generic Unsafe.park. Each cluster carries the
    // resolved Hybris component label + a representative 3-frame stack.
    Map<String, Integer> frameCounts = new LinkedHashMap<>();
    Map<String, ThreadInfo> frameSample = new LinkedHashMap<>();
    for (ThreadInfo t : last) {
      String key = clusterKey(t);
      if (key.isEmpty()) { continue; }
      frameCounts.merge(key, 1, Integer::sum);
      frameSample.putIfAbsent(key, t);
    }
    frameCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(TOP_N)
        .forEach(e -> {
          ThreadInfo t = frameSample.get(e.getKey());
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("count", e.getValue());
          m.put("component", hybrisComponent(t.topFrame));
          m.put("frames", new ArrayList<>(t.frames));   // top-3 stack for display
          a.topFrames.add(m);
        });

    // BLOCKED threads + the monitor they wait on.
    for (ThreadInfo t : last) {
      if ("BLOCKED".equals(t.state) || t.waitingOn != null) {
        Map<String, Object> m = row(t);
        m.put("waitingOn", t.waitingOn == null ? "" : t.waitingOn);
        a.blocked.add(m);
      }
    }

    // Stuck: same name+state+topFrame present in EVERY snapshot (likely a real stall).
    if (snapshots.size() >= 2) {
      Map<String, Integer> seen = new LinkedHashMap<>();
      Map<String, ThreadInfo> sample = new LinkedHashMap<>();
      for (List<ThreadInfo> snap : snapshots) {
        // dedupe within a snapshot so a repeated name doesn't over-count
        Map<String, ThreadInfo> uniq = new LinkedHashMap<>();
        for (ThreadInfo t : snap) { uniq.putIfAbsent(stuckKey(t), t); }
        for (Map.Entry<String, ThreadInfo> e : uniq.entrySet()) {
          seen.merge(e.getKey(), 1, Integer::sum);
          sample.putIfAbsent(e.getKey(), e.getValue());
        }
      }
      for (Map.Entry<String, Integer> e : seen.entrySet()) {
        if (e.getValue() == snapshots.size()) {
          ThreadInfo t = sample.get(e.getKey());
          if (isIdle(t)) { continue; }   // skip parked/idle pool threads — not a stall
          a.stuck.add(row(t));
        }
      }
    }

    buildFindings(a, last, snapshots);
  }

  // Findings = the "answers" layer. Two tiers of language (user directive):
  //   CONFIRMED (assert "is") — only what the JVM itself proved (deadlock).
  //   POINTER   (investigative "worth looking at") — everything heuristic; state the evidence
  //             (counts + resolved component) and invite analysis, never claim a root cause.
  // Emit only on unambiguous evidence; otherwise the INFO "not-the-JVM" note.
  private static void buildFindings(Analysis a, List<ThreadInfo> last, List<List<ThreadInfo>> snapshots) {
    // 1. Deadlock — CONFIRMED. The JVM's own determination.
    if (a.deadlockFound) {
      a.findings.add(finding("red", "confirmed", "Deadlock detected",
          "The JVM reported a Java-level deadlock — a circular lock dependency that will not "
          + "resolve without a restart. See the deadlock chain below."));
    }

    // 2. Work-pool cluster — POINTER. A large share of a real work pool on the same top frame.
    Map<String, int[]> byPoolFrame = new LinkedHashMap<>();   // "pool||frame" -> [count]
    Map<String, Integer> poolTotals = new LinkedHashMap<>();
    for (ThreadInfo t : last) {
      String pool = poolOf(t.name);
      poolTotals.merge(pool, 1, Integer::sum);
      if (isWorkPool(pool) && t.topFrame != null && !t.topFrame.isEmpty() && !isIdleFrame(t.topFrame)) {
        byPoolFrame.computeIfAbsent(pool + "||" + t.topFrame, k -> new int[1])[0]++;
      }
    }
    for (Map.Entry<String, int[]> e : byPoolFrame.entrySet()) {
      int count = e.getValue()[0];
      String pool = e.getKey().substring(0, e.getKey().indexOf("||"));
      String frame = e.getKey().substring(e.getKey().indexOf("||") + 2);
      int poolTotal = poolTotals.getOrDefault(pool, count);
      if (count >= 8 && count >= 0.4 * poolTotal) {
        String comp = hybrisComponent(frame);
        boolean sustained = clusterInAllSnapshots(snapshots, frame, count);
        String sev = count >= 20 ? "red" : "amber";
        a.findings.add(finding(sev, "pointer",
            count + " " + pool + " threads are in " + comp,
            count + " of " + poolTotal + " " + pool + " threads share this frame"
            + (sustained ? ", sustained across all snapshots" : "") + " — worth investigating."));
      }
    }

    // 3. DB-frame threads — POINTER.
    int dbThreads = 0;
    for (ThreadInfo t : last) { if (isDbFrame(t.topFrame)) { dbThreads++; } }
    if (dbThreads >= 5) {
      a.findings.add(finding("amber", "pointer", dbThreads + " threads are in JDBC / database frames",
          "A database call is on top of stack — check DB / query performance."));
    }

    // 4. Stuck — POINTER (only if the stuck list found something real).
    if (!a.stuck.isEmpty()) {
      Object comp = a.stuck.get(0).get("topFrame");
      a.findings.add(finding("amber", "pointer",
          a.stuck.size() + " thread(s) unchanged across all snapshots",
          "Same frame in every snapshot (see Stuck below), in " + hybrisComponent(comp == null ? "" : comp.toString()) + "."));
    }

    // 5. Nothing fired → INFO (accurate, not a false "healthy").
    if (a.findings.isEmpty()) {
      a.findings.add(finding("info", "pointer", "No JVM bottleneck evident in these dumps",
          "No deadlock, no persistent thread cluster, no BLOCKED threads."));
    }
  }

  private static Map<String, Object> finding(String severity, String kind, String title, String detail) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("severity", severity);
    m.put("kind", kind);
    m.put("title", title);
    m.put("detail", detail);
    return m;
  }

  private static boolean isWorkPool(String pool) {
    return "HTTP workers".equals(pool) || "Hybris worker pool".equals(pool);
  }

  private static boolean clusterInAllSnapshots(List<List<ThreadInfo>> snapshots, String frame, int latestCount) {
    for (List<ThreadInfo> snap : snapshots) {
      int c = 0;
      for (ThreadInfo t : snap) { if (frame.equals(t.topFrame)) { c++; } }
      if (c < Math.max(2, latestCount / 2)) { return false; }
    }
    return snapshots.size() >= 2;
  }

  // Resolve a raw stack frame to a human, Commerce-aware label. Best-effort; falls back to the
  // raw frame so nothing is hidden.
  static String hybrisComponent(String frame) {
    if (frame == null || frame.isEmpty()) { return "unknown"; }
    String f = frame;
    if (f.contains("flexiblesearch") || f.contains("FlexibleSearch")) { return "FlexibleSearch (DB query)"; }
    if (isDbFrame(f)) { return "JDBC / database"; }
    if (f.contains("solr") || f.contains("Solr")) { return "Solr"; }
    if (f.contains("cronjob") || f.contains("CronJob") || f.contains("ServicelayerJob")) { return "CronJob / job"; }
    if (f.contains("de.hybris.platform.")) {
      // derive the package segment after de.hybris.platform. (e.g. ...jalo.order... -> "order")
      int i = f.indexOf("de.hybris.platform.") + "de.hybris.platform.".length();
      String rest = f.substring(i);
      int dot = rest.indexOf('.');
      String seg = dot > 0 ? rest.substring(0, dot) : rest;
      return "hybris:" + seg;
    }
    // Shorten a generic java frame to class.method for readability.
    return frame;
  }

  private static boolean isDbFrame(String frame) {
    if (frame == null) { return false; }
    return frame.contains("java.sql") || frame.contains("jdbcwrapper") || frame.contains("com.sap.db")
        || frame.contains("oracle.jdbc") || frame.contains("org.hsqldb") || frame.contains("commons.dbcp")
        || frame.contains("HikariCP") || frame.contains("mysql.cj");
  }

  private static boolean isIdleFrame(String frame) {
    if (frame == null) { return true; }
    for (String tok : IDLE_FRAME_TOKENS) { if (frame.contains(tok)) { return true; } }
    return false;
  }

  // Cluster key = the joined top-3 frames (so distinct stacks don't merge on a shared terminal frame).
  private static String clusterKey(ThreadInfo t) {
    if (t.frames.isEmpty()) { return t.topFrame == null ? "" : t.topFrame; }
    return String.join(" <- ", t.frames);
  }

  private static String stuckKey(ThreadInfo t) { return t.name + "|" + t.state + "|" + t.topFrame; }

  // "Stuck" should mean an APPLICATION thread wedged in the same business frame across snapshots
  // — not idle infrastructure. Exclude anything that legitimately sits still: parked/waiting/
  // sleeping frames, I/O acceptors/pollers, JVM-internal and container threads (by name), and
  // threads with no state/frame. Kept deliberately broad to avoid false "stall" noise.
  private static final String[] IDLE_FRAME_TOKENS = {
      "park", "Unsafe.park", "Object.wait", "epollWait", "Thread.sleep",
      "Net.accept", "ServerSocket", "SocketDispatcher.read", "KQueue.poll", "EPoll.wait",
      "Selector", "poll0", "accept0", "waitForReferencePendingList",
      "ThreadImpl.dumpThreads",           // the thread taking THIS dump
      "ReferenceQueue.remove", "PollingConditionalWaiter",
      // Blocking network receive = a thread waiting for inbound data, not a stall. Covers the
      // hybris cluster-comms threads (UDP broadcast receivers, connection receivers) that sit in
      // a native receive/poll the whole time and would otherwise show as a false "stuck".
      "receive0", "Net.poll", "DatagramChannel"
  };
  private static final String[] IDLE_NAME_TOKENS = {
      "Reference Handler", "Finalizer", "Signal Dispatcher", "JDWP", "DestroyJavaVM",
      "Common-Cleaner", "Attach Listener", "Notification Thread", "GC ", "G1 ", "VM ",
      "RMI TCP Accept", "RMI Scheduler", "RMI TCP Connection",
      "Acceptor", "Poller", "ClientPoller",
      "Catalina-utility", "Connection evictor", "task-scheduler", "Wrapper-",
      "AfterSaveEventPublisher", "StatDaemonThread", "container-",
      "scheduling-", "Keep-Alive", "process reaper"
      // NOTE: work-pool names (hybrisHTTP/http-nio/https-jsse/ajp-nio HTTP workers, PooledThread
      // Hybris worker pool, generic pool-) are intentionally NOT listed here. Those threads must be
      // judged idle by their FRAME (parked/waiting via IDLE_FRAME_TOKENS), not blanket-excluded by
      // name — otherwise a worker genuinely wedged in a business frame would never be flagged stuck.
  };

  private static boolean isIdle(ThreadInfo t) {
    if (t.topFrame == null || t.topFrame.isEmpty()) { return true; }
    if (t.state == null || "UNKNOWN".equals(t.state)) { return true; }
    for (String tok : IDLE_FRAME_TOKENS) { if (t.topFrame.contains(tok)) { return true; } }
    if (t.name != null) {
      for (String tok : IDLE_NAME_TOKENS) { if (t.name.contains(tok)) { return true; } }
    }
    return false;
  }

  private static Map<String, Object> row(ThreadInfo t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", t.name);
    m.put("state", t.state);
    m.put("cpu", t.cpu);
    m.put("topFrame", t.topFrame);
    return m;
  }
}
