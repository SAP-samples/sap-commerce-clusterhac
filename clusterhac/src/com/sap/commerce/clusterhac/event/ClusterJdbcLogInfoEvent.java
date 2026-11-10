package com.sap.commerce.clusterhac.event;

import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.collections4.CollectionUtils;

public class ClusterJdbcLogInfoEvent extends AbstractEvent implements ClusterAwareEvent {

  private String uuid;
  private int clusterId = -1;
  private Collection<String> nodeGroups;

  public ClusterJdbcLogInfoEvent(String uuid, int clusterId, Collection<String> nodeGroups) {
    this.uuid = uuid;
    this.clusterId = clusterId;
    this.nodeGroups =
        nodeGroups == null ? CollectionUtils.emptyCollection() : new ArrayList<>(nodeGroups);
  }

  public ClusterJdbcLogInfoEvent(String uuid, int clusterId) {
    this.uuid = uuid;
    this.clusterId = clusterId;
  }

  public ClusterJdbcLogInfoEvent(int clusterId) {
    this.clusterId = clusterId;
  }

  public ClusterJdbcLogInfoEvent(String uuid) {
    this.uuid = uuid;
  }

  public ClusterJdbcLogInfoEvent() {
  }

  public String getUuid() {
    return uuid;
  }

  public Collection<String> getNodeGroups() {
    return nodeGroups;
  }

  public int getClusterId() {
    return clusterId;
  }

  @Override
  public boolean canPublish(PublishEventContext publishEventContext) {
    return true;
  }

}
