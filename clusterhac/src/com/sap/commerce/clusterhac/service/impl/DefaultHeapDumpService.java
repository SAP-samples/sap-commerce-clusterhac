package com.sap.commerce.clusterhac.service.impl;

import com.sap.commerce.clusterhac.service.HeapDumpService;
import de.hybris.platform.util.Config;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link HeapDumpService}. On the local node it: (1) creates a FIFO (named pipe) under the
 * Hybris temp dir; (2) spawns a SEPARATE uploader JVM ({@link AzureBlobPipeStreamer#main}) that reads
 * the FIFO and streams it to Azure Blob Storage; (3) triggers {@code jcmd <own-pid> GC.heap_dump
 * -gz=<n> -overwrite <fifo>} so the gzipped dump flows through the pipe to Azure — the full dump
 * never lands on local disk. Mirrors the reference {@code azure-jmap} workflow.
 *
 * <p>The uploader runs in a child process, not an in-JVM thread, on purpose: a heap dump pauses all
 * threads of the dumping JVM at a safepoint, so an in-process reader draining the same FIFO would
 * deadlock. The config snapshot ({@link DumpConfig}) is resolved on the tenant-bound event thread
 * and handed to the worker thread, which has no active tenant and must not touch {@code Config}.
 */
public class DefaultHeapDumpService implements HeapDumpService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultHeapDumpService.class);

  static final String P_CONNECTION_STRING = "clusterhac.heapdump.azure.connection-string";
  /** Fallback source when the heap-dump connection string is blank: the CCv2 hotfolder account. */
  static final String P_HOTFOLDER_CONNECTION_STRING = "azure.hotfolder.storage.account.connection-string";
  static final String P_CONTAINER = "clusterhac.heapdump.azure.container";
  static final String P_GZIP = "clusterhac.heapdump.gzip";
  static final String DEFAULT_CONTAINER = "memory-dump-container";
  static final int DEFAULT_GZIP = 1;

  private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

  private final AtomicReference<HeapDumpState> state = new AtomicReference<>(HeapDumpState.idle());

  @Override
  public boolean isConfigured() {
    return StringUtils.isNotBlank(connectionString())
        && StringUtils.isNotBlank(container());
  }

  @Override
  public HeapDumpState getState() {
    return state.get();
  }

  @Override
  public void dumpToAzure(String triggeredBy) {
    if (state.get().getStatus() == Status.IN_PROGRESS) {
      LOG.info("Heap dump already in progress on this node; ignoring trigger.");
      return;
    }
    if (!isConfigured()) {
      state.set(new HeapDumpState(Status.FAILED, null, null, 0L, now(), now(),
          "Azure storage not configured (" + P_CONNECTION_STRING + " / " + P_CONTAINER + ")."));
      LOG.warn("Heap dump requested but Azure storage is not configured.");
      return;
    }
    // Resolve all tenant-scoped config HERE, on the event thread (which has an active tenant). The
    // worker thread below is a plain thread with no tenant bound, so it must not call Config.*.
    final DumpConfig cfg = new DumpConfig(connectionString(), container(), gzipLevel(), tempDir(),
        hostName(), sanitize(triggeredBy));
    state.set(new HeapDumpState(Status.IN_PROGRESS, null, null, 0L, now(), null, "Starting…"));
    // Run off the event thread: the JVM freezes while dumping, so the cluster-event thread must not
    // block on it.
    Thread worker = new Thread(() -> runDump(cfg), "clusterhac-heapdump");
    worker.setDaemon(true);
    worker.start();
  }

  private void runDump(DumpConfig cfg) {
    Path fifo = null;
    try {
      LOG.info("Heap dump worker started: host={}, container={}, gzip={}, tempDir={}",
          cfg.host, cfg.container, cfg.gzip, cfg.tempDir);
      fifo = createFifo(cfg.tempDir);
      LOG.info("Heap dump FIFO created at {}", fifo);

      // The FIFO reader/uploader MUST run in a separate JVM: an in-process reader cannot drain a
      // FIFO that this same JVM is heap-dumping into — the dump's safepoint blocks the reader thread
      // and the two deadlock. So we spawn a child java process (reusing our classpath, which already
      // has the Azure SDK + our uploader class) to read the FIFO → Azure, then trigger jcmd here.
      Process uploader = startUploaderProcess(cfg, fifo);

      // Trigger jcmd on its own thread: if the uploader dies before opening the FIFO (e.g. bad
      // classpath, Azure error), jcmd's write-open would block forever — so we must be able to
      // detect the dead child and unblock. jcmd writes to the FIFO; the uploader reads it.
      final Path pipe = fifo;
      final java.util.concurrent.atomic.AtomicReference<Throwable> jcmdError =
          new java.util.concurrent.atomic.AtomicReference<>();
      Thread jcmdThread = new Thread(() -> {
        try {
          triggerHeapDump(pipe, cfg.gzip);
          LOG.info("jcmd GC.heap_dump returned");
        } catch (Throwable t) {
          jcmdError.set(t);
        }
      }, "clusterhac-heapdump-jcmd");
      jcmdThread.setDaemon(true);
      jcmdThread.start();

      // Phase: streaming. Surface it so the status table shows movement during the (possibly
      // minutes-long) dump+upload instead of a bare "Starting…".
      setPhase("Dumping heap and streaming to Azure…", 0L);

      // Read the uploader's output to completion (this drives the FIFO drain, parses live progress)
      // and get its exit.
      String resultLine = readUploaderResult(uploader);
      int exit = uploader.waitFor();
      LOG.info("Uploader exited {} with result line: {}", exit, resultLine);

      // The uploader has finished; jcmd should be done too. If jcmd is somehow still blocked (child
      // died before opening the pipe), unblock its write-open by opening the read end, then join.
      jcmdThread.join(5000);
      if (jcmdThread.isAlive()) {
        try (InputStream drain = Files.newInputStream(pipe)) {
          drain.readAllBytes();
        } catch (IOException ignored) {
          // best effort unblock
        }
        jcmdThread.join(5000);
      }
      if (jcmdError.get() != null) {
        LOG.warn("jcmd GC.heap_dump reported an error", jcmdError.get());
      }

      Result parsed = parseResult(resultLine);
      if (exit != 0 || parsed == null || !parsed.ok) {
        String msg = parsed != null && parsed.message != null ? parsed.message
            : (jcmdError.get() != null ? String.valueOf(jcmdError.get().getMessage())
                : "uploader exited " + exit);
        throw new IOException("Heap-dump upload failed: " + msg);
      }
      state.set(new HeapDumpState(Status.DONE, parsed.blobName, parsed.blobUrl, parsed.bytes,
          state.get().getStartedAt(), now(), "Heap dump streamed to Azure."));
      LOG.info("Heap dump complete: blob={}, bytes={}", parsed.blobName, parsed.bytes);
    } catch (Throwable e) {
      LOG.error("Heap dump failed", e);
      state.set(new HeapDumpState(Status.FAILED, null, null, state.get().getBytesStreamed(),
          state.get().getStartedAt(), now(),
          e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    } finally {
      if (fifo != null) {
        try {
          Files.deleteIfExists(fifo);
        } catch (IOException ignored) {
          // best effort
        }
      }
    }
  }

  /** Launches the child uploader JVM (reads FIFO → Azure) on the current classpath. */
  private Process startUploaderProcess(DumpConfig cfg, Path fifo) throws IOException {
    String javaBin = Paths.get(System.getProperty("java.home"), "bin",
        System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java")
        .toString();
    String classpath = buildUploaderClasspath();
    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin);
    cmd.add("-cp");
    cmd.add(classpath);
    cmd.add(AzureBlobPipeStreamer.class.getName());
    cmd.add(cfg.connectionString);
    cmd.add(cfg.container);
    cmd.add(fifo.toString());
    cmd.add(cfg.host);
    cmd.add(cfg.triggeredBy);
    LOG.info("Starting heap-dump uploader process: {} -cp <{} chars> {} <conn> {} {} {} {}",
        javaBin, classpath.length(), AzureBlobPipeStreamer.class.getName(), cfg.container,
        fifo, cfg.host, cfg.triggeredBy);
    return new ProcessBuilder(cmd).redirectErrorStream(true).start();
  }

  /**
   * Builds the classpath for the child uploader JVM. {@code java.class.path} is NOT reliable under
   * Commerce/Tomcat (it is often just the bootstrap jar — the real classpath is assembled by
   * Commerce's own classloaders at runtime), so the classpath is derived from the actual on-disk
   * locations of the classes the uploader needs: our own class plus the Azure Blob SDK and its
   * transitive dependencies (reactor/netty), discovered via each class's {@code CodeSource}.
   */
  private static String buildUploaderClasspath() {
    // Representative classes spanning everything the uploader loads: our streamer, the Azure blob +
    // common + core SDK, reactor-netty, and SLF4J. Resolving each to its jar/dir and adding the
    // whole containing directory pulls in the rest of the Azure/netty jars that sit beside them.
    Class<?>[] anchors = {
        AzureBlobPipeStreamer.class,
        com.azure.storage.blob.BlobClientBuilder.class,
        com.azure.storage.blob.specialized.AppendBlobClient.class,
        com.azure.storage.common.StorageSharedKeyCredential.class,
        com.azure.core.util.Context.class,
        org.slf4j.Logger.class,
    };
    java.util.LinkedHashSet<String> entries = new java.util.LinkedHashSet<>();
    for (Class<?> c : anchors) {
      try {
        var cs = c.getProtectionDomain().getCodeSource();
        if (cs == null || cs.getLocation() == null) {
          continue;
        }
        Path loc = Paths.get(cs.getLocation().toURI());
        entries.add(loc.toString());
        // If it's a jar, add every jar in its directory (the Azure/netty jars live together).
        if (loc.getFileName() != null && loc.getFileName().toString().endsWith(".jar")
            && loc.getParent() != null) {
          entries.add(loc.getParent().toString() + java.io.File.separator + "*");
        }
      } catch (Exception e) {
        LOG.debug("Could not resolve code source for {}", c, e);
      }
    }
    return String.join(java.io.File.pathSeparator, entries);
  }

  /**
   * Reads the uploader's stdout to completion, updating live byte progress from
   * {@code CLUSTERHAC_HEAPDUMP_PROGRESS} lines, and returns the {@code CLUSTERHAC_HEAPDUMP_RESULT}
   * payload (or null).
   */
  private String readUploaderResult(Process uploader) throws IOException {
    String result = null;
    try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(
        uploader.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith(AzureBlobPipeStreamer.PROGRESS_PREFIX)) {
          try {
            long bytes = Long.parseLong(
                line.substring(AzureBlobPipeStreamer.PROGRESS_PREFIX.length()).trim());
            setPhase("Streaming to Azure…", bytes);
          } catch (NumberFormatException ignored) {
            // ignore malformed progress line
          }
          continue;
        }
        LOG.info("[heapdump-uploader] {}", line);
        if (line.startsWith(AzureBlobPipeStreamer.RESULT_PREFIX)) {
          result = line.substring(AzureBlobPipeStreamer.RESULT_PREFIX.length()).trim();
        }
      }
    }
    return result;
  }

  /** Updates the in-progress state's message + live byte count (keeps status "high touch"). */
  private void setPhase(String message, long bytesStreamed) {
    HeapDumpState s = state.get();
    state.set(new HeapDumpState(Status.IN_PROGRESS, s.getBlobName(), s.getBlobUrl(), bytesStreamed,
        s.getStartedAt(), null, message));
  }

  /** Parsed form of the uploader's result line. */
  private static final class Result {

    boolean ok;
    String blobName;
    String blobUrl;
    long bytes;
    String message;
  }

  private static Result parseResult(String line) {
    if (line == null) {
      return null;
    }
    Result r = new Result();
    // OK <blobName> <bytes> <blobUrl>   |   ERR <message>
    if (line.startsWith("OK ")) {
      String[] parts = line.substring(3).trim().split("\\s+", 3);
      r.ok = true;
      r.blobName = parts.length > 0 ? parts[0] : null;
      try {
        r.bytes = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
      } catch (NumberFormatException ignored) {
        r.bytes = 0L;
      }
      r.blobUrl = parts.length > 2 ? parts[2] : null;
    } else if (line.startsWith("ERR ")) {
      r.ok = false;
      r.message = line.substring(4).trim();
    } else {
      r.ok = false;
      r.message = line;
    }
    return r;
  }

  /** Config snapshot resolved on the tenant-bound event thread and handed to the worker thread. */
  private static final class DumpConfig {

    final String connectionString;
    final String container;
    final int gzip;
    final Path tempDir;
    final String host;
    final String triggeredBy;

    DumpConfig(String connectionString, String container, int gzip, Path tempDir, String host,
        String triggeredBy) {
      this.connectionString = connectionString;
      this.container = container;
      this.gzip = gzip;
      this.tempDir = tempDir;
      this.host = host;
      this.triggeredBy = triggeredBy;
    }
  }

  /** Keep a name segment blob-path-safe; {@code unknown} when null/blank. */
  private static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.replaceAll("[^A-Za-z0-9._-]", "-");
  }

  /**
   * Triggers {@code jcmd <pid> GC.heap_dump -gz=<level> -overwrite <pipe>} against this JVM, writing
   * into the FIFO. Uses the {@code jcmd} binary from {@code java.home/bin} (present in the JDK that
   * runs Commerce, incl. CCv2). This is the only route that supports both gzip AND a FIFO target:
   * the DiagnosticCommand MBean does not expose {@code GC.heap_dump} as an invokable operation, and
   * {@link com.sun.management.HotSpotDiagnosticMXBean#dumpHeap} refuses a pre-existing FIFO and has
   * no gzip option. Package-private and overridable so tests can skip the real dump.
   */
  void triggerHeapDump(Path pipe, int gzip) {
    try {
      String jcmd = jcmdPath();
      long pid = ProcessHandle.current().pid();
      List<String> cmd = new ArrayList<>();
      cmd.add(jcmd);
      cmd.add(Long.toString(pid));
      cmd.add("GC.heap_dump");
      if (gzip > 0) {
        cmd.add("-gz=" + gzip);
      }
      cmd.add("-overwrite");
      cmd.add(pipe.toString());

      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      // Drain jcmd's own stdout/stderr (it prints "Dumping heap..." / "Heap dump file created").
      String output;
      try (InputStream os = p.getInputStream()) {
        output = new String(os.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
      }
      int exit = p.waitFor();
      if (exit != 0) {
        throw new IOException("jcmd GC.heap_dump exited " + exit + ": " + output);
      }
      LOG.info("jcmd GC.heap_dump: {}", output);
    } catch (Exception e) {
      throw new RuntimeException("GC.heap_dump invocation failed", e);
    }
  }

  /** Locates the {@code jcmd} binary in the running JDK ({@code java.home/bin[/jcmd[.exe]]}). */
  private static String jcmdPath() {
    String javaHome = System.getProperty("java.home");
    String exe = System.getProperty("os.name", "").toLowerCase().contains("win")
        ? "jcmd.exe" : "jcmd";
    Path candidate = Paths.get(javaHome, "bin", exe);
    return Files.isExecutable(candidate) ? candidate.toString() : exe;
  }

  Path createFifo(Path dir) throws IOException, InterruptedException {
    Files.createDirectories(dir);
    sweepStaleFifos(dir);
    Path fifo = dir.resolve("clusterhac-heapdump-" + System.currentTimeMillis() + ".fifo");
    Files.deleteIfExists(fifo);
    Process p = new ProcessBuilder("mkfifo", fifo.toString()).redirectErrorStream(true).start();
    if (p.waitFor() != 0) {
      throw new IOException("mkfifo failed for " + fifo);
    }
    return fifo;
  }

  /** Removes leftover FIFO nodes from crashed/killed runs so empty pipe nodes don't accumulate. */
  private static void sweepStaleFifos(Path dir) {
    try (var stream = Files.newDirectoryStream(dir, "clusterhac-heapdump-*.fifo")) {
      for (Path stale : stream) {
        try {
          Files.deleteIfExists(stale);
          LOG.info("Removed stale heap-dump FIFO {}", stale);
        } catch (IOException e) {
          LOG.debug("Could not remove stale FIFO {}", stale, e);
        }
      }
    } catch (IOException e) {
      LOG.debug("Could not sweep stale FIFOs in {}", dir, e);
    }
  }

  /** Resolves the temp dir. Called on the tenant-bound event thread (reads {@code Config}). */
  private Path tempDir() {
    String hybrisTemp = Config.getString("HYBRIS_TEMP_DIR", null);
    if (StringUtils.isNotBlank(hybrisTemp)) {
      return Paths.get(hybrisTemp, "clusterhac");
    }
    return Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "clusterhac");
  }

  private static String now() {
    return TS.format(new Date());
  }

  private static String hostName() {
    try {
      return InetAddress.getLocalHost().getCanonicalHostName();
    } catch (Exception e) {
      String env = System.getenv("HOSTNAME");
      return env != null ? env : "node";
    }
  }

  /**
   * The Azure connection string to stream to: the heap-dump-specific property, or — when that is
   * blank — the CCv2 hotfolder storage account connection string as a fallback.
   */
  private String connectionString() {
    String own = Config.getString(P_CONNECTION_STRING, "");
    if (StringUtils.isNotBlank(own)) {
      return own;
    }
    return Config.getString(P_HOTFOLDER_CONNECTION_STRING, "");
  }

  private String container() {
    return Config.getString(P_CONTAINER, DEFAULT_CONTAINER);
  }

  private int gzipLevel() {
    try {
      return Config.getInt(P_GZIP, DEFAULT_GZIP);
    } catch (Exception e) {
      return DEFAULT_GZIP;
    }
  }
}
