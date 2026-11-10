package com.sap.commerce.clusterhac.service;

import static java.util.Optional.ofNullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sap.commerce.clusterhac.data.ClusterJdbcLogInfoResult;
import com.sap.commerce.clusterhac.event.ClusterJdbcLogInfoEvent;
import de.hybris.platform.servicelayer.event.EventService;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClusterJdbcLogInfoExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(
      ClusterJdbcLogInfoExecutorService.class);

  private final EventService eventService;
  private final Cache<String, Collection<ClusterJdbcLogInfoResult>> clusterJdbcLogInfoResults;

  public ClusterJdbcLogInfoExecutorService(
      EventService eventService,
      Cache<String, Collection<ClusterJdbcLogInfoResult>> clusterJdbcLogInfoResults) {
    this.eventService = eventService;
    this.clusterJdbcLogInfoResults = clusterJdbcLogInfoResults;
  }

  protected static Cache<String, Collection<ClusterJdbcLogInfoResult>> createCacheInstance() {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
  }

  public void execute() {
    LOG.debug("Publishing cluster view event  on all nodes:");
    eventService.publishEvent(new ClusterJdbcLogInfoEvent());
  }

  public String submit() {
    LOG.debug("Publishing cluster JDBC log event  on all nodes: ");
    String uuid = UUID.randomUUID().toString();
    clusterJdbcLogInfoResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ClusterJdbcLogInfoEvent((uuid)));
    return uuid;
  }

  public void attachResult(String uuid, ClusterJdbcLogInfoResult result) {
    ofNullable(clusterJdbcLogInfoResults.getIfPresent(uuid)).ifPresent(
        collection -> collection.add(result));
  }

  public Collection<ClusterJdbcLogInfoResult> getClusterEvaluationResults(String uuid) {
    return ofNullable(clusterJdbcLogInfoResults.getIfPresent(uuid)).orElseGet(
        Collections::emptyList);
  }
}
