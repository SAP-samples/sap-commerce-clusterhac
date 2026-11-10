package com.sap.commerce.clusterhac.event.listeners;


import com.sap.commerce.clusterhac.data.ClusterJdbcLogInfoResult;
import com.sap.commerce.clusterhac.event.ClusterJdbcLogInfoEvent;
import com.sap.commerce.clusterhac.event.ClusterJdbcLogInfoResultEvent;
import de.hybris.platform.hac.facade.HacDatabaseFacade;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import de.hybris.platform.util.Config;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterJdbcLogInfoEventListener extends
    AbstractEventListener<ClusterJdbcLogInfoEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ClusterService clusterService;
  private final EventService eventService;
  private final HacDatabaseFacade hacDatabaseFacade;

  public ClusterJdbcLogInfoEventListener(
      ClusterService clusterService,
      EventService eventService, HacDatabaseFacade hacDatabaseFacade) {
    this.clusterService = clusterService;
    this.eventService = eventService;
    this.hacDatabaseFacade = hacDatabaseFacade;
  }


  /**
   * This method is called by  and processes the given event.
   *
   * @param clusterJdbcLogInfoEvent the event to be processed
   * @see #onApplicationEvent(ClusterJdbcLogInfoEvent)
   */
  @Override
  protected void onEvent(ClusterJdbcLogInfoEvent clusterJdbcLogInfoEvent) {
    // Check the event
    if (shouldHandleEvent(clusterJdbcLogInfoEvent)) {
      LOG.debug("Executing ClusterJdbcLogInfoEvent for Uuid {}", clusterJdbcLogInfoEvent.getUuid());
      if (clusterJdbcLogInfoEvent.getUuid() != null) {

        ClusterJdbcLogInfoResult clusterJdbcLogInfoResult = new ClusterJdbcLogInfoResult();
        try {

          Map<String, Object> databaseInfoMap = hacDatabaseFacade.databaseLogsInfo();
          clusterJdbcLogInfoResult.setLogEnabled((Boolean) databaseInfoMap.get("logEnabled"));
          clusterJdbcLogInfoResult.setTracesEnabled((Boolean) databaseInfoMap.get("tracesEnabled"));
          clusterJdbcLogInfoResult.setParamsEnabled(
              Boolean.valueOf(Config.getBoolean("db.log.sql.parameters", false)));
          clusterJdbcLogInfoResult.setLogFilePath((String) databaseInfoMap.get("logFilePath"));
          clusterJdbcLogInfoResult.setLogFileSize((Integer) databaseInfoMap.get("logFileSize"));
          clusterJdbcLogInfoResult.setNodeID(clusterService.getClusterId());
          clusterJdbcLogInfoResult.setClusterGroup(clusterService.getClusterGroups());

          LOG.info("Executing ClusterJdbcLogInfoEvent event on " + clusterService.getClusterId());
          eventService.publishEvent(
              new ClusterJdbcLogInfoResultEvent(clusterJdbcLogInfoEvent, clusterJdbcLogInfoResult));

        } catch (IOException e) {
          LOG.error("Failed to read JDBC log info on node {}", clusterService.getClusterId(), e);
        }
      }
    }
  }

  protected boolean shouldHandleEvent(final ClusterJdbcLogInfoEvent clusterCacheEvent) {
    return clusterCacheEvent.getClusterId() == -1 ||
        clusterCacheEvent.getClusterId() == clusterService.getClusterId();
  }

}
