package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.event.ClusterHeapDumpInfoResultEvent;
import com.sap.commerce.clusterhac.service.ClusterHeapDumpInfoExecutorService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/**
 * Runs on the requesting node: lands each incoming heap-dump result in the UUID-keyed cache.
 * Mirrors {@link ClusterThreadDumpInfoResultEventListener}.
 */
public class ClusterHeapDumpInfoResultEventListener extends
    AbstractEventListener<ClusterHeapDumpInfoResultEvent> {

  private final ClusterHeapDumpInfoExecutorService clusterHeapDumpInfoExecutorService;

  public ClusterHeapDumpInfoResultEventListener(
      ClusterHeapDumpInfoExecutorService clusterHeapDumpInfoExecutorService) {
    this.clusterHeapDumpInfoExecutorService = clusterHeapDumpInfoExecutorService;
  }

  @Override
  protected void onEvent(ClusterHeapDumpInfoResultEvent event) {
    clusterHeapDumpInfoExecutorService.attachResult(event.getUuid(),
        event.getClusterHeapDumpInfoResult());
  }
}
