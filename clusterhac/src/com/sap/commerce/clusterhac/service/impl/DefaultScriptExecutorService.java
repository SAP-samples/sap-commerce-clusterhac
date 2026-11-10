package com.sap.commerce.clusterhac.service.impl;


import com.sap.commerce.clusterhac.service.ScriptExecutorService;
import de.hybris.platform.hac.scripting.ScriptingLanguageExecutor;
import de.hybris.platform.servicelayer.cluster.ClusterService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;


public class DefaultScriptExecutorService implements ScriptExecutorService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultScriptExecutorService.class);
  private static final String SCRIPT_OUT_WRITER = "out";
  private static final String CLUSTER_GROUPS = "ClusterGroups";
  private static final String HOST_NAME = "HostName";
  private static final String STACKTRACE_TEXT_KEY = "stacktraceText";
  private static final String OUTPUT_TEXT_KEY = "outputText";
  private static final String EXECUTION_RESULT_KEY = "executionResult";
  private static final String GROOVY = "groovy";

  private final ScriptingLanguageExecutor executor;
  private final ClusterService clusterService;

  public DefaultScriptExecutorService(ScriptingLanguageExecutor executor,
      ClusterService clusterService) {
    this.executor = executor;
    this.clusterService = clusterService;
  }

  @Override
  public void executeScript(String script, boolean commitMode) {
    executor.executeScript(GROOVY, script, commitMode);
  }

  @Override
  public String getHostname() {
    try {
      String newScript = getScript("hostname/hostname.groovy");
      Map<String, Object> results = executor.executeScript(GROOVY, newScript, false);
      return String.valueOf(results.get(EXECUTION_RESULT_KEY));
    } catch (Exception e) {
      LOG.error("Failed to resolve hostname via hostname.groovy", e);
    }
    return null;
  }

  @Override
  public String getNodeInfo() {
    try {
      String newScript = getScript("cluster/node-info.groovy");
      Map<String, Object> results = executor.executeScript(GROOVY, newScript, false);
      return String.valueOf(results.get(EXECUTION_RESULT_KEY));
    } catch (Exception e) {
      LOG.error("Failed to collect node info via node-info.groovy", e);
    }
    return null;
  }

  @Override
  public String getclusterGroupAndHostName() {
    Map<String, String> clusterGroupAndHostName = new HashMap<String, String>();
    clusterGroupAndHostName.put(CLUSTER_GROUPS,
        StringUtils.collectionToCommaDelimitedString(clusterService.getClusterGroups()));
    clusterGroupAndHostName.put(HOST_NAME,
        getHostname());
    return org.apache.commons.lang3.StringUtils.join(clusterGroupAndHostName);
  }

  public String getScript(String scriptName) throws Exception {
    return getScript(scriptName, Collections.emptyMap());
  }

  public String getScript(String scriptName, Map<String, Object> params) throws Exception {
    String resourcePath = String.format("/scripts/%s", scriptName);
    String groovyScript;
    try (InputStream resourceAsStream = getClass().getResourceAsStream(resourcePath)) {
      if (resourceAsStream == null) {
        throw new IOException("Script not found on classpath: " + resourcePath);
      }
      groovyScript = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
    }
    if (params != null && !params.isEmpty()) {
      groovyScript = applyPlaceholders(groovyScript, params);
    }
    return groovyScript;
  }

  private String applyPlaceholders(String script, Map<String, Object> params) {
    String newScript = script;
    for (Map.Entry<String, Object> entry : params.entrySet()) {
      String k = entry.getKey();
      Object v = entry.getValue();
      newScript = newScript.replace(String.format("???%s???", k), String.valueOf(v));
    }
    return newScript;
  }

}
