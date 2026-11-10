<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%--
	Reusable per-region cache block. Expects request-scoped attributes:
	  region    - the RegionCacheData entry (with .name)
	  clusterID - the target cluster node id
	Rendered by regionCache.jsp for the Entity, Query and Other sub-views.
--%>
<div id="div-${region.name}">
	<div class="region-body">
		<div class="region-kpis">
			<div class="region-kpi"><span class="k-label">Max Size</span><span class="k-val" id="${region.name}_maxEntries"></span></div>
			<div class="region-kpi"><span class="k-label">Max Reached</span><span class="k-val" id="${region.name}_maxReachedSize"></span></div>
			<div class="region-kpi"><span class="k-label">Fill Ratio</span><span class="k-val" id="${region.name}_fill"></span></div>
			<div class="region-kpi"><span class="k-label">Hit/Miss Ratio</span><span class="k-val" id="${region.name}_factor"></span></div>
			<div class="region-kpi"><span class="k-label" title="Eviction policy when the region is full">Eviction Policy</span><span class="k-val">${evictionPolicies[region.name]}</span></div>
		</div>
		<table class="region-stats">
			<thead>
			<tr>
				<th>Hits</th>
				<th>Misses</th>
				<th>Invalidations</th>
				<th>Evictions</th>
				<th>Fetches</th>
				<th>InstanceCount</th>
			</tr>
			</thead>
			<tbody>
			<tr>
				<td id="${region.name}_Hits"></td>
				<td id="${region.name}_Misses"></td>
				<td id="${region.name}_Invalidations"></td>
				<td id="${region.name}_Evictions"></td>
				<td id="${region.name}_Fetches"></td>
				<td id="${region.name}_InstanceCount"></td>
			</tr>
			</tbody>
		</table>
		<table id="${region.name}_types" >
			<thead>
				<tr>
					<th>Type</th>
					<th>Hits</th>
					<th>Misses</th>
					<th>Ratio</th>
					<th>Invalidations</th>
					<th>Evictions</th>
					<th>Fetches</th>
					<th>InstanceCount</th>
				</tr>
			</thead>
		<tbody>
	</tbody>
</table>
		<div class="marginTop">
			<c:choose>
				<%-- Query region: SQL-level top objects (needs the server-side Groovy script) --%>
				<c:when test="${region.name == 'queryCacheRegion'}">
					<button class="topObjectsBtn" data-region="${region.name}"
						data-url="topObjects/${clusterID}?regionName=${region.name}"
						data-results-url="<c:url value="/cluster/monitoring/cache/topObjects/results"/>">
						Load top objects
					</button>
					<span id="${region.name}_topObjectsMsg" class="quiet"></span>

					<%-- Totals + view tabs (populated/enabled once data is loaded) --%>
					<div id="${region.name}_topObjectsTotals" class="quiet" style="margin:8px 0 4px;"></div>
					<ul id="${region.name}_topObjectsToggle" class="cache-subtabs topObjectsTabs" style="display:none;">
						<li><a href="#" class="topViewBtn active" data-region="${region.name}" data-view="query">Group by query shape</a></li>
						<li><a href="#" class="topViewBtn" data-region="${region.name}" data-view="table">Group by table</a></li>
					</ul>

					<%-- By query (default): one row per SQL shape --%>
					<div id="${region.name}_topObjectsView_query" class="topObjectsView">
						<table id="${region.name}_topObjects">
							<thead>
								<tr>
									<th title="Number of cached entries sharing this query shape (distinct parameter sets)">Entries</th>
									<th>Query</th>
									<th title="This shape's entries as a share of all cached query entries">% of cache</th>
								</tr>
							</thead>
							<tbody>
							</tbody>
						</table>
					</div>

					<%-- By table: entries rolled up per FROM-clause table --%>
					<div id="${region.name}_topObjectsView_table" class="topObjectsView" style="display:none">
						<table id="${region.name}_topObjectsByTable">
							<thead>
								<tr>
									<th>Table</th>
									<th title="Total cached entries across all query shapes hitting this table">Entries</th>
									<th title="Distinct query shapes hitting this table">Shapes</th>
									<th title="This table's entries as a share of all cached query entries">% of cache</th>
								</tr>
							</thead>
							<tbody>
							</tbody>
						</table>
					</div>
				</c:when>
				<%-- Entity / other regions: "types worth analysing" computed client-side from the
				     per-type stats already on the page (misses + invalidations churn). --%>
				<c:otherwise>
					<h4 style="margin:6px 0;">Types worth analysing</h4>
					<table id="${region.name}_analyseTable" class="region-stats">
						<thead>
							<tr>
								<th>Type</th>
								<th>Hits</th>
								<th title="Cache-miss DB round-trips">Misses</th>
								<th>Hit ratio</th>
								<th title="Cached data made stale by writes">Invalidations</th>
								<th title="Loads performed">Fetches</th>
								<th>Flags</th>
							</tr>
						</thead>
						<tbody>
						</tbody>
					</table>
				</c:otherwise>
			</c:choose>
		</div>

		<%-- Per-region clear (destructive — two-step guard, matches the node-wide clear-all). --%>
		<div class="clear-cache-guard">
			<label class="clear-cache-toggle">
				<input type="checkbox" class="enableClearRegion" data-region="${region.name}"/> Enable clearing this region
			</label>
			<button class="clearRegionBtn" disabled data-region="${region.name}"
				data-url="regionCache/clearRegion/${clusterID}?regionName=${region.name}"
				data-results-url="<c:url value="/cluster/monitoring/cache/topObjects/results"/>">Clear this region</button>
			<span id="${region.name}_clearMsg" class="quiet clear-cache-note">Clears only <strong>${region.name}</strong> on this node. Tick the box to enable.</span>
		</div>
	</div>
</div>
