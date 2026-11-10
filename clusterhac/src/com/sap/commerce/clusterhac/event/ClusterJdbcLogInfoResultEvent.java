package com.sap.commerce.clusterhac.event;

import static java.util.Optional.ofNullable;

import com.sap.commerce.clusterhac.data.ClusterJdbcLogInfoResult;
import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.servicelayer.event.impl.EventScope;

public class ClusterJdbcLogInfoResultEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String uuid;
  private final Integer targetNodeId;
  private final ClusterJdbcLogInfoResult clusterJdbcLogInfoResult;

  public ClusterJdbcLogInfoResultEvent(ClusterJdbcLogInfoEvent sourceEvent,
      ClusterJdbcLogInfoResult clusterJdbcLogInfoResult) {
    this.uuid = sourceEvent.getUuid();
    this.targetNodeId = ofNullable(sourceEvent.getScope()).map(EventScope::getClusterId)
        .orElse(null);
    this.clusterJdbcLogInfoResult = clusterJdbcLogInfoResult;
  }

  public String getUuid() {
    return uuid;
  }

  public int getTargetNodeId() {
    return targetNodeId;
  }

  public ClusterJdbcLogInfoResult getClusterJdbcLogInfoResult() {
    return clusterJdbcLogInfoResult;
  }

  @Override
  public boolean canPublish(PublishEventContext context) {
    return targetNodeId == null || context.getTargetNodeId() == targetNodeId;
  }

}
