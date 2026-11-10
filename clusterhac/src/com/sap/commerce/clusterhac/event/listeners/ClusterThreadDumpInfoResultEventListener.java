package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.event.ClusterThreadDumpInfoResultEvent;
import com.sap.commerce.clusterhac.service.ClusterThreadDumpInfoExecutorService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

public class ClusterThreadDumpInfoResultEventListener extends
    AbstractEventListener<ClusterThreadDumpInfoResultEvent> {

  private final ClusterThreadDumpInfoExecutorService clusterThreadDumpInfoExecutorService;

  public ClusterThreadDumpInfoResultEventListener(
      ClusterThreadDumpInfoExecutorService clusterThreadDumpInfoExecutorService) {
    this.clusterThreadDumpInfoExecutorService = clusterThreadDumpInfoExecutorService;
  }

  @Override
  protected void onEvent(ClusterThreadDumpInfoResultEvent event) {
    clusterThreadDumpInfoExecutorService.attachResult(event.getUuid(),
        event.getClusterThreadDumpInfoResult());
  }
}
