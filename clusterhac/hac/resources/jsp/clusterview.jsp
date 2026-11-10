<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="hac" uri="/WEB-INF/custom.tld" %>
<html>
<head>
	<title>Cluster</title>

	<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/clusterview.css"/>" type="text/css"
		  media="screen, projection"/>
	<script>var ctx = "${pageContext.request.contextPath}/cluster/monitoring/cache/"</script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/history.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/clusterview.js"/>"></script>
</head>
	<body>
			<div class="prepend-top span-17 colborder chac chac-auto" id="content">
				<button id="toggleSidebarButton">&gt;</button>
				<div class="marginLeft">
					<h2>Cluster</h2>
						<table id="clusternodes" data-updateNodesUrl="<c:url value="/cluster/monitoring/data"/>"
							   clusterdata-updateNodesUrl="<c:url value="/cluster/monitoring/data"/>"
							   clusterdata-updateResult="<c:url value="/cluster/monitoring/results"/>" >
							<thead>
								<tr>
									<th>Node ID</th>
									<th>Host / Groups</th>
									<th>Cluster IP</th>
									<th>Heap (used / max)</th>
									<th>Non-heap</th>
									<th>Threads (live/peak/daemon)</th>
									<th>CPU</th>
									<th>DB pool</th>
									<th>Uptime</th>
									<th>GC (count / time)</th>
									<th>Logs</th>
									<th>Cluster Cache</th>
								</tr>
							</thead>
							<tbody>
							</tbody>
						</table>
					<span style="float:inherit">
						<p><strong>Note:</strong>Click the <strong>refresh</strong> button if the result has not been displayed or is incomplete yet.
								</span>
					<button id="refresh-button" class="marginTop" style="float:right">Refresh</button>


					<br/>
					<div id="methodWrapper"></div>

					<br/>
					<span style="float:inherit">
							<p><strong>Usage:</strong> Below use-case to clear the cache of cluster environment.
								<br/>
								<strong>Warning:</strong> It is advised to use this functionality due diligence, clearing the cache can impact the performance.</p>
                           </span>

					<div id="cluser-node-groups" >

						<span style="float:left;padding:2em .2em 0;"><strong>Cluster Node Groups :</strong> &nbsp;
						<select id="nodeGroups" >
							<option value="please-select">--Please Select--</option>
							<c:forEach items="${clusterNodeGroups}" var="nodeGroup">
								<option value="${nodeGroup}">${nodeGroup}</option>
							</c:forEach>
						</select>
							</span>

						<span style="float:left;padding:2em 5em 0;"><strong>Cache Regions :</strong> &nbsp;
						<select id="cacheregions" >
							<option value="please-select">--Please Select--</option>
							<option value="all">all</option>
							<c:forEach items="${regions}" var="region">
								<option value="${region.name}">${region.name}</option>
							</c:forEach>
						</select>
							</span>


						<span style="float:left;padding:2em 5em 0;">
						<button id="resetCache" disabled data-url="<c:url value="/cluster/monitoring/data/regionCache/clear"/> ">Clear cache</button>
						</span>
					</div>

				</div>
					
			</div>
			<div class="span-6 last" id="sidebar">
				<div class="prepend-top" id="recent-reviews">
					<h3 class="caps">Page description</h3>
					<div class="box">
						<div class="quiet">
							This page provides a list of available cluster nodes.
							<hr />
							<hac:note>
								Refreshing the list can take up to 30 seconds.
							</hac:note>
						</div>
					</div>
					<h3 class="caps">Useful Links</h3>
					<div class="box">
						<ul>
							<li> <a href="${wikiCluster}" target="_blank" class="quiet" >Clustered Environment</a> </li>
						</ul>
					</div>
				</div>
			</div>		
	</body>
</html>

