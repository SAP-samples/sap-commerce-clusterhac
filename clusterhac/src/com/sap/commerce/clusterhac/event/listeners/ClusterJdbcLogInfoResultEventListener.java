package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.event.ClusterJdbcLogInfoResultEvent;
import com.sap.commerce.clusterhac.service.ClusterJdbcLogInfoExecutorService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

public class ClusterJdbcLogInfoResultEventListener extends
    AbstractEventListener<ClusterJdbcLogInfoResultEvent> {

  private final ClusterJdbcLogInfoExecutorService clusterJdbcLogInfoExecutorService;

  public ClusterJdbcLogInfoResultEventListener(
      ClusterJdbcLogInfoExecutorService clusterJdbcLogInfoExecutorService) {
    this.clusterJdbcLogInfoExecutorService = clusterJdbcLogInfoExecutorService;
  }

  @Override
  protected void onEvent(ClusterJdbcLogInfoResultEvent event) {
    clusterJdbcLogInfoExecutorService.attachResult(event.getUuid(),
        event.getClusterJdbcLogInfoResult());
  }
}
