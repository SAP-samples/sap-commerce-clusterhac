package com.sap.commerce.clusterhac.service;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Optional.ofNullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sap.commerce.clusterhac.data.ScriptEvaluationResult;
import com.sap.commerce.clusterhac.event.ScriptExecutorEvent;
import de.hybris.platform.servicelayer.event.EventService;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClusterScriptExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterScriptExecutorService.class);

  private final EventService eventService;
  private final Cache<String, Collection<ScriptEvaluationResult>> scriptEvaluationResults;

  public ClusterScriptExecutorService(
      EventService eventService,
      Cache<String, Collection<ScriptEvaluationResult>> scriptEvaluationResults) {
    this.eventService = eventService;
    this.scriptEvaluationResults = scriptEvaluationResults;
  }

  protected static Cache<String, Collection<ScriptEvaluationResult>> createCacheInstance() {
    return CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
  }

  public void execute(String script, Collection<String> nodeGroups) {
    checkNotNull(script, "Script cannot be null");
    LOG.debug("Publishing cluster event script on all nodes: {}", script);
    eventService.publishEvent(new ScriptExecutorEvent(script, nodeGroups));
  }

  /** Fire-and-forget execution targeting an explicit set of node IDs (pod-level sub-selection). */
  public void executeOnNodes(String script, Collection<String> nodeIds) {
    checkNotNull(script, "Script cannot be null");
    LOG.debug("Publishing cluster event script to node ids: {}", nodeIds);
    eventService.publishEvent(new ScriptExecutorEvent(null, script,
        CollectionUtils.emptyCollection(), nodeIds));
  }

  public String submit(String script, Collection<String> nodeGroups) {
    return submit(script, null, nodeGroups);
  }

  public String submit(String script, String nodeID) {
    return submit(script, nodeID, CollectionUtils.emptyCollection());
  }

  /** Submit targeting an explicit set of node IDs (pod-level sub-selection). */
  public String submitToNodes(String script, Collection<String> nodeIds) {
    checkNotNull(script, "Script cannot be null");
    String uuid = UUID.randomUUID().toString();
    scriptEvaluationResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ScriptExecutorEvent(uuid, script,
        CollectionUtils.emptyCollection(), nodeIds));
    return uuid;
  }

  private String submit(String script, String nodeID, Collection<String> nodeGroups) {
    checkNotNull(script, "Script cannot be null");
    LOG.debug("Publishing cluster event script on all nodes: {}", script);
    String uuid = UUID.randomUUID().toString();
    scriptEvaluationResults.put(uuid, new ConcurrentLinkedQueue<>());
    eventService.publishEvent(new ScriptExecutorEvent(uuid, nodeID, script, nodeGroups));
    return uuid;
  }

  public void attachResult(String uuid, ScriptEvaluationResult result) {
    ofNullable(scriptEvaluationResults.getIfPresent(uuid)).ifPresent(
        collection -> collection.add(result));
  }

  public Collection<ScriptEvaluationResult> getScriptEvaluationResults(String uuid) {
    return ofNullable(scriptEvaluationResults.getIfPresent(uuid)).orElseGet(Collections::emptyList);
  }
}
