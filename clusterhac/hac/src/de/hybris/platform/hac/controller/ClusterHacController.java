/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import java.util.List;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Controller;

/**
 *
 */
@Controller
public class ClusterHacController {

  private final String HAC_CLUSTER_NODE_GROUPS = "hac.cluster.node.groups";
  @Resource
  private ConfigurationService configurationService;

  public List<String> getClusterNodeGroups() {
    final String nodeGroups = configurationService.getConfiguration()
        .getString(HAC_CLUSTER_NODE_GROUPS, "backgroundProcessing,backoffice,api,accstorefront");
    final List<String> nodeGroupsList = List.of(nodeGroups.split(","));
    return nodeGroupsList;
  }
}
