/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller;


import com.sap.commerce.clusterhac.data.ClusterDetailResult;
import com.sap.commerce.clusterhac.data.ClusterJdbcLogInfoResult;
import com.sap.commerce.clusterhac.service.ClusterJdbcLogInfoExecutorService;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.ClusterViewExecutorService;
import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.hac.facade.HacDatabaseFacade;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.util.Config;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class ClusterDatabaseController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String VIEW_BASE = "database/";

  private final HacDatabaseFacade dbFacade;

  @Value("${hac.extlinks.wiki.jdbclogging:https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD}")
  private String wikiJdbcLogging;

  @Value("${hac.extlinks.wiki.database:https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD}")
  private String wikiDatabase;

  @Resource
  private ClusterViewExecutorService clusterViewExecutorService;

  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;

  @Resource
  private ScriptExecutorService scriptExecutorService;

  @Resource
  private UserService userService;

  @Resource
  private ClusterJdbcLogInfoExecutorService clusterJdbcLogInfoExecutorService;


  @Autowired
  public ClusterDatabaseController(final HacDatabaseFacade dbFacade) {
    this.dbFacade = dbFacade;
  }

  @GetMapping(value = "/cluster/database")
  public String database(final Model model,
      @RequestParam(required = false) String nodeId,
      @RequestParam(required = false) String host) {
    model.addAttribute("wikiJdbcLogging", wikiJdbcLogging);
    model.addAttribute("wikiDatabase", wikiDatabase);
    model.addAttribute("clsURL", Config.getString("ccv2.services.logging", ""));
    // Optional node context when arriving from the Overview node strip (highlights that node's
    // row); per-node JDBC toggles already target a single node.
    model.addAttribute("nodeId", nodeId);
    model.addAttribute("host", host);

    return VIEW_BASE + "database";
  }

  @RequestMapping(value = "/cluster/database/data", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public Map<String, String> clusterData() {
    String uuid = clusterViewExecutorService.submit();
    return Map.of("uuid", uuid);
  }

  @GetMapping(value = "/cluster/database/results", produces = "application/json")
  @ResponseBody
  public Collection<ClusterDetailResult> getScriptEvaluationResults(@RequestParam String uuid) {
    return clusterViewExecutorService.getClusterEvaluationResults(uuid);
  }

  @GetMapping(value = "/cluster/database/analyzeLog", headers = "Accept=application/json")
  @ResponseBody
  public Map analyzeLog() throws IOException, ResourceException, ScriptException {
    return dbFacade.analyzeLog();
  }


  @PostMapping(value = "/cluster/database/stacktrace/{active}", headers = "Accept=application/json")
  @ResponseBody
  public void toggleTracesInLog(@PathVariable final boolean active) {
    dbFacade.toggleTracesInLogOutput(active);
  }


  @PostMapping(value = "/cluster/database/params/{active}", headers = "Accept=application/json")
  @ResponseBody
  public void toggleQueriesWithParamsInLog(@PathVariable final boolean active) {
    dbFacade.toggleQueriesWithParams(active);
  }

  @PostMapping(value = "/cluster/database/logs/{active}", headers = "Accept=application/json")
  @ResponseBody
  public void toggleDatabseLogging(@PathVariable final boolean active) {
    dbFacade.toggleDatabaseLogging(active);
  }

  @PostMapping(value = "/cluster/database/logs/", headers = "Accept=application/json")
  @ResponseBody
  public void toggleClusterDatabseLogging(@RequestParam final String nodeID,
      @RequestParam final String logEnabled,
      @RequestParam final String tracesEnabled,
      @RequestParam final String paramsEnabled) {
    final UserModel currentUser = userService.getCurrentUser();
    LOG.info(
        "[{}] executed cluster JDBC logging from HAC on nodeID [{}]: logEnabled=[{}], tracesEnabled =[{}] , paramsEnabled =[{}] ",
        currentUser.getUid(), nodeID, logEnabled, tracesEnabled, paramsEnabled);

    Map<String, Object> params = new HashMap<>();
    params.put("LOG_ENABLED", logEnabled);
    params.put("TRACES_ENABLED", tracesEnabled);
    params.put("PARAMS_ENABLED", paramsEnabled);
    try {
      String newScript = scriptExecutorService.getScript("jdbclog/jdbc-log.groovy", params);
      clusterScriptExecutorService.submit(newScript, nodeID);
    } catch (Exception ex) {
      LOG.info(ex.getMessage());
    }
  }

  @GetMapping(value = "/cluster/database/logs/download")
  @ResponseBody
  public void databaseLogsDownload(final HttpServletResponse response,
      @RequestParam final int downloadSize) {
    dbFacade.databaseLogsDownload(response, downloadSize);
  }

  @GetMapping(value = "/cluster/database/logs", headers = "Accept=application/json")
  @ResponseBody
  public Map<String, Object> databaseLogsInfo() throws IOException {
    return dbFacade.databaseLogsInfo();
  }

  @GetMapping(value = "/cluster/database/loginfos", produces = "application/json")
  @ResponseBody
  public Map<String, String> clusterDatabaseLogsInfo() {
    String uuid = clusterJdbcLogInfoExecutorService.submit();
    return Map.of("uuid", uuid);
  }

  @RequestMapping(value = "/cluster/database/loginfos/results", headers = "Accept=application/json", method = RequestMethod.GET)
  @ResponseBody
  public Collection<ClusterJdbcLogInfoResult> clusterDatabaseLogsInfoResults(
      @RequestParam String uuid) {
    return getDatabaseLogsInfoResults(uuid);
  }

  private Collection<ClusterJdbcLogInfoResult> getDatabaseLogsInfoResults(String uuid) {
    // Return whatever has arrived; the client keeps polling this endpoint (no blocking wait).
    Collection<ClusterJdbcLogInfoResult> clusterJdbcLogInfoResults =
        clusterJdbcLogInfoExecutorService.getClusterEvaluationResults(uuid);
    if (clusterJdbcLogInfoResults != null && !clusterJdbcLogInfoResults.isEmpty()) {
      return clusterJdbcLogInfoResults;
    }
    return CollectionUtils.emptyCollection();
  }

  @PostMapping(value = "/cluster/database/clearlog", headers = "Accept=application/json")
  @ResponseBody
  public Map<String, Object> databaseClearLog() {
    return dbFacade.databaseClearLog();
  }

}
