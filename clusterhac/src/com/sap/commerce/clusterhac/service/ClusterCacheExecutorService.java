package com.sap.commerce.clusterhac.service;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Optional.ofNullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sap.commerce.clusterhac.event.ClusterCacheEvent;
import de.hybris.platform.servicelayer.event.EventService;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClusterCacheExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterCacheExecutorService.class);

  private final EventService eventService;
  private final Cache<String, Collection<String>> clusterCacheResults;

  public ClusterCacheExecutorService(
      EventService eventService,
      Cache<String, Collection<String>> clusterCacheResults) {
    this.eventService = eventService;
    this.clusterCacheResults = clusterCacheResults;
  }

  protected static Cache<String, Collection<String>> createCacheInstance() {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
  }

  public void execute(int clusterID) {
    checkNotNull(clusterID, "clusterID cannot be null");
    LOG.debug("Publishing cluster cache event  on all nodes: {}", clusterID);
    eventService.publishEvent(new ClusterCacheEvent(clusterID));
  }

  public String submit(int clusterID) {
    return submit(clusterID, false);
  }

  public String submit(int clusterID, boolean clearClusterCache) {
    checkNotNull(clusterID, "clusterID cannot be null");
    LOG.debug("Publishing cluster cache event  on all nodes: {}", clusterID);
    String uuid = UUID.randomUUID().toString();
    clusterCacheResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ClusterCacheEvent(uuid, clusterID, clearClusterCache));
    return uuid;
  }

  public void attachResult(String uuid, String result) {
    ofNullable(clusterCacheResults.getIfPresent(uuid)).ifPresent(
        collection -> collection.add(result));
  }

  public Collection<String> getClusterEvaluationResults(String uuid) {
    return ofNullable(clusterCacheResults.getIfPresent(uuid)).orElseGet(Collections::emptyList);
  }
}
