package com.sap.commerce.clusterhac.event;

import static java.util.Optional.ofNullable;

import com.sap.commerce.clusterhac.data.ClusterThreadDumpInfoResult;
import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.servicelayer.event.impl.EventScope;

public class ClusterThreadDumpInfoResultEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String uuid;
  private final Integer targetNodeId;
  private final ClusterThreadDumpInfoResult clusterThreadDumpInfoResult;

  public ClusterThreadDumpInfoResultEvent(ClusterThreadDumpInfoEvent sourceEvent,
      ClusterThreadDumpInfoResult clusterJdbcLogInfoResult) {
    this.uuid = sourceEvent.getUuid();
    this.targetNodeId = ofNullable(sourceEvent.getScope()).map(EventScope::getClusterId)
        .orElse(null);
    this.clusterThreadDumpInfoResult = clusterJdbcLogInfoResult;
  }

  public String getUuid() {
    return uuid;
  }

  public int getTargetNodeId() {
    return targetNodeId;
  }

  public ClusterThreadDumpInfoResult getClusterThreadDumpInfoResult() {
    return clusterThreadDumpInfoResult;
  }

  @Override
  public boolean canPublish(PublishEventContext context) {
    return targetNodeId == null || context.getTargetNodeId() == targetNodeId;
  }

}
