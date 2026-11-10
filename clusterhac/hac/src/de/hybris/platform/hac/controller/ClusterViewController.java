/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller;

import com.sap.commerce.clusterhac.data.ClusterDetailResult;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.ClusterViewExecutorService;
import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.hac.facade.HacCacheFacade;
import de.hybris.platform.hac.facade.HacClusterFacade;
import de.hybris.platform.servicelayer.user.UserService;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller()
public class ClusterViewController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String nodeGroupAll = "all";
  private final HacCacheFacade cacheFacade;
  @Value("${hac.extlinks.wiki.cluster:https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD}")
  private String wikiCluster;
  @Resource
  private UserService userService;
  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;
  @Resource
  private ScriptExecutorService scriptExecutorService;
  @Resource
  private ClusterViewExecutorService clusterViewExecutorService;

  @Autowired
  public ClusterViewController(@SuppressWarnings("unused") final HacClusterFacade clusterFacade,
      HacCacheFacade cacheFacade) {
    this.cacheFacade = cacheFacade;
  }

  @RequestMapping(value = "/cluster/monitoring")
  public String cluster(final Model model) {
    model.addAttribute("wikiCluster", wikiCluster);
    model.addAttribute("regions", cacheFacade.getCacheRegions());
    return "clusterview";
  }

  @ModelAttribute("clusterNodeGroups")
  public List<String> getClusterNodeGroupsModel() {
    List<String> nodeGroups = new ArrayList<>();
    nodeGroups.add(nodeGroupAll);
    nodeGroups.addAll(getClusterNodeGroups());
    return nodeGroups;
  }

  @RequestMapping(value = "/cluster/monitoring/data", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public Map<String, String> clusterData() {
    // Return a JSON object with the UUID instead of a raw string
    return Map.of("uuid", clusterViewExecutorService.submit());
  }

  @GetMapping(value = "/cluster/monitoring/results", produces = "application/json")
  @ResponseBody
  public Collection<ClusterDetailResult> getScriptEvaluationResults(@RequestParam String uuid) {
    return clusterViewExecutorService.getClusterEvaluationResults(uuid);
  }

  @PostMapping("/cluster/monitoring/data/regionCache/clear")
  @ResponseBody
  public String execute(
      @RequestParam Collection<String> clusterNodeGroups, @RequestParam String regionName) {

    try {
      Map<String, Object> params = Map.of("REGION_NAME", regionName);

      String newScript = scriptExecutorService.getScript("cache/clear-cache.groovy", params);

      final UserModel currentUser = userService.getCurrentUser();
      LOG.info("[{}] executed clear script on cluster from HAC: eventNodes={}, script=[{}]",
          currentUser.getUid(),
          clusterNodeGroups,
          newScript);

      LOG.debug("Cluster Node Groups: {}", clusterNodeGroups);

      if (clusterNodeGroups.contains(nodeGroupAll)) {
        clusterNodeGroups = getClusterNodeGroups();
      }
      return clusterScriptExecutorService.submit(newScript, clusterNodeGroups);
    } catch (Exception ex) {
      return ex.getMessage();
    }
  }

}