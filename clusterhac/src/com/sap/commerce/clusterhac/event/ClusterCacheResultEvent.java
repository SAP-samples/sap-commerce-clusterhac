package com.sap.commerce.clusterhac.event;

import static java.util.Optional.ofNullable;

import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.servicelayer.event.impl.EventScope;

public class ClusterCacheResultEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String uuid;
  private final Integer targetNodeId;
  private final String clusterCacheResult;

  public ClusterCacheResultEvent(ClusterCacheEvent sourceEvent, String clusterCacheResult) {
    this.uuid = sourceEvent.getUuid();
    this.targetNodeId = ofNullable(sourceEvent.getScope()).map(EventScope::getClusterId)
        .orElse(null);
    this.clusterCacheResult = clusterCacheResult;
  }

  public String getUuid() {
    return uuid;
  }

  public int getTargetNodeId() {
    return targetNodeId;
  }

  public String getClusterCacheResult() {
    return clusterCacheResult;
  }

  @Override
  public boolean canPublish(PublishEventContext context) {
    return targetNodeId == null || context.getTargetNodeId() == targetNodeId;
  }


}
