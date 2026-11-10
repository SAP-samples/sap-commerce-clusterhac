/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.sap.commerce.clusterhac.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.hac.scripting.ScriptingLanguageExecutor;
import de.hybris.platform.servicelayer.cluster.ClusterService;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


/**
 * Unit tests for {@link DefaultScriptExecutorService}. Focus is the classpath-script loading and the
 * <code>???key???</code> placeholder substitution done by {@code getScript(name, params)} — pure logic,
 * exercised against the real shipped scripts on the test classpath. The executor/cluster collaborators
 * are mocked but not needed for these paths.
 */
@UnitTest
@ExtendWith(MockitoExtension.class)
class DefaultScriptExecutorServiceTest
{
	@Mock
	private ScriptingLanguageExecutor executor;
	@Mock
	private ClusterService clusterService;

	private DefaultScriptExecutorService service;

	@BeforeEach
	void setUp()
	{
		service = new DefaultScriptExecutorService(executor, clusterService);
	}

	@Test
	void loadsScriptFromClasspath() throws Exception
	{
		final String script = service.getScript("property/fetch-property.groovy");

		assertNotNull(script, "script should be loaded from /scripts on the classpath");
		assertFalse(script.isEmpty(), "script should not be empty");
	}

	@Test
	void substitutesPlaceholderWithParamValue() throws Exception
	{
		final Map<String, Object> params = new HashMap<>();
		params.put("CONFIG_KEY", "media.globalSettings");

		final String script = service.getScript("property/fetch-property.groovy", params);

		assertTrue(script.contains("media.globalSettings"), "placeholder value should be substituted in");
		assertFalse(script.contains("???CONFIG_KEY???"), "no ???CONFIG_KEY??? token should remain");
	}

	@Test
	void leavesUnknownPlaceholdersUntouched() throws Exception
	{
		// A param that doesn't match any token must not corrupt the script; the real token stays.
		final Map<String, Object> params = new HashMap<>();
		params.put("NOT_A_TOKEN", "x");

		final String script = service.getScript("property/fetch-property.groovy", params);

		assertTrue(script.contains("???CONFIG_KEY???"), "unmatched token should remain untouched");
	}

	@Test
	void emptyParamsLeaveScriptWithPlaceholders() throws Exception
	{
		final String script = service.getScript("property/fetch-property.groovy");

		assertTrue(script.contains("???CONFIG_KEY???"), "with no params the raw placeholder is preserved");
	}
}
