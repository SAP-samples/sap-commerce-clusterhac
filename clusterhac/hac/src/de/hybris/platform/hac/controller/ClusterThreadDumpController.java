package de.hybris.platform.hac.controller;

import com.google.common.base.Charsets;
import com.sap.commerce.clusterhac.data.ClusterThreadDumpInfoResult;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.ClusterThreadDumpInfoExecutorService;
import com.sap.commerce.clusterhac.service.ClusterThreadDumpService;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.hac.controller.threaddump.ThreadDumpAnalyzer;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/cluster/thread-dump")
public class ClusterThreadDumpController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SCRIPT_DIRECTORY = "classpath:scripts/thread-dump/";

  private static final String nodeGroupAll = "all";

  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;
  @Resource
  private ClusterThreadDumpService clusterThreadDumpService;
  @Resource
  private ResourceLoader resourceLoader;
  @Resource
  private HttpServletRequest httpServletRequest;
  @Resource
  private ClusterThreadDumpInfoExecutorService clusterThreadDumpInfoExecutorService;


  @GetMapping
  public String threadDumpView(final Model model,
      @RequestParam(required = false) String nodeId,
      @RequestParam(required = false) String host) {
    model.addAttribute("pageTitle", "Record Thread Dumps");
    List<String> nodeGroups = new ArrayList<String>();
    nodeGroups.add(nodeGroupAll);
    nodeGroups.addAll(getClusterNodeGroups());

    model.addAttribute("nodeGroups", nodeGroups);
    // Optional node context when arriving from the Overview node strip (pre-selects/highlights
    // that node); thread-dump recording itself remains node-group scoped.
    model.addAttribute("nodeId", nodeId);
    model.addAttribute("host", host);
    return "thread-dump";
  }

  @PostMapping("/start")
  @ResponseBody
  public void startThreadDumpRecording(@RequestParam long interval,
      @RequestParam(required = false) Collection<String> clusterNodeGroups,
      @RequestParam(required = false) Collection<String> nodeIds) {
    Function<String, String> withInterval =
        content -> content.replaceAll("\\$interval", String.valueOf(interval));
    // Pod-level sub-selection takes precedence.
    if (nodeIds != null && !nodeIds.isEmpty()) {
      LOG.debug("startThreadDumpRecording for node ids {}", nodeIds);
      executeClusterScriptOnNodes("start-recording.groovy", withInterval, nodeIds);
      return;
    }
    LOG.debug(
        "startThreadDumpRecording request receiver for  Cluster Node Groups" + clusterNodeGroups);
    if (clusterNodeGroups != null && clusterNodeGroups.contains(nodeGroupAll)) {
      clusterNodeGroups = getClusterNodeGroups();
    }
    executeClusterScript("start-recording.groovy", withInterval, clusterNodeGroups);
  }

  @PostMapping("/stop")
  @ResponseBody
  public void stopThreadDumpRecording() {
    executeClusterScript("stop-recording.groovy");
  }

  @PostMapping("/save-media")
  @ResponseBody
  public void saveThreadDumpMedia(@RequestParam(required = false) Collection<String> clusterNodeGroups,
      @RequestParam(required = false) Collection<String> nodeIds) {
    if (nodeIds != null && !nodeIds.isEmpty()) {
      LOG.debug("saveThreadDumpMedia for node ids {}", nodeIds);
      executeClusterScriptOnNodes("stop-recording.groovy", null, nodeIds);
      executeClusterScriptOnNodes("save-media.groovy", null, nodeIds);
      return;
    }
    LOG.debug("saveThreadDumpMedia request receiver for Cluster Node Groups" + clusterNodeGroups);
    if (clusterNodeGroups == null || clusterNodeGroups.isEmpty() || clusterNodeGroups.contains(nodeGroupAll)) {
      clusterNodeGroups = getClusterNodeGroups();
    }
    // Stop and save must target the SAME groups the user selected — otherwise stopping all
    // groups would halt recording the user never asked to stop.
    executeClusterScript("stop-recording.groovy", clusterNodeGroups);
    executeClusterScript("save-media.groovy", clusterNodeGroups);
  }

  @PostMapping("/clean-up-media")
  @ResponseBody
  public void cleanUpThreadDumpMedia() {
    clusterThreadDumpService.cleanUpThreadDumpMedia();
  }

  @GetMapping("/results")
  @ResponseBody
  public List<ThreadDumpMediaData> getThreadDumpFiles() {
    return clusterThreadDumpService.findThreadDumpMedia().stream()
        .map(
            media -> new ThreadDumpMediaData(httpServletRequest.getHeader(HttpHeaders.HOST), media))
        .collect(Collectors.toList());
  }

  @GetMapping(value = "/dumpinfos", produces = "application/json")
  @ResponseBody
  public Map<String, String> clusterThreadDumpInfo() throws IOException {
    String uuid = clusterThreadDumpInfoExecutorService.submit();
    return Map.of("uuid", uuid);
  }

  private static final int MAX_ANALYZE_DUMPS = 5;

  /**
   * Analyzes one or more saved thread-dump Media (each a zip of jstack snapshots) in place — no
   * download, no cluster round-trip. Streams the media bytes and returns a compact summary: a
   * per-dump trend, state histogram, top threads by CPU, top stack frames, BLOCKED/contention,
   * and threads stuck across all pooled snapshots. Up to {@value #MAX_ANALYZE_DUMPS} dumps.
   */
  @GetMapping(value = "/analyze", produces = "application/json")
  @ResponseBody
  public ThreadDumpAnalyzer.Analysis analyze(@RequestParam("code") List<String> codes) {
    ThreadDumpAnalyzer.Analysis err = new ThreadDumpAnalyzer.Analysis();
    if (codes == null || codes.isEmpty()) {
      err.error = "No dump selected.";
      return err;
    }
    if (codes.size() > MAX_ANALYZE_DUMPS) {
      err.error = "Select up to " + MAX_ANALYZE_DUMPS + " dumps (got " + codes.size() + ").";
      return err;
    }
    // Order the selected dumps by creation time so the trend + first/last CPU delta are chronological.
    Map<String, java.util.Date> created = new HashMap<>();
    for (MediaModel m : clusterThreadDumpService.findThreadDumpMedia()) {
      created.put(m.getCode(), m.getCreationtime());
    }
    List<String> ordered = new ArrayList<>(codes);
    ordered.sort(Comparator.comparing(c -> created.getOrDefault(c, new java.util.Date(0))));

    List<ThreadDumpAnalyzer.NamedStream> streams = new ArrayList<>();
    try {
      for (String code : ordered) {
        InputStream in = clusterThreadDumpService.streamThreadDumpMedia(code);
        if (in == null) {
          err.error = "Thread-dump media not found: " + code;
          return err;
        }
        java.util.Date c = created.get(code);
        String label = c != null ? String.format("%1$tF %1$tT", c) : code;
        streams.add(new ThreadDumpAnalyzer.NamedStream(label, in));
      }
      ThreadDumpAnalyzer.Analysis result = ThreadDumpAnalyzer.analyze(streams);
      // Time span across the selected dumps (for the CPU-delta "window" note).
      java.util.Date first = created.get(ordered.get(0));
      java.util.Date lastD = created.get(ordered.get(ordered.size() - 1));
      if (first != null && lastD != null) {
        result.spanMinutes = Math.max(0, (lastD.getTime() - first.getTime()) / 60000L);
      }
      return result;
    } catch (Exception e) {
      LOG.error("Failed to analyze thread-dump media {}", ordered, e);
      err.error = e.getMessage() == null ? "analysis failed" : e.getMessage();
      return err;
    } finally {
      for (ThreadDumpAnalyzer.NamedStream ns : streams) {
        try { if (ns.stream != null) { ns.stream.close(); } } catch (IOException ignored) {}
      }
    }
  }

  @RequestMapping(value = "/dumpinfos/results", headers = "Accept=application/json", method = RequestMethod.GET)
  @ResponseBody
  public Collection<ClusterThreadDumpInfoResult> clusterThreadDumpInfoResults(
      @RequestParam String uuid) {
    return getThreadDumpInfoResults(uuid);
  }

  private Collection<ClusterThreadDumpInfoResult> getThreadDumpInfoResults(String uuid) {
    // Return whatever has arrived; the client keeps polling this endpoint (no blocking wait).
    Collection<ClusterThreadDumpInfoResult> clusterThreadDumpInfoResult =
        clusterThreadDumpInfoExecutorService.getClusterEvaluationResults(uuid);
    if (clusterThreadDumpInfoResult != null && !clusterThreadDumpInfoResult.isEmpty()) {
      return clusterThreadDumpInfoResult;
    }
    return CollectionUtils.emptyCollection();
  }

  private void executeClusterScript(String name) {
    executeClusterScript(name, null, getClusterNodeGroups());
  }

  private void executeClusterScript(String name, Collection<String> nodeGroups) {
    executeClusterScript(name, null, nodeGroups);
  }

  private void executeClusterScript(String name,
      Function<String, String> contentPostProcessingFunction
      , Collection<String> nodeGroups) {
    try (var inputStream = resourceLoader.getResource(SCRIPT_DIRECTORY + name).getInputStream()) {
      var content = IOUtils.toString(inputStream, Charsets.UTF_8);
      if (contentPostProcessingFunction != null) {
        content = contentPostProcessingFunction.apply(content);
      }
      clusterScriptExecutorService.execute(content, nodeGroups);
    } catch (IOException e) {
      throw new RuntimeException("Cannot read resource content", e);
    }
  }

  /** Run a thread-dump script on an explicit set of node IDs (pod-level sub-selection). */
  private void executeClusterScriptOnNodes(String name,
      Function<String, String> contentPostProcessingFunction, Collection<String> nodeIds) {
    try (var inputStream = resourceLoader.getResource(SCRIPT_DIRECTORY + name).getInputStream()) {
      var content = IOUtils.toString(inputStream, Charsets.UTF_8);
      if (contentPostProcessingFunction != null) {
        content = contentPostProcessingFunction.apply(content);
      }
      clusterScriptExecutorService.executeOnNodes(content, nodeIds);
    } catch (IOException e) {
      throw new RuntimeException("Cannot read resource content", e);
    }
  }

  static class ThreadDumpMediaData {

    private static final String MEDIA_URL_FORMAT = "https://%s%s";
    private static final String MEDIA_CREATION_TIME_FORMAT = "%1$tF %1$tT";

    private final String url;
    private final String creationTime;
    private final String code;

    private ThreadDumpMediaData(String host, MediaModel media) {
      this.url = String.format(MEDIA_URL_FORMAT, host, media.getURL());
      this.creationTime = String.format(MEDIA_CREATION_TIME_FORMAT, media.getCreationtime());
      this.code = media.getCode();
    }

    public String getUrl() {
      return url;
    }

    public String getCreationTime() {
      return creationTime;
    }

    public String getCode() {
      return code;
    }
  }
}
