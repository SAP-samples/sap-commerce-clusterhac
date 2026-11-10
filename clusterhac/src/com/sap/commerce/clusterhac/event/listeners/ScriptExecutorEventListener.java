package com.sap.commerce.clusterhac.event.listeners;

import com.google.common.base.Throwables;
import com.sap.commerce.clusterhac.data.ScriptEvaluationResult;
import com.sap.commerce.clusterhac.event.ScriptExecutorEvent;
import com.sap.commerce.clusterhac.event.ScriptResultEvent;
import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.hac.scripting.ScriptingLanguageExecutor;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptExecutorEventListener extends AbstractEventListener<ScriptExecutorEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String SCRIPT_OUT_WRITER = "out";
  private static final String CLUSTER_GROUPS = "ClusterGroups";
  private static final String HOST_NAME = "HostName";
  private static final String STACKTRACE_TEXT_KEY = "stacktraceText";
  private static final String OUTPUT_TEXT_KEY = "outputText";
  private static final String EXECUTION_RESULT_KEY = "executionResult";
  private static final String GROOVY = "groovy";

  private final ClusterService clusterService;
  private final EventService eventService;
  private final ScriptExecutorService scriptExecutorService;
  private final ScriptingLanguageExecutor executor;

  public ScriptExecutorEventListener(
      ClusterService clusterService,
      EventService eventService,
      ScriptExecutorService scriptExecutorService,
      ScriptingLanguageExecutor executor) {
    this.clusterService = clusterService;
    this.eventService = eventService;
    this.scriptExecutorService = scriptExecutorService;
    this.executor = executor;
  }

  @Override
  protected void onEvent(ScriptExecutorEvent scriptExecutorEvent) {

    // Check the event
    if (shouldHandleEvent(scriptExecutorEvent)) {
      String script = scriptExecutorEvent.getScript();
      LOG.debug("Executing script {}", script);
      if (scriptExecutorEvent.getUuid() != null) {
        ScriptEvaluationResult result = executeWithFeedback(script, scriptExecutorEvent);
        LOG.info("Executing cluster aware event on " + clusterService.getClusterGroups());
        eventService.publishEvent(new ScriptResultEvent(scriptExecutorEvent, result));
      } else {
        // its for saving the thread dumps as media
        scriptExecutorService.executeScript(script, true);
      }
    }
  }

  protected boolean shouldHandleEvent(final ScriptExecutorEvent scriptExecutorEvent) {
    String myId = "" + clusterService.getClusterId();
    // Specific-pod targeting: run if this node's id is in the explicit nodeIds set.
    if (scriptExecutorEvent.getNodeIds().stream().anyMatch(id -> StringUtils.equals(id, myId))) {
      return true;
    }
    return StringUtils.equals(scriptExecutorEvent.getNodeId(), myId)
        || clusterService.getClusterGroups().stream()
            .anyMatch(group -> scriptExecutorEvent.getNodeGroups().stream()
                    .anyMatch(nodeGroup -> nodeGroup.equalsIgnoreCase(group)));
  }

  private ScriptEvaluationResult executeWithFeedback(String script,
      ScriptExecutorEvent scriptExecutorEvent) {

    ScriptEvaluationResult scriptEvaluationResult = new ScriptEvaluationResult();
    try {
      scriptEvaluationResult.setNode(clusterService.getClusterId());
      scriptEvaluationResult.setClusterGroup(clusterService.getClusterGroups());
      scriptEvaluationResult.setClusterGroupAndHostName(
          scriptExecutorService.getclusterGroupAndHostName());

      Map<String, Object> results = executor.executeScript(GROOVY, script, false);
      scriptEvaluationResult.setExecutionResult(String.valueOf(results.get(EXECUTION_RESULT_KEY)));
      scriptEvaluationResult.setOutputText(String.valueOf(results.get(OUTPUT_TEXT_KEY)));
      scriptEvaluationResult.setStacktraceText(String.valueOf(results.get(STACKTRACE_TEXT_KEY)));

      // Concise audit line at INFO (no full output — top-objects/large scripts would flood the
      // console); the full result/output/stacktrace go to DEBUG.
      String outputText = scriptEvaluationResult.getOutputText();
      LOG.info(
          "Script executed: uniqueId={} successful={} node={} groups={} outputChars={}",
          scriptExecutorEvent.getUuid(), true, scriptEvaluationResult.getNode(),
          scriptEvaluationResult.getClusterGroup(),
          outputText == null ? 0 : outputText.length());
      LOG.debug(
          "Script result uniqueId={} result={} outputText={} stacktraceText={}",
          scriptExecutorEvent.getUuid(),
          scriptEvaluationResult.getExecutionResult(),
          outputText,
          scriptEvaluationResult.getStacktraceText());

    } catch (Exception e) {
      var stacktraceText = Throwables.getStackTraceAsString(e);
      scriptEvaluationResult.setStacktraceText(stacktraceText);
      LOG.error("Error occurred while executing script. uniqueId={} , stacktraceText={} , node={} "
          + "", scriptExecutorEvent.getUuid(), stacktraceText, clusterService.getClusterId());

    }
    return scriptEvaluationResult;
  }

}
