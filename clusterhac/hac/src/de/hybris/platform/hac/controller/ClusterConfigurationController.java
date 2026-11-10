/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller;

import com.sap.commerce.clusterhac.data.ScriptEvaluationResult;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
@RequestMapping(value = "/cluster/")
public class ClusterConfigurationController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String VIEW_BASE = "config/";
  private static final String nodeGroupAll = "all";

  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;


  @Resource
  private UserService userService;

  @Resource
  private ScriptExecutorService scriptExecutorService;


  @Value("${hac.extlinks.wiki.projectproperties}")
  private String wikiProjectProperties;

  @Value("${hac.extlinks.wiki.configuration}")
  private String wikiConfiguration;


  @RequestMapping(value = "config", method = RequestMethod.GET)
  public String config(final Model model) throws IOException {
    model.addAttribute("wikiProjectProperties", wikiProjectProperties);
    model.addAttribute("wikiConfiguration", wikiConfiguration);
    List<String> nodeGroups = new ArrayList<String>();
    nodeGroups.add(nodeGroupAll);
    nodeGroups.addAll(getClusterNodeGroups());
    model.addAttribute("nodeGroups", nodeGroups);
    return VIEW_BASE + "clusterconfig";
  }

  //Method is called when the Value of a Key has been edited and checks, whether there
  //is a change or not, and, if it has changed, stores the property to edited ones.
  @RequestMapping(value = "config/updateprops/", method = RequestMethod.POST, produces = "application/json")
  @ResponseBody
  public Map<String, String> updateClusterProperties(@RequestParam final String key,
      @RequestParam final String val,
      @RequestParam(required = false) Collection<String> clusterNodeGroups,
      @RequestParam final boolean fetchkey) {
    final UserModel currentUser = userService.getCurrentUser();
    String newScript;
    if (clusterNodeGroups == null) {
      clusterNodeGroups = new ArrayList<>();
    }
    LOG.info("[{}] executed cluster property {} on cluster from HAC: eventNodes={}, key=[{}]",
        currentUser.getUid(), fetchkey ? "fetch" : "update", clusterNodeGroups, key);
    if (!key.matches(ControllerConstants.JAVA_PROPERTY_REGEX)) {
      LOG.debug(String.format("Key '%s' does not match java properties format", key));
      return Map.of("error", ControllerConstants.VALIDATION_ERROR);
    }
    Map<String, Object> params = new HashMap<>();
    params.put("CONFIG_KEY", key);
    if (!fetchkey) {
      params.put("CONFIG_VALUE", escapeGroovyStringLiteral(val));
    }
    try {
      if (fetchkey) {
        newScript = scriptExecutorService.getScript("property/fetch-property.groovy", params);
      } else {
        newScript = scriptExecutorService.getScript("property/change-property.groovy", params);
      }

      if (clusterNodeGroups.contains(nodeGroupAll)) {
        clusterNodeGroups = getClusterNodeGroups();
      }

      String uuid = clusterScriptExecutorService.submit(newScript, clusterNodeGroups);
      return Map.of("uuid", uuid);
    } catch (Exception ex) {
      LOG.error("Failed to {} cluster property [{}]", fetchkey ? "fetch" : "update", key, ex);
      return Map.of("error", StringUtils.defaultString(ex.getMessage(), ControllerConstants.VALIDATION_ERROR));
    }
  }

  @GetMapping(value = "config/results", produces = "application/json")
  @ResponseBody
  public Collection<ScriptEvaluationResult> getScriptEvaluationResults(@RequestParam String uuid) {
    return clusterScriptExecutorService.getScriptEvaluationResults(uuid);
  }

  // The value is substituted into a Groovy double-quoted string literal
  // ("???CONFIG_VALUE???"). Escape the characters that would break out of that
  // literal or trigger GString interpolation. Commas are NOT special in a Groovy
  // string and must be left untouched.
  private static String escapeGroovyStringLiteral(final String val) {
    if (val == null) {
      return null;
    }
    return val.replace("\\", "\\\\")   // backslash first
        .replace("\"", "\\\"")          // closing quote
        .replace("$", "\\$")            // GString interpolation
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

}
