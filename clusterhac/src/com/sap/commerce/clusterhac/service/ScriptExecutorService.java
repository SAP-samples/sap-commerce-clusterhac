package com.sap.commerce.clusterhac.service;

import java.util.Map;

public interface ScriptExecutorService {

  void executeScript(String script, boolean commitMode);

  String getScript(String scriptName, Map<String, Object> params) throws Exception;

  String getHostname();

  String getNodeInfo();

  String getclusterGroupAndHostName();

  String getScript(String scriptName) throws Exception;
}
