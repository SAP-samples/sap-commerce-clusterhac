package com.sap.commerce.clusterhac.event;

import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

/**
 * Broadcast request asking every node to report its current heap-dump status. Mirrors
 * {@link ClusterThreadDumpInfoEvent}: broadcast to all nodes ({@code canPublish} true), with the
 * "is this for me" decision left to the listener via {@code clusterId} ({@code -1} = all nodes).
 */
public class ClusterHeapDumpInfoEvent extends AbstractEvent implements ClusterAwareEvent {

  private String uuid;
  private int clusterId = -1;

  public ClusterHeapDumpInfoEvent(String uuid, int clusterId) {
    this.uuid = uuid;
    this.clusterId = clusterId;
  }

  public ClusterHeapDumpInfoEvent(String uuid) {
    this.uuid = uuid;
  }

  public ClusterHeapDumpInfoEvent() {
  }

  public String getUuid() {
    return uuid;
  }

  public int getClusterId() {
    return clusterId;
  }

  @Override
  public boolean canPublish(PublishEventContext publishEventContext) {
    return true;
  }
}
