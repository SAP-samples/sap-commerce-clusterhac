package com.sap.commerce.clusterhac.event.listeners;

import com.sap.commerce.clusterhac.data.ClusterHeapDumpInfoResult;
import com.sap.commerce.clusterhac.event.ClusterHeapDumpInfoEvent;
import com.sap.commerce.clusterhac.event.ClusterHeapDumpInfoResultEvent;
import com.sap.commerce.clusterhac.service.HeapDumpService;
import com.sap.commerce.clusterhac.service.HeapDumpService.HeapDumpState;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs on each node: reports that node's current heap-dump state back to the requesting node.
 * Mirrors {@link ClusterThreadDumpInfoEventListener}.
 */
public class ClusterHeapDumpInfoEventListener extends
    AbstractEventListener<ClusterHeapDumpInfoEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ClusterService clusterService;
  private final EventService eventService;
  private final HeapDumpService heapDumpService;

  public ClusterHeapDumpInfoEventListener(ClusterService clusterService, EventService eventService,
      HeapDumpService heapDumpService) {
    this.clusterService = clusterService;
    this.eventService = eventService;
    this.heapDumpService = heapDumpService;
  }

  @Override
  protected void onEvent(ClusterHeapDumpInfoEvent event) {
    if (!shouldHandleEvent(event) || event.getUuid() == null) {
      return;
    }
    try {
      HeapDumpState dumpState = heapDumpService.getState();
      ClusterHeapDumpInfoResult result = new ClusterHeapDumpInfoResult();
      result.setNodeID(clusterService.getClusterId());
      result.setClusterGroup(clusterService.getClusterGroups());
      result.setStatus(dumpState.getStatus().name());
      result.setBlobName(dumpState.getBlobName());
      result.setBlobUrl(dumpState.getBlobUrl());
      result.setBytesStreamed(dumpState.getBytesStreamed());
      result.setStartedAt(dumpState.getStartedAt());
      result.setFinishedAt(dumpState.getFinishedAt());
      result.setMessage(dumpState.getMessage());
      eventService.publishEvent(new ClusterHeapDumpInfoResultEvent(event, result));
    } catch (Exception e) {
      LOG.error("Exception in ClusterHeapDumpInfoEvent", e);
    }
  }

  protected boolean shouldHandleEvent(final ClusterHeapDumpInfoEvent event) {
    return event.getClusterId() == -1 || event.getClusterId() == clusterService.getClusterId();
  }
}
