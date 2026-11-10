package com.sap.commerce.clusterhac.event.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.commerce.clusterhac.data.ClusterCacheResult;
import com.sap.commerce.clusterhac.event.ClusterCacheEvent;
import com.sap.commerce.clusterhac.event.ClusterCacheResultEvent;
import de.hybris.platform.hac.facade.HacCacheFacade;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterCacheEventListener extends AbstractEventListener<ClusterCacheEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ClusterService clusterService;
  private final EventService eventService;
  private final HacCacheFacade hacCacheFacade;

  public ClusterCacheEventListener(
      ClusterService clusterService,
      EventService eventService, HacCacheFacade hacCacheFacade) {
    this.clusterService = clusterService;
    this.eventService = eventService;
    this.hacCacheFacade = hacCacheFacade;
  }


  /**
   * This method is called by  and processes the given event.
   *
   * @param clusterCacheEvent the event to be processed
   * @see #onApplicationEvent(ClusterCacheEvent)
   */
  @Override
  protected void onEvent(ClusterCacheEvent clusterCacheEvent) {
    // Check the event
    if (shouldHandleEvent(clusterCacheEvent)) {
      LOG.debug("Executing cluster cache data for Uuid {}", clusterCacheEvent.getUuid());
      String clusterCacheResultString = null;
      if (clusterCacheEvent.getUuid() != null) {
        if (clusterCacheEvent.isClearClusterCache()) {
          hacCacheFacade.clearCache();
        }
        ClusterCacheResult clusterCacheResult = new ClusterCacheResult();
        clusterCacheResult.setRegionCacheData(hacCacheFacade.getRegionCacheData());
        clusterCacheResult.setNode(clusterService.getClusterId());
        final ObjectMapper objectMapper = new ObjectMapper();
        try {
          clusterCacheResultString = objectMapper.writeValueAsString(clusterCacheResult);

        } catch (JsonProcessingException e) {
          LOG.error("Failed to serialize cluster cache result on node {}",
              clusterService.getClusterId(), e);
        }

        LOG.info("Executing cluster aware event on " + clusterService.getClusterId());
        eventService.publishEvent(
            new ClusterCacheResultEvent(clusterCacheEvent, clusterCacheResultString));
      }
    }
  }

  protected boolean shouldHandleEvent(final ClusterCacheEvent clusterCacheEvent) {
    return clusterCacheEvent.getClusterID() == clusterService.getClusterId();
  }

}
