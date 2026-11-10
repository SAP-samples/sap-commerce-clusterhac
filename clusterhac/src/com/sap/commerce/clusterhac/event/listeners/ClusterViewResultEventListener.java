package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.event.ClusterViewResultEvent;
import com.sap.commerce.clusterhac.service.ClusterViewExecutorService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

public class ClusterViewResultEventListener extends AbstractEventListener<ClusterViewResultEvent> {

  private final ClusterViewExecutorService clusterViewExecutorService;

  public ClusterViewResultEventListener(
      ClusterViewExecutorService clusterViewExecutorService) {
    this.clusterViewExecutorService = clusterViewExecutorService;
  }

  @Override
  protected void onEvent(ClusterViewResultEvent event) {
    clusterViewExecutorService.attachResult(event.getUuid(), event.getClusterDetailResult());
  }
}
