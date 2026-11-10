package com.sap.commerce.clusterhac.event;

import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

public class ClusterViewEvent extends AbstractEvent implements ClusterAwareEvent {

  private String uuid;


  public ClusterViewEvent(String uuid) {
    this.uuid = uuid;
  }

  public ClusterViewEvent() {
  }

  public String getUuid() {
    return uuid;
  }

  @Override
  public boolean canPublish(PublishEventContext publishEventContext) {
    return true;
  }

}
