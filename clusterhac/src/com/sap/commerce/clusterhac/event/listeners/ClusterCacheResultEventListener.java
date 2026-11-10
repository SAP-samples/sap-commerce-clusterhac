package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.event.ClusterCacheResultEvent;
import com.sap.commerce.clusterhac.service.ClusterCacheExecutorService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

public class ClusterCacheResultEventListener extends
    AbstractEventListener<ClusterCacheResultEvent> {

  private final ClusterCacheExecutorService clusterCacheExecutorService;

  public ClusterCacheResultEventListener(
      ClusterCacheExecutorService clusterCacheExecutorService) {
    this.clusterCacheExecutorService = clusterCacheExecutorService;
  }

  @Override
  protected void onEvent(ClusterCacheResultEvent event) {
    clusterCacheExecutorService.attachResult(event.getUuid(), event.getClusterCacheResult());
  }
}
