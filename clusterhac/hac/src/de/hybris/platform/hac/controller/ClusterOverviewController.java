/*
 * Copyright (c) 2024 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller;

import com.sap.commerce.clusterhac.data.ClusterDetailResult;
import com.sap.commerce.clusterhac.service.ClusterViewExecutorService;
import java.util.Collection;
import java.util.Map;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Cluster Overview / landing page. Aggregates data already collected by other features (the
 * cluster-view node summary + cache health) into a single at-a-glance dashboard. No new
 * node-side plumbing — it reuses the ClusterView event pipeline for the node strip and the
 * existing cache endpoints for the cache-health summary.
 */
@Controller
public class ClusterOverviewController extends ClusterHacController {

  private static final String VIEW = "overview";

  @Resource
  private ClusterViewExecutorService clusterViewExecutorService;

  @GetMapping(value = "/cluster/overview")
  public String overview(final Model model) {
    return VIEW;
  }

  /** Node summary: reuse the cluster-view scatter/gather (same as the status view). */
  @RequestMapping(value = "/cluster/overview/nodes", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public Map<String, String> nodes() {
    return Map.of("uuid", clusterViewExecutorService.submit());
  }

  @GetMapping(value = "/cluster/overview/nodes/results", produces = "application/json")
  @ResponseBody
  public Collection<ClusterDetailResult> nodeResults(@RequestParam String uuid) {
    return clusterViewExecutorService.getClusterEvaluationResults(uuid);
  }
}
