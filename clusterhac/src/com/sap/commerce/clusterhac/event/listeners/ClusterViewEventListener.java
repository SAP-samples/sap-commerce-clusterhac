package com.sap.commerce.clusterhac.event.listeners;


import com.sap.commerce.clusterhac.data.ClusterDetailResult;
import com.sap.commerce.clusterhac.event.ClusterViewEvent;
import com.sap.commerce.clusterhac.event.ClusterViewResultEvent;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import de.hybris.platform.util.Config;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 *
 */
public class ClusterViewEventListener extends AbstractEventListener<ClusterViewEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String CLUSTER_GROUPS = "ClusterGroups";
  private static final String HOST_NAME = "HostName";
  private static final String CCV2_SERVICES_LOGGING = "ccv2.services.logging";


  private final ClusterService clusterService;
  private final EventService eventService;
  private final ScriptExecutorService scriptExecutorService;
  private final ClusterScriptExecutorService clusterScriptExecutorService;
  private static final String FILTER_INDEX_INFO = "('$state':(store:appState),meta:(alias:!n,disabled:!f,index:'"
          + "maintained-by-perfx_k8s-content-package_index-pattern-logs-json',";

  public ClusterViewEventListener(
      ClusterService clusterService,
      EventService eventService,
      ClusterScriptExecutorService clusterScriptExecutorService,
      ScriptExecutorService scriptExecutorService) {
    this.clusterService = clusterService;
    this.eventService = eventService;
    this.clusterScriptExecutorService = clusterScriptExecutorService;
    this.scriptExecutorService = scriptExecutorService;
  }


  /**
   * This method is called by  and processes the given event.
   *
   * @param clusterViewEvent the event to be processed
   * @see #onApplicationEvent(ClusterViewEvent)
   */
  @Override
  protected void onEvent(ClusterViewEvent clusterViewEvent) {
    LOG.debug("Executing cluster view Event for Uuid {}", clusterViewEvent.getUuid());
    var clusterDetailResult = new ClusterDetailResult();
    Map<String, String> clusterGroupAndHostName = new HashMap<String, String>();
    if (clusterViewEvent.getUuid() != null) {
      clusterDetailResult.setNodeID(clusterService.getClusterId());
      clusterDetailResult.setClusterGroup(clusterService.getClusterGroups());

      clusterGroupAndHostName.put(CLUSTER_GROUPS,
          StringUtils.collectionToCommaDelimitedString(clusterService.getClusterGroups()));
      String hostName = null;
      try {
        hostName = scriptExecutorService.getHostname();
        clusterGroupAndHostName.put(HOST_NAME, hostName);
        clusterDetailResult.setClusterGroupAndHostName(
            org.apache.commons.lang3.StringUtils.join(clusterGroupAndHostName));
        clusterDetailResult.setHostName(
            org.apache.commons.lang3.StringUtils.trim(hostName));
        clusterDetailResult.setKibanaEndpoint(getKibanaLogUrl(hostName));
        populateNodeInfo(clusterDetailResult);
      } catch (Exception e) {
        // Enrichment (host / node-info scripts) is best-effort — never let it stop the node
        // from reporting in. The core node id/group is already set above.
        LOG.error("Node enrichment failed on node {}; publishing core result",
            clusterService.getClusterId(), e);
      }
      LOG.info("Executing cluster view event on " + clusterService.getClusterId());
      eventService.publishEvent(new ClusterViewResultEvent(clusterViewEvent, clusterDetailResult));
    }

  }

  /**
   * Runs the per-node JVM/pod-health script and populates the result. The script returns a
   * tab-delimited list of key=value pairs (self-describing, order-independent), e.g.:
   * ip=.. heapUsedMb=.. heapMaxMb=.. nonHeapMb=.. uptime=.. gc=.. threads=.. cpu=.. dbPool=..
   */
  private void populateNodeInfo(ClusterDetailResult clusterDetailResult) {
    String nodeInfo = scriptExecutorService.getNodeInfo();
    if (org.apache.commons.lang3.StringUtils.isBlank(nodeInfo) || "null".equals(nodeInfo)) {
      LOG.warn("Node info unavailable for node {}", clusterService.getClusterId());
      return;
    }
    Map<String, String> info = new HashMap<>();
    for (String pair : nodeInfo.split("\t", -1)) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        info.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
      }
    }
    clusterDetailResult.setClusterIP(info.getOrDefault("ip", "n/a"));
    clusterDetailResult.setHeapUsedMb(parseLong(info.get("heapUsedMb")));
    clusterDetailResult.setHeapMaxMb(parseLong(info.get("heapMaxMb")));
    clusterDetailResult.setNonHeapMb(parseLong(info.get("nonHeapMb")));
    clusterDetailResult.setUptime(info.getOrDefault("uptime", "n/a"));
    clusterDetailResult.setGcInfo(info.getOrDefault("gc", "n/a"));
    clusterDetailResult.setThreads(info.getOrDefault("threads", "n/a"));
    clusterDetailResult.setCpu(info.getOrDefault("cpu", "n/a"));
    clusterDetailResult.setDbPool(info.getOrDefault("dbPool", "n/a"));
    // Raw numeric health signals for the Overview "nodes needing attention" list (-1 = unknown).
    clusterDetailResult.setCpuPct(parseInt(info.get("cpuPct")));
    clusterDetailResult.setPoolActive(parseInt(info.get("poolActive")));
    clusterDetailResult.setPoolMax(parseInt(info.get("poolMax")));
    clusterDetailResult.setLastGcMs(parseLong(info.get("lastGcMs")));
  }

  private int parseInt(String value) {
    try {
      return Integer.parseInt(org.apache.commons.lang3.StringUtils.trim(value));
    } catch (NumberFormatException | NullPointerException e) {
      return -1;
    }
  }

  private long parseLong(String value) {
    try {
      return Long.parseLong(org.apache.commons.lang3.StringUtils.trim(value));
    } catch (NumberFormatException | NullPointerException e) {
      return -1L;
    }
  }

  private String getKibanaLogUrl(String hostName) {
    String kibanaEndpoint = Config.getString(CCV2_SERVICES_LOGGING, "");
    hostName = org.apache.commons.lang3.StringUtils.trim(hostName);

    if (org.apache.commons.lang3.StringUtils.isNotEmpty(kibanaEndpoint)) {
      String baseUrl = kibanaEndpoint; //${kibanaEndpoint.domainName}

//      String path = "/app/kibana#/discover?_g=(refreshInterval:(pause:!f,value:5000),"
//          + "time:(from:now-1h,mode:quick,to:now))&_a=(columns:!(log),"
//          + "filters:!(('$state':(store:appState),meta:(alias:!n,disabled:!f,index:'flb-*',"
//          + "key:kubernetes.pod_name,negate:!f,params:(query:" + hostName + ","
//          + "type:phrase),type:phrase,value:" + hostName + "),query:"
//          + "(match:(kubernetes.pod_name:(query:" + hostName + ",type:phrase)))),"
//          + "('$state':(store:appState),meta:(alias:!n,disabled:!f,index:'flb-*',"
//          + "key:logs.loggerName,negate:!f,params:"
//          + "(query:de.hybris.platform.jdbcwrapper.logger.JDBCSLF4JAwareLogger,type:phrase),"
//          + "type:phrase,value:de.hybris.platform.jdbcwrapper.logger.JDBCSLF4JAwareLogger),"
//          + "query:(match:(logs.loggerName:(query:de.hybris.platform.jdbcwrapper.logger.JDBCSLF4JAwareLogger,"
//          + "type:phrase))))),index:'flb-*',interval:auto,query:(language:lucene,query:''),sort:!(time,desc))";
//

       String CLS_DASHBOARDS_URL_TEMPLATE = "/app/kibana#/discover?"
              + "_g=(refreshInterval:(pause:!f,value:5000),time:(from:now-2h,mode:quick,to:now))"
              + "&_a=(columns:!(level,message,log)," + "filters:!(" + FILTER_INDEX_INFO
              + "key:kubernetes.labels.app_kubernetes_io_name,negate:!f,"
              + "params:(query:hybris,type:phrase),type:phrase,value:hybris),"
              + "match_phrase:(kubernetes.labels.app_kubernetes_io_name:hybris))," + FILTER_INDEX_INFO
              + "key:kubernetes.labels.app_kubernetes_io_component,negate:!f,"
              + "params:(query:pod_name,type:phrase),type:phrase,value:pod_name),"
              + "match_phrase:(kubernetes.pod_name:" + hostName + "))," + FILTER_INDEX_INFO
              + "key:kubernetes.labels.ccv2_cx_sap_com_deployment-id,"
              + "negate:!f,params:(query:JDBCLog,type:phrase)"
              + ",type:phrase,value:JDBCLog),"
              + "match:(logs.loggerName:de.hybris.platform.jdbcwrapper.logger.JDBCSLF4JAwareLogger))),"
              + "highlightAll:!f,index:'maintained-by-perfx_k8s-content-package_index-pattern-logs-json',"
              + "query:(language:lucene,query:''),version:!t)";

      return baseUrl + CLS_DASHBOARDS_URL_TEMPLATE;
    }
    return null;
  }

}
