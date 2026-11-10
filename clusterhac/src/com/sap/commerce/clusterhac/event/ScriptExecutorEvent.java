package com.sap.commerce.clusterhac.event;

import de.hybris.platform.servicelayer.event.ClusterAwareEvent;
import de.hybris.platform.servicelayer.event.PublishEventContext;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.collections4.CollectionUtils;

public class ScriptExecutorEvent extends AbstractEvent implements ClusterAwareEvent {

  private final String script;
  private final Collection<String> nodeGroups;
  private String uuid;
  private String nodeId;
  // Optional set of specific node IDs to target (pod-level sub-selection). When non-empty, a
  // node runs the script if its cluster id is in this set (in addition to the group match).
  private Collection<String> nodeIds;

  public ScriptExecutorEvent(String uuid, String nodeId, String script,
      Collection<String> nodeGroups) {
    this.uuid = uuid;
    this.script = script;
    this.nodeId = nodeId;
    this.nodeGroups =
        nodeGroups == null ? CollectionUtils.emptyCollection() : new ArrayList<>(nodeGroups);
    this.nodeIds = CollectionUtils.emptyCollection();
  }

  public ScriptExecutorEvent(String uuid, String script, Collection<String> nodeGroups,
      Collection<String> nodeIds) {
    this.uuid = uuid;
    this.script = script;
    this.nodeGroups =
        nodeGroups == null ? CollectionUtils.emptyCollection() : new ArrayList<>(nodeGroups);
    this.nodeIds =
        nodeIds == null ? CollectionUtils.emptyCollection() : new ArrayList<>(nodeIds);
  }

  public ScriptExecutorEvent(String script, Collection<String> nodeGroups) {
    this.script = script;
    this.nodeGroups =
        nodeGroups == null ? CollectionUtils.emptyCollection() : new ArrayList<>(nodeGroups);
    this.nodeIds = CollectionUtils.emptyCollection();
  }

  public Collection<String> getNodeIds() {
    return nodeIds == null ? CollectionUtils.emptyCollection() : nodeIds;
  }

  public String getUuid() {
    return uuid;
  }

  public String getScript() {
    return script;
  }

  public Collection<String> getNodeGroups() {
    return nodeGroups;
  }

  public String getNodeId() {
    return nodeId;
  }

  @Override
  public boolean canPublish(PublishEventContext publishEventContext) {
    return true;
  }

}
