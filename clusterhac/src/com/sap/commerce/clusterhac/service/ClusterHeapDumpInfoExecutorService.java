package com.sap.commerce.clusterhac.service;

import static java.util.Optional.ofNullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sap.commerce.clusterhac.data.ClusterHeapDumpInfoResult;
import com.sap.commerce.clusterhac.event.ClusterHeapDumpInfoEvent;
import de.hybris.platform.servicelayer.event.EventService;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Submits a heap-dump status round-trip and collects the per-node replies into a UUID-keyed Guava
 * cache the controller polls. Mirrors {@code ClusterThreadDumpInfoExecutorService}.
 */
public class ClusterHeapDumpInfoExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(
      ClusterHeapDumpInfoExecutorService.class);

  private final EventService eventService;
  private final Cache<String, Collection<ClusterHeapDumpInfoResult>> clusterHeapDumpInfoResults;

  public ClusterHeapDumpInfoExecutorService(EventService eventService,
      Cache<String, Collection<ClusterHeapDumpInfoResult>> clusterHeapDumpInfoResults) {
    this.eventService = eventService;
    this.clusterHeapDumpInfoResults = clusterHeapDumpInfoResults;
  }

  protected static Cache<String, Collection<ClusterHeapDumpInfoResult>> createCacheInstance() {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
  }

  public String submit() {
    LOG.debug("Publishing cluster heap dump info event on all nodes");
    String uuid = UUID.randomUUID().toString();
    clusterHeapDumpInfoResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ClusterHeapDumpInfoEvent(uuid));
    return uuid;
  }

  public void attachResult(String uuid, ClusterHeapDumpInfoResult result) {
    ofNullable(clusterHeapDumpInfoResults.getIfPresent(uuid)).ifPresent(
        collection -> collection.add(result));
  }

  public Collection<ClusterHeapDumpInfoResult> getClusterEvaluationResults(String uuid) {
    return ofNullable(clusterHeapDumpInfoResults.getIfPresent(uuid)).orElseGet(
        Collections::emptyList);
  }
}
