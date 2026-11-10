package com.sap.commerce.clusterhac.event;

import static java.util.Optional.ofNullable;

import com.sap.commerce.clusterhac.data.ClusterHeapDumpInfoResult;
import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.servicelayer.event.impl.EventScope;

/**
 * Carries one node's heap-dump status back to the node that published the request. Mirrors
 * {@link ClusterThreadDumpInfoResultEvent}: the result is addressed to the origin node via the
 * source event's scope cluster id.
 */
public class ClusterHeapDumpInfoResultEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String uuid;
  private final Integer targetNodeId;
  private final ClusterHeapDumpInfoResult clusterHeapDumpInfoResult;

  public ClusterHeapDumpInfoResultEvent(ClusterHeapDumpInfoEvent sourceEvent,
      ClusterHeapDumpInfoResult clusterHeapDumpInfoResult) {
    this.uuid = sourceEvent.getUuid();
    this.targetNodeId = ofNullable(sourceEvent.getScope()).map(EventScope::getClusterId)
        .orElse(null);
    this.clusterHeapDumpInfoResult = clusterHeapDumpInfoResult;
  }

  public String getUuid() {
    return uuid;
  }

  public int getTargetNodeId() {
    return targetNodeId;
  }

  public ClusterHeapDumpInfoResult getClusterHeapDumpInfoResult() {
    return clusterHeapDumpInfoResult;
  }

  @Override
  public boolean canPublish(PublishEventContext context) {
    return targetNodeId == null || context.getTargetNodeId() == targetNodeId;
  }
}
