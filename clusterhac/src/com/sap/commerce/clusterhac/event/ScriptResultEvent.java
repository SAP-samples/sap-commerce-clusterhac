package com.sap.commerce.clusterhac.event;

import static java.util.Optional.ofNullable;

import com.sap.commerce.clusterhac.data.ScriptEvaluationResult;
import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.servicelayer.event.impl.EventScope;

public class ScriptResultEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String uuid;
  private final Integer targetNodeId;
  private final ScriptEvaluationResult scriptEvaluationResult;

  public ScriptResultEvent(ScriptExecutorEvent sourceEvent,
      ScriptEvaluationResult scriptEvaluationResult) {
    this.uuid = sourceEvent.getUuid();
    this.targetNodeId = ofNullable(sourceEvent.getScope()).map(EventScope::getClusterId)
        .orElse(null);
    this.scriptEvaluationResult = scriptEvaluationResult;
  }

  public String getUuid() {
    return uuid;
  }

  public int getTargetNodeId() {
    return targetNodeId;
  }

  public ScriptEvaluationResult getScriptEvaluationResult() {
    return scriptEvaluationResult;
  }

  @Override
  public boolean canPublish(PublishEventContext context) {
    return targetNodeId == null || context.getTargetNodeId() == targetNodeId;
  }
}
