package com.sap.commerce.clusterhac.event;

import static java.util.Optional.ofNullable;

import com.sap.commerce.clusterhac.data.ClusterDetailResult;
import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.servicelayer.event.impl.EventScope;

public class ClusterViewResultEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String uuid;
  private final Integer targetNodeId;
  private final ClusterDetailResult clusterDetailResult;

  public ClusterViewResultEvent(ClusterViewEvent sourceEvent,
      ClusterDetailResult clusterDetailResult) {
    this.uuid = sourceEvent.getUuid();
    this.targetNodeId = ofNullable(sourceEvent.getScope()).map(EventScope::getClusterId)
        .orElse(null);
    this.clusterDetailResult = clusterDetailResult;
  }

  public String getUuid() {
    return uuid;
  }

  public int getTargetNodeId() {
    return targetNodeId;
  }

  public ClusterDetailResult getClusterDetailResult() {
    return clusterDetailResult;
  }

  @Override
  public boolean canPublish(PublishEventContext context) {
    return targetNodeId == null || context.getTargetNodeId() == targetNodeId;
  }


}
