package com.sap.commerce.clusterhac.service;

import static java.util.Optional.ofNullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sap.commerce.clusterhac.data.ClusterDetailResult;
import com.sap.commerce.clusterhac.event.ClusterViewEvent;
import de.hybris.platform.servicelayer.event.EventService;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClusterViewExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterViewExecutorService.class);

  private final EventService eventService;
  private final Cache<String, Collection<ClusterDetailResult>> clusterCacheResults;

  public ClusterViewExecutorService(
      EventService eventService,
      Cache<String, Collection<ClusterDetailResult>> clusterCacheResults) {
    this.eventService = eventService;
    this.clusterCacheResults = clusterCacheResults;
  }

  protected static Cache<String, Collection<ClusterDetailResult>> createCacheInstance() {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
  }

  public void execute() {
    LOG.debug("Publishing cluster view event  on all nodes:");
    eventService.publishEvent(new ClusterViewEvent());
  }

  public String submit() {
    LOG.debug("Publishing cluster cache event  on all nodes: ");
    String uuid = UUID.randomUUID().toString();
    clusterCacheResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ClusterViewEvent((uuid)));
    return uuid;
  }

  public void attachResult(String uuid, ClusterDetailResult result) {
    ofNullable(clusterCacheResults.getIfPresent(uuid)).ifPresent(
        collection -> collection.add(result));
  }

  public Collection<ClusterDetailResult> getClusterEvaluationResults(String uuid) {
    return ofNullable(clusterCacheResults.getIfPresent(uuid)).orElseGet(Collections::emptyList);
  }
}
