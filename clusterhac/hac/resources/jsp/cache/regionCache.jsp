<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<html>
<head>
<title>Cache</title>

<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection">
<link rel="stylesheet" href="<c:url value="/static/css/custom-theme/jquery-ui.1.12.1.css"/>" type="text/css" media="screen, projection"/>
<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection"/>

<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
<script type="text/javascript" src="<c:url value="/static/js/chartjs/chart.umd.js"/>"></script>
<script type="text/javascript" src="<c:url value="/static/js/cluster/history.js"/>"></script>
<script type="text/javascript" src="<c:url value="/static/js/cluster/cache/regionCache.js"/>"></script>

</head>
<body>
	<div class="prepend-top span-17 colborder chac chac-auto" id="content">
		<button id="toggleSidebarButton">&gt;</button>
		<div class="marginLeft marginBottom">
			<h2>Cache <c:if test="${not empty clusterID}"><span class="chac-pod-badge"><c:choose><c:when test="${not empty host}">${host}</c:when><c:otherwise>Node ${clusterID}</c:otherwise></c:choose></span></c:if></h2>
			<hr/>
			<br/>

			<ul class="cache-subtabs">
				<li><a href="#" class="cache-subtab active" data-pane="dashboard">Dashboard</a></li>
				<li><a href="#" class="cache-subtab" data-pane="entity">Entity</a></li>
				<li><a href="#" class="cache-subtab" data-pane="query">Query</a></li>
				<%-- Customer-nominated pinned regions (clusterhac.cache.pinnedRegions), between Query and Other --%>
				<c:forEach var="pr" items="${pinnedRegions}">
					<li><a href="#" class="cache-subtab" data-pane="pinned-${pr.name}">${pr.name}</a></li>
				</c:forEach>
				<li><a href="#" class="cache-subtab" data-pane="other">Other regions</a></li>
			</ul>

			<%-- Shared data-URL holder used by regionCache.js for the poll loop --%>
			<div id="accordion-data" data-updateDataUrl="regionCache/data/${clusterID}" data-resultDataUrl="regionCache/data/results/${clusterID}" style="display:none"></div>

			<%-- ===== Dashboard pane ===== --%>
			<div id="pane-dashboard" class="cache-pane active">

				<%-- Cache findings — the answers, up top (populated by regionCache.js) --%>
				<h3>Cache findings</h3>
				<div id="dash-findings" class="chac"></div>

				<%-- Supporting totals. Aggregate hit ratio is a weak headline (high-volume regions
				     mask a bad query cache), so it sits here as context, not the lead. --%>
				<div class="cache-dash-tiles marginTop">
					<div class="cache-tile">
						<div class="label">Regions</div>
						<div class="value" id="dash-regionCount">&ndash;</div>
					</div>
					<div class="cache-tile">
						<div class="label">Aggregate Hit Ratio <span class="quiet" title="Dominated by high-volume regions — can mask a poor query cache. See findings above.">(context)</span></div>
						<div class="value" id="dash-hitRatio">&ndash;</div>
						<div class="cache-bar"><span id="dash-hitRatioBar"></span></div>
					</div>
					<div class="cache-tile">
						<div class="label">Total Hits</div>
						<div class="value" id="dash-totalHits">&ndash;</div>
					</div>
					<div class="cache-tile">
						<div class="label">Total Misses</div>
						<div class="value" id="dash-totalMisses">&ndash;</div>
					</div>
				</div>

				<%-- Hit-ratio across regions (Chart.js — HAC's bundled v4 copy) --%>
				<h3>Hit ratio by region</h3>
				<div style="max-width:900px; margin-bottom:18px;">
					<canvas id="dash-hitChart" height="120"></canvas>
				</div>

				<h3 id="dash-attention-title">Regions worth a look</h3>
				<table class="cache-worst">
					<thead>
						<tr><th>Region</th><th>Hit Ratio</th><th>Fill Ratio</th><th>Evictions</th><th>Reason</th></tr>
					</thead>
					<tbody id="dash-worst"></tbody>
				</table>

				<%-- ===== Clear entire cache (destructive — node-wide, two-step guard) ===== --%>
				<hr class="marginTop"/>
				<div class="clear-cache-guard">
					<label class="clear-cache-toggle">
						<input type="checkbox" id="enableClearCache"/> Enable cache clearing
					</label>
					<button id="resetCache" disabled
						data-url="<c:url value="/cluster/monitoring/cache/regionCache/clear/${clusterID}"/>">Clear entire cache</button>
					<span class="quiet clear-cache-note">Clears the <strong>entire</strong> cache on this node &mdash; all regions, not just one. Can impact performance. Tick the box to enable. (To clear a single region, use its own button on the Entity/Query/Other tabs.)</span>
				</div>
			</div>

			<%-- ===== Entity pane ===== --%>
			<div id="pane-entity" class="cache-pane">
				<c:forEach var="region" items="${entityRegions}">
					<h3><b>${region.name}</b></h3>
					<c:set var="region" value="${region}" scope="request"/>
					<jsp:include page="region-block.jsp"/>
				</c:forEach>
				<c:if test="${empty entityRegions}"><p class="quiet">No entity cache region found.</p></c:if>
			</div>

			<%-- ===== Query pane ===== --%>
			<div id="pane-query" class="cache-pane">
				<c:forEach var="region" items="${queryRegions}">
					<h3><b>${region.name}</b></h3>
					<c:set var="region" value="${region}" scope="request"/>
					<jsp:include page="region-block.jsp"/>
				</c:forEach>
				<c:if test="${empty queryRegions}"><p class="quiet">No query cache region found.</p></c:if>
			</div>

			<%-- ===== Pinned region panes (clusterhac.cache.pinnedRegions) ===== --%>
			<c:forEach var="pr" items="${pinnedRegions}">
				<div id="pane-pinned-${pr.name}" class="cache-pane">
					<h3><b>${pr.name}</b></h3>
					<c:set var="region" value="${pr}" scope="request"/>
					<jsp:include page="region-block.jsp"/>
				</div>
			</c:forEach>

			<%-- ===== Other regions pane (accordion) ===== --%>
			<div id="pane-other" class="cache-pane">
				<div id="accordion">
					<c:forEach var="region" items="${otherRegions}">
						<h3 id="h-${region.name}" style="padding-top:5px;padding-bottom:5px;"><b>&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp ${region.name}</b></h3>
						<c:set var="region" value="${region}" scope="request"/>
						<jsp:include page="region-block.jsp"/>
					</c:forEach>
				</div>
			</div>

		</div>
	</div>

	<div class="span-6 last" id="sidebar">
		<div class="prepend-top" id="recent-reviews">
			<h3 class="caps">Useful Links</h3>
				<div class="box">
				<div class="quiet">
					<a href="${wikiCache}" target="_blank" class="quiet">Cache in Platform</a>
				</div>
			</div>
			<h3 class="caps">Cache health</h3>
			<div class="box">
				<div class="quiet">
					<strong>Hit/Miss</strong> ratio is colour-coded on a red&ndash;amber&ndash;green
					gradient; <strong>Fill</strong> ratio is shown neutrally (a full region is not
					inherently a problem). The <strong>Dashboard</strong> tab summarises cluster
					cache health and ranks regions worth a look. <strong>Entity</strong>
					and <strong>Query</strong> isolate the two caches that most affect performance;
					everything else is under <strong>Other regions</strong>.
					See the SAP Help Portal (search &ldquo;Monitoring Caches&rdquo;) for tuning guidance.
				</div>
			</div>
		</div>
	</div>
	</body>
</html>
