package com.sap.commerce.clusterhac.event;

import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

public class ClusterCacheEvent extends AbstractEvent implements ClusterAwareEvent {

  private final int clusterID;
  private String uuid;
  private boolean clearClusterCache;


  public ClusterCacheEvent(String uuid, int clusterID, boolean clearClusterCache) {
    this.uuid = uuid;
    this.clusterID = clusterID;
    this.clearClusterCache = clearClusterCache;
  }

  public ClusterCacheEvent(int clusterID) {
    this.clusterID = clusterID;

  }

  public String getUuid() {
    return uuid;
  }

  public int getClusterID() {
    return clusterID;
  }

  @Override
  public boolean canPublish(PublishEventContext publishEventContext) {
    return true;
  }

  public boolean isClearClusterCache() {
    return clearClusterCache;
  }

  public void setClearClusterCache(boolean clearClusterCache) {
    this.clearClusterCache = clearClusterCache;
  }
}
