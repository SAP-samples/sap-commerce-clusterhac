<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<html>
<head>
<title>Cluster Overview</title>
<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection"/>
<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection"/>
<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
<script type="text/javascript" src="<c:url value="/static/js/cluster/history.js"/>"></script>
<script type="text/javascript" src="<c:url value="/static/js/cluster/overview.js"/>"></script>
</head>
<body>
	<div class="prepend-top span-24 colborder chac chac-auto" id="content"
		data-ctx="${pageContext.request.contextPath}"
		data-nodesUrl="<c:url value="/cluster/overview/nodes"/>"
		data-nodesResultUrl="<c:url value="/cluster/overview/nodes/results"/>"
		data-cacheDataUrl="<c:url value="/cluster/monitoring/cache/regionCache/data/0"/>"
		data-cacheResultUrl="<c:url value="/cluster/monitoring/cache/regionCache/data/results/0"/>">
		<div class="marginLeft marginBottom">
			<h2>Cluster Overview</h2>
			<hr/>

			<%-- Tool tiles: cluster/group-scoped operations only. Single-node tools (Cache,
			     Thread dump, JDBC) are reached per-node from the node cards below. --%>
			<div class="ov-tools">
				<a class="ov-tool" href="<c:url value="/cluster/scripting"/>"><span class="t-name">Cluster Scripting</span><span class="t-desc">Run Groovy across node groups</span></a>
				<a class="ov-tool" href="<c:url value="/cluster/config"/>"><span class="t-name">Cluster Config</span><span class="t-desc">Fetch / update properties per group</span></a>
			</div>

			<%-- Cache health summary (aggregate only; per-region detail lives on the Cache tab) --%>
			<h3>Cache health</h3>
			<div class="cache-dash-tiles">
				<div class="cache-tile"><div class="label">Regions</div><div class="value" id="ov-regionCount">&ndash;</div></div>
				<div class="cache-tile"><div class="label">Aggregate Hit Ratio</div><div class="value" id="ov-hitRatio">&ndash;</div><div class="cache-bar"><span id="ov-hitRatioBar"></span></div></div>
				<div class="cache-tile"><div class="label">Total Hits</div><div class="value" id="ov-totalHits">&ndash;</div></div>
				<div class="cache-tile"><div class="label">Total Misses</div><div class="value" id="ov-totalMisses">&ndash;</div></div>
			</div>

			<%-- Nodes needing attention (threshold breaches; populated by overview.js) --%>
			<h3>Nodes needing attention</h3>
			<div id="ov-attention" class="marginTop"></div>

			<%-- Node / pod health strip --%>
			<h3>Nodes</h3>
			<p class="quiet">Click <strong>Refresh</strong> if the list is incomplete (gather runs across all nodes).</p>
			<button id="ov-refresh">Refresh</button>
			<div id="ov-nodes" class="marginTop node-cards"></div>
		</div>
	</div>
</body>
</html>
