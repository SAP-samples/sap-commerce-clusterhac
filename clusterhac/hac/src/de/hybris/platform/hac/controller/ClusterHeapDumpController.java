package de.hybris.platform.hac.controller;

import com.google.common.base.Charsets;
import com.sap.commerce.clusterhac.data.ClusterHeapDumpInfoResult;
import com.sap.commerce.clusterhac.service.ClusterHeapDumpInfoExecutorService;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.HeapDumpService;
import de.hybris.platform.servicelayer.user.UserService;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * HAC tab for capturing JVM heap dumps and streaming them to Azure Blob Storage. A heap dump
 * freezes the JVM, so it is always targeted at explicitly selected pods — never a node group or the
 * whole cluster. Trigger is fire-and-forget on the chosen nodes; live per-pod status (including the
 * resulting Azure blob name) is reported via the {@code /dumpinfos} round-trip.
 */
@Controller
@RequestMapping("/cluster/heap-dump")
public class ClusterHeapDumpController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String SCRIPT = "classpath:scripts/heap-dump/heap-dump.groovy";

  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;
  @Resource
  private ClusterHeapDumpInfoExecutorService clusterHeapDumpInfoExecutorService;
  @Resource
  private HeapDumpService heapDumpService;
  @Resource
  private ResourceLoader resourceLoader;
  @Resource
  private UserService userService;

  @GetMapping
  public String heapDumpView(final Model model,
      @RequestParam(required = false) String nodeId,
      @RequestParam(required = false) String host) {
    model.addAttribute("pageTitle", "Cluster Heap Dump");
    // Optional node context when arriving from the Overview node strip (pre-selects that pod).
    model.addAttribute("nodeId", nodeId);
    model.addAttribute("host", host);
    return "heap-dump";
  }

  /**
   * Triggers a heap dump on the explicitly selected pods. Pod selection is mandatory: an empty
   * {@code nodeIds} is rejected (heap dump is never broadcast to a node group).
   */
  @PostMapping(value = "/trigger", produces = "application/json")
  @ResponseBody
  public Map<String, Object> trigger(@RequestParam(required = false) Collection<String> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return Map.of("ok", false, "error", "Select at least one pod. Heap dump is pod-targeted.");
    }
    if (!heapDumpService.isConfigured()) {
      return Map.of("ok", false, "configured", false,
          "error", "Azure storage is not configured. Set clusterhac.heapdump.azure.* properties.");
    }
    LOG.info("Triggering heap dump on nodeIds={}", nodeIds);
    // Resolve the HAC user HERE (the target pod has no HTTP session) and carry it into the script so
    // the blob name records who triggered the dump.
    String triggeredBy = resolveTriggeringUser();
    try (var inputStream = resourceLoader.getResource(SCRIPT).getInputStream()) {
      var content = IOUtils.toString(inputStream, Charsets.UTF_8)
          .replace("$triggeredBy", triggeredBy);
      clusterScriptExecutorService.executeOnNodes(content, nodeIds);
    } catch (IOException e) {
      throw new RuntimeException("Cannot read heap-dump script", e);
    }
    return Map.of("ok", true, "configured", true, "nodeIds", nodeIds);
  }

  /** The HAC user who triggered the dump, sanitized for a blob name; {@code unknown} if unresolved. */
  private String resolveTriggeringUser() {
    String uid = null;
    try {
      uid = userService.getCurrentUser() != null ? userService.getCurrentUser().getUid() : null;
    } catch (Exception e) {
      LOG.debug("Could not resolve current user for heap-dump blob name", e);
    }
    if (uid == null || uid.isBlank()) {
      return "unknown";
    }
    // Keep the blob name path-safe: replace anything but [A-Za-z0-9._-] with '-'.
    return uid.replaceAll("[^A-Za-z0-9._-]", "-");
  }

  @GetMapping(value = "/config-status", produces = "application/json")
  @ResponseBody
  public Map<String, Boolean> configStatus() {
    return Map.of("configured", heapDumpService.isConfigured());
  }

  @GetMapping(value = "/dumpinfos", produces = "application/json")
  @ResponseBody
  public Map<String, String> clusterHeapDumpInfo() {
    return Map.of("uuid", clusterHeapDumpInfoExecutorService.submit());
  }

  @GetMapping(value = "/dumpinfos/results", produces = "application/json")
  @ResponseBody
  public Collection<ClusterHeapDumpInfoResult> clusterHeapDumpInfoResults(
      @RequestParam String uuid) {
    // Return whatever has arrived; the client keeps polling (no blocking wait).
    Collection<ClusterHeapDumpInfoResult> results =
        clusterHeapDumpInfoExecutorService.getClusterEvaluationResults(uuid);
    return results != null && !results.isEmpty() ? results : CollectionUtils.emptyCollection();
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @org.springframework.web.bind.annotation.ExceptionHandler(RuntimeException.class)
  @ResponseBody
  public Map<String, Object> handle(RuntimeException e) {
    LOG.error("Heap dump controller error", e);
    return Map.of("ok", false, "error", String.valueOf(e.getMessage()));
  }
}
