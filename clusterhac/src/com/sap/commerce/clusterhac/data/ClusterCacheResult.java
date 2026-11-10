/*
 * ----------------------------------------------------------------
 * --- WARNING: THIS FILE IS GENERATED AND WILL BE OVERWRITTEN!
 * --- Generated at 11 Nov. 2021, 8:42:29 pm
 * ----------------------------------------------------------------
 *
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.sap.commerce.clusterhac.data;


import de.hybris.platform.hac.data.dto.cache.region.RegionCacheData;


public  class ClusterCacheResult
{

	/** <i>Generated property</i> for <code>ClusterCacheResult.node</code> property defined at extension <code>azuredbhac</code>. */

	private int node;

	/** <i>Generated property</i> for <code>ClusterCacheResult.executionResult</code> property defined at extension <code>azuredbhac</code>. */

	private String executionResult;

	/** <i>Generated property</i> for <code>ClusterCacheResult.regionCacheData</code> property defined at extension <code>azuredbhac</code>. */

	private RegionCacheData regionCacheData;

	public ClusterCacheResult()
	{
		// default constructor
	}

	public void setNode(final int node)
	{
		this.node = node;
	}

	public int getNode()
	{
		return node;
	}

	public void setExecutionResult(final String executionResult)
	{
		this.executionResult = executionResult;
	}

	public String getExecutionResult()
	{
		return executionResult;
	}

	public void setRegionCacheData(final RegionCacheData regionCacheData)
	{
		this.regionCacheData = regionCacheData;
	}

	public RegionCacheData getRegionCacheData()
	{
		return regionCacheData;
	}


}