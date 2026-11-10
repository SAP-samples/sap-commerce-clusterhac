package de.hybris.platform.hac.controller;

import com.sap.commerce.clusterhac.data.ScriptEvaluationResult;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.hac.facade.HacDynamicLanguagesFacade;
import de.hybris.platform.scripting.engine.internal.ScriptEngineType;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.user.UserService;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/cluster/scripting")
public class ClusterScriptingController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String nodeGroupAll = "all";
  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;
  @SuppressWarnings("unused")
  @Resource
  private ConfigurationService configurationService;
  @Resource
  private HacDynamicLanguagesFacade hacDynamicLanguagesFacade;
  @Resource
  private UserService userService;
  @Value("${hac.extlinks.wiki.scripting:https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD}")
  private String scriptingHelpUrl;

  @ModelAttribute("scriptingLanguages")
  public Set<ScriptEngineType> getScriptingLanguages() {
    return this.hacDynamicLanguagesFacade.getRegisteredEngineTypes();
  }

  @GetMapping
  public String clusterView(final Model model) {
    model.addAttribute("scriptingHelpUrl", this.scriptingHelpUrl);
    model.addAttribute("pageTitle", "Cluster Scripting");
    List<String> nodeGroups = new ArrayList<>();
    nodeGroups.add(nodeGroupAll);
    nodeGroups.addAll(getClusterNodeGroups());
    model.addAttribute("nodeGroups", nodeGroups);
    return "scripting";
  }

  @PostMapping(value = "/execute", produces = "application/json")
  @ResponseBody
  public Map<String, String> execute(@RequestParam String script,
      @RequestParam(required = false) Collection<String> clusterNodeGroups,
      @RequestParam(required = false) Collection<String> nodeIds) {
    final UserModel currentUser = userService.getCurrentUser();
    // Pod-level sub-selection takes precedence: if specific node IDs are given, target only those.
    if (nodeIds != null && !nodeIds.isEmpty()) {
      LOG.info("[{}] executed script on cluster from HAC: nodeIds={}, script=[{}]",
          currentUser.getUid(), nodeIds, script);
      return Map.of("uuid", clusterScriptExecutorService.submitToNodes(script, nodeIds));
    }
    Collection<String> groups = clusterNodeGroups == null ? java.util.List.of() : clusterNodeGroups;
    LOG.info("[{}] executed script on cluster from HAC: eventNodes={}, script=[{}]",
        currentUser.getUid(), groups, script);
    LOG.debug("Cluster Node Groups: {}", groups);
    if (groups.contains(nodeGroupAll)) {
      groups = getClusterNodeGroups();
    }
    String uuid = clusterScriptExecutorService.submit(script, groups);
    // return JSON object with uuid to avoid quoted string issues on the client
    return Map.of("uuid", uuid);
  }

  @GetMapping(value = "/results", produces = "application/json")
  @ResponseBody
  public Collection<ScriptEvaluationResult> getScriptEvaluationResults(@RequestParam String uuid) {
    return clusterScriptExecutorService.getScriptEvaluationResults(uuid);
  }

}
