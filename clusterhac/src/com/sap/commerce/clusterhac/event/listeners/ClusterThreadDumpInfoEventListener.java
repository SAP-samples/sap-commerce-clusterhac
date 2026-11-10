package com.sap.commerce.clusterhac.event.listeners;


import com.sap.commerce.clusterhac.data.ClusterThreadDumpInfoResult;
import com.sap.commerce.clusterhac.event.ClusterThreadDumpInfoEvent;
import com.sap.commerce.clusterhac.event.ClusterThreadDumpInfoResultEvent;
import de.hybris.platform.hac.facade.ThreadDumpCollector;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterThreadDumpInfoEventListener extends
    AbstractEventListener<ClusterThreadDumpInfoEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ClusterService clusterService;
  private final EventService eventService;
  private final ThreadDumpCollector threadDumpCollector;

  public ClusterThreadDumpInfoEventListener(
      ClusterService clusterService,
      EventService eventService, ThreadDumpCollector threadDumpCollector) {
    this.clusterService = clusterService;
    this.eventService = eventService;
    this.threadDumpCollector = threadDumpCollector;
  }


  /**
   * This method is called by  and processes the given event.
   *
   * @param clusterThreadDumpInfoEvent the event to be processed
   * @see #onApplicationEvent(ClusterThreadDumpInfoEvent)
   */
  @Override
  protected void onEvent(ClusterThreadDumpInfoEvent clusterThreadDumpInfoEvent) {
    // Check the event
    if (shouldHandleEvent(clusterThreadDumpInfoEvent)) {
      LOG.debug("Executing clusterThreadDumpInfoEvent for Uuid {}",
          clusterThreadDumpInfoEvent.getUuid());
      if (clusterThreadDumpInfoEvent.getUuid() != null) {

        ClusterThreadDumpInfoResult clusterThreadDumpInfoResult = new ClusterThreadDumpInfoResult();
        try {

          clusterThreadDumpInfoResult.setNodeID(clusterService.getClusterId());
          clusterThreadDumpInfoResult.setClusterGroup(clusterService.getClusterGroups());
          clusterThreadDumpInfoResult.setDumpProcessing(threadDumpCollector.isCollecting());

          LOG.info(
              "Executing clusterThreadDumpInfoEvent event on " + clusterService.getClusterId());
          eventService.publishEvent(new ClusterThreadDumpInfoResultEvent(clusterThreadDumpInfoEvent,
              clusterThreadDumpInfoResult));

        } catch (Exception e) {
          LOG.error("Exception in ClusterThreadDumpInfoEvent", e);
        }
      }
    }
  }

  protected boolean shouldHandleEvent(final ClusterThreadDumpInfoEvent clusterThreadDumpInfoEvent) {
    return clusterThreadDumpInfoEvent.getClusterId() == -1 ||
        clusterThreadDumpInfoEvent.getClusterId() == clusterService.getClusterId();
  }

}
