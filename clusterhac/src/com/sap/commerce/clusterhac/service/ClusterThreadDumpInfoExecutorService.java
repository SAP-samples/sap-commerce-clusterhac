package com.sap.commerce.clusterhac.service;

import static java.util.Optional.ofNullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sap.commerce.clusterhac.data.ClusterJdbcLogInfoResult;
import com.sap.commerce.clusterhac.data.ClusterThreadDumpInfoResult;
import com.sap.commerce.clusterhac.event.ClusterJdbcLogInfoEvent;
import com.sap.commerce.clusterhac.event.ClusterThreadDumpInfoEvent;
import de.hybris.platform.servicelayer.event.EventService;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClusterThreadDumpInfoExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(
      ClusterThreadDumpInfoExecutorService.class);

  private final EventService eventService;
  private final Cache<String, Collection<ClusterThreadDumpInfoResult>> clusterThreadDumpInfoResults;

  public ClusterThreadDumpInfoExecutorService(
      EventService eventService,
      Cache<String, Collection<ClusterThreadDumpInfoResult>> clusterThreadDumpInfoResults) {
    this.eventService = eventService;
    this.clusterThreadDumpInfoResults = clusterThreadDumpInfoResults;
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
    LOG.debug("Publishing cluster thread dump event  on all nodes: ");
    String uuid = UUID.randomUUID().toString();
    clusterThreadDumpInfoResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ClusterThreadDumpInfoEvent((uuid)));
    return uuid;
  }

  public void attachResult(String uuid, ClusterThreadDumpInfoResult result) {
    ofNullable(clusterThreadDumpInfoResults.getIfPresent(uuid)).ifPresent(
        collection -> collection.add(result));
  }

  public Collection<ClusterThreadDumpInfoResult> getClusterEvaluationResults(String uuid) {
    return ofNullable(clusterThreadDumpInfoResults.getIfPresent(uuid)).orElseGet(
        Collections::emptyList);
  }
}
