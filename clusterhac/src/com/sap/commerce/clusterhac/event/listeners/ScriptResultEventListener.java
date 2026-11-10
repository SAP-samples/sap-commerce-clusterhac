package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.event.ScriptResultEvent;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

public class ScriptResultEventListener extends AbstractEventListener<ScriptResultEvent> {

  private final ClusterScriptExecutorService scriptExecutorService;

  public ScriptResultEventListener(ClusterScriptExecutorService scriptExecutorService) {
    this.scriptExecutorService = scriptExecutorService;
  }

  @Override
  protected void onEvent(ScriptResultEvent event) {
    scriptExecutorService.attachResult(event.getUuid(), event.getScriptEvaluationResult());
  }
}
