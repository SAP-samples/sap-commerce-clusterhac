/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package de.hybris.platform.hac.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.commerce.clusterhac.data.ClusterCacheResult;
import com.sap.commerce.clusterhac.data.ScriptEvaluationResult;
import com.sap.commerce.clusterhac.service.ClusterCacheExecutorService;
import com.sap.commerce.clusterhac.service.ClusterScriptExecutorService;
import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.hac.data.dto.cache.region.RegionCacheData;
import de.hybris.platform.hac.facade.HacCacheFacade;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class ClusterCacheController extends ClusterHacController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String VIEW_BASE = "cache/";
  private static final String TOP_OBJECTS_SCRIPT = "cache/top-objects.groovy";
  private static final String CLEAR_CACHE_SCRIPT = "cache/clear-cache.groovy";
  private static final int DEFAULT_TOP_LIMIT = 20;

  private final HacCacheFacade cacheFacade;

  @Value("${hac.extlinks.wiki.cache:https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD}")
  private String wikiCache;

  @Resource
  private ClusterCacheExecutorService clusterCacheExecutorService;

  @Resource
  private ClusterScriptExecutorService clusterScriptExecutorService;

  @Resource
  private ScriptExecutorService scriptExecutorService;

  @Autowired
  public ClusterCacheController(final HacCacheFacade cacheFacade) {
    this.cacheFacade = cacheFacade;
  }


  @RequestMapping(value = "/cluster/monitoring/cache/{clusterID}", method = RequestMethod.GET)
  public String cache(@PathVariable String clusterID,
      @RequestParam(required = false) String host, final Model model) {
    model.addAttribute("wikiCache", wikiCache);
    model.addAttribute("clusterID", clusterID);
    model.addAttribute("host", host);

    if (cacheFacade.isRegionCacheEnabled()) {
      java.util.List<de.hybris.platform.regioncache.region.CacheRegion> regions =
          cacheFacade.getCacheRegions();
      model.addAttribute("regions", regions);

      // Customer-nominated "pinned" regions (config: clusterhac.cache.pinnedRegions, comma-sep,
      // max 5) get promoted to their own sub-tab (between Query and Other) and are moved OUT of
      // Other. Unknown names are skipped with a WARN so a mistyped config is visible in the log.
      java.util.List<String> pinnedNames = new java.util.ArrayList<>();
      String pinnedProp = de.hybris.platform.util.Config.getString("clusterhac.cache.pinnedRegions", "");
      if (pinnedProp != null && !pinnedProp.isBlank()) {
        for (String n : pinnedProp.split(",")) {
          String name = n.trim();
          if (!name.isEmpty() && !pinnedNames.contains(name)) { pinnedNames.add(name); }
        }
      }
      if (pinnedNames.size() > 5) {
        LOG.warn("clusterhac.cache.pinnedRegions has {} entries; keeping the first 5, dropping {}",
            pinnedNames.size(), pinnedNames.subList(5, pinnedNames.size()));
        pinnedNames = new java.util.ArrayList<>(pinnedNames.subList(0, 5));
      }

      // Bucket regions for the sub-tab split: entity + query get their own tabs, pinned regions
      // get their own tabs, the rest (including the type-system caches) go under "Other regions".
      java.util.List<de.hybris.platform.regioncache.region.CacheRegion> entityRegions =
          new java.util.ArrayList<>();
      java.util.List<de.hybris.platform.regioncache.region.CacheRegion> queryRegions =
          new java.util.ArrayList<>();
      java.util.List<de.hybris.platform.regioncache.region.CacheRegion> otherRegions =
          new java.util.ArrayList<>();
      java.util.Map<String, de.hybris.platform.regioncache.region.CacheRegion> byName =
          new java.util.HashMap<>();
      for (de.hybris.platform.regioncache.region.CacheRegion region : regions) {
        String name = region.getName();
        byName.put(name, region);
        if ("entityCacheRegion".equals(name)) {
          entityRegions.add(region);
        } else if ("queryCacheRegion".equals(name)) {
          queryRegions.add(region);
        } else if (!pinnedNames.contains(name)) {
          otherRegions.add(region);
        }
        // pinned regions are collected below, in config order, and excluded from Other above.
      }
      // Pinned regions in config order; skip (and warn) names with no matching live region.
      java.util.List<de.hybris.platform.regioncache.region.CacheRegion> pinnedRegions =
          new java.util.ArrayList<>();
      for (String name : pinnedNames) {
        de.hybris.platform.regioncache.region.CacheRegion region = byName.get(name);
        if (region != null) {
          pinnedRegions.add(region);
        } else {
          LOG.warn("Pinned cache region not found on this node (skipped): {}", name);
        }
      }
      model.addAttribute("entityRegions", entityRegions);
      model.addAttribute("queryRegions", queryRegions);
      model.addAttribute("pinnedRegions", pinnedRegions);
      model.addAttribute("otherRegions", otherRegions);

      // Per-region eviction policy: <regionName>.region.cache.eviction.policy. If a region has
      // its own override, show it; otherwise show "default" (the region inherits the global
      // cache.evictionpolicy rather than defining its own).
      java.util.Map<String, String> evictionPolicies = new java.util.HashMap<>();
      for (de.hybris.platform.regioncache.region.CacheRegion region : regions) {
        String name = region.getName();
        String override = de.hybris.platform.util.Config.getString(
            name + ".region.cache.eviction.policy", null);
        String display = (override != null && !override.isBlank()) ? override.trim() : "default";
        evictionPolicies.put(name, display);
      }
      model.addAttribute("evictionPolicies", evictionPolicies);
      return VIEW_BASE + "regionCache";
    }
    return VIEW_BASE + "404";
  }

  @RequestMapping(value = "/cluster/monitoring/cache/regionCache/data", headers = "Accept=application/json", method = RequestMethod.GET)
  @ResponseBody
  public RegionCacheData regionCacheData() {
    return cacheFacade.getRegionCacheData();
  }

  @RequestMapping(value = "/cluster/monitoring/cache/regionCache/data/{clusterID}", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public java.util.Map<String, String> regionClusterCacheData(@PathVariable String clusterID) {
    String uuid = clusterCacheExecutorService.submit(Integer.valueOf(clusterID));
    return java.util.Map.of("uuid", uuid);
  }

  @RequestMapping(value = "/cluster/monitoring/cache/regionCache/data/results/{clusterID}", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public RegionCacheData regionCacheData(@RequestParam String uuid) {
    return getRegionCacheData(uuid);
  }

  private RegionCacheData getRegionCacheData(String uuid) {
    final ObjectMapper objectMapper = new ObjectMapper();
    ClusterCacheResult clusterCacheResult;
    Collection<String> clusterCacheResults;
    try {
      clusterCacheResults = clusterCacheExecutorService.getClusterEvaluationResults(uuid);
      if (!clusterCacheResults.isEmpty()) {
        clusterCacheResult = objectMapper.readValue(clusterCacheResults.iterator().next(),
            ClusterCacheResult.class);
        return clusterCacheResult.getRegionCacheData();
      }
    } catch (JsonProcessingException e) {
      LOG.error("Failed to parse cluster cache result for uuid {}", uuid, e);
    }
    // Results not ready yet: return an empty (but valid) JSON object so the client's
    // JSON.parse never sees an empty body. The client keeps polling until regions arrive.
    return new RegionCacheData();
  }


  @RequestMapping(value = "/cluster/monitoring/cache/regionCache/clear", method = RequestMethod.POST, headers = "Accept=application/json")
  @ResponseBody
  public RegionCacheData clearRegionCache() {
    cacheFacade.clearCache();
    return cacheFacade.getRegionCacheData();
  }

  @RequestMapping(value = "/cluster/monitoring/cache/regionCache/clear/{clusterID}", method = RequestMethod.POST, headers = "Accept=application/json")
  @ResponseBody
  public String clearClusterRegionCache(@PathVariable String clusterID) {
    return clusterCacheExecutorService.submit(Integer.valueOf(clusterID), true);
  }

  /**
   * Triggers the top-objects Groovy script for a single region on the target node. Returns a
   * uuid the client polls for results. Uses the existing cluster script pipeline (no new
   * event/listener classes) and the public CacheRegion.getAllKeys() API in the script.
   */
  @RequestMapping(value = "/cluster/monitoring/cache/topObjects/{clusterID}", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public Map<String, String> topObjects(@PathVariable String clusterID,
      @RequestParam String regionName,
      @RequestParam(required = false, defaultValue = "20") int limit) {
    try {
      int safeLimit = limit > 0 ? limit : DEFAULT_TOP_LIMIT;
      Map<String, Object> params = Map.of(
          "REGION_NAME", regionName,
          "LIMIT", String.valueOf(safeLimit));
      String script = scriptExecutorService.getScript(TOP_OBJECTS_SCRIPT, params);
      String uuid = clusterScriptExecutorService.submit(script, clusterID);
      return Map.of("uuid", uuid);
    } catch (Exception e) {
      LOG.error("Failed to submit top-objects script for region {} on node {}", regionName,
          clusterID, e);
      return Map.of("error", e.getMessage() == null ? "unknown error" : e.getMessage());
    }
  }

  @GetMapping(value = "/cluster/monitoring/cache/topObjects/results", produces = "application/json")
  @ResponseBody
  public Collection<ScriptEvaluationResult> topObjectsResults(@RequestParam String uuid) {
    return clusterScriptExecutorService.getScriptEvaluationResults(uuid);
  }

  /**
   * Clears a SINGLE cache region on the target node (as opposed to the node-wide clear-all).
   * Reuses the cluster script pipeline: runs clear-cache.groovy with the region name (its
   * per-region branch calls region.clearCache()). Returns a uuid the client polls via the
   * shared topObjects results endpoint.
   */
  @RequestMapping(value = "/cluster/monitoring/cache/regionCache/clearRegion/{clusterID}", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public Map<String, String> clearRegion(@PathVariable String clusterID,
      @RequestParam String regionName) {
    try {
      Map<String, Object> params = Map.of("REGION_NAME", regionName);
      String script = scriptExecutorService.getScript(CLEAR_CACHE_SCRIPT, params);
      String uuid = clusterScriptExecutorService.submit(script, clusterID);
      return Map.of("uuid", uuid);
    } catch (Exception e) {
      LOG.error("Failed to submit clear-region script for region {} on node {}", regionName,
          clusterID, e);
      return Map.of("error", e.getMessage() == null ? "unknown error" : e.getMessage());
    }
  }

}
