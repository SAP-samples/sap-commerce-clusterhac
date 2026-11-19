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
	<script>
		var clsURL = "${clsURL}";
	</script>
	<script type="text/javascript" src="<c:url value="/static/js/database/clusterDatabase.js?p=4"/>"></script>
</head>
	<body>
			<div class="prepend-top span-17 colborder chac chac-auto" id="content" data-focus-node="${nodeId}">
				<button id="toggleSidebarButton">&gt;</button>
				<div class="marginLeft">
					<h2>JDBC Logging</h2>
					<p class="quiet script-usage"><strong>Usage:</strong> toggle JDBC (SQL) logging, traces and parameters per node. Use the buttons on each node row below to target a specific pod.</p>
					<div class="script-warning">
						<strong>Performance impact.</strong> JDBC logging records every SQL statement and writes to the log &mdash; it adds I/O and slows the node. Enable it only while actively diagnosing, on the specific node(s) you need, and turn it off afterwards.
					</div>

					<c:if test="${empty clsURL}">
						<p class="quiet"><strong>Note:</strong> the "View logs in CLS" link is unavailable because <code>ccv2.services.logging</code> is not set. JDBC logging toggles below work without it.</p>
					</c:if>

					<table id="clusternodes" data-updateNodesUrl="<c:url value="/cluster/database/data"/>"
						   clusterdata-updateNodesUrl="<c:url value="/cluster/database/data"/>"
						   clusterdata-updateResult="<c:url value="/cluster/database/results"/>" >
						<thead>
						<tr>
							<th>Node ID</th>
							<th>Cluster Details</th>
							<th>JDBC Logging</th>
						</tr>
						</thead>
						<tbody>
						</tbody>
					</table>

					<p class="quiet"><strong>Note:</strong> click <strong>Refresh</strong> if the list is incomplete (the gather runs across nodes).</p>
					<button id="refresh-button" class="marginTop" style="float:right">Refresh</button>




					<div id="accordion" toggleLogging="<c:url value="/cluster/database/logs/"/>"
						 toggleTraces="<c:url value="/cluster/database/stacktrace/"/>"
						 toggleParams="<c:url value="/cluster/database/params/"/>"
						 data-refreshLoggingInfoUrl="<c:url value="/cluster/database/loginfos"/>"
						 data-refreshLoggingInfoUrlResults="<c:url value="/cluster/database/loginfos/results"/>" >
					</div>
					<br/>
					<div id="methodWrapper"></div>

					<br/>

				</div>
					
			</div>
			<div class="span-6 last" id="sidebar">
				<div class="prepend-top">
					<h3 class="caps">Page description</h3>
					<div class="box">
						<div class="quiet">
							This page enables you to start logging to the configured log file and download it. If traces are enabled, then a SQL comment is added to each statement with the Java stack trace from where the statement is being called. <br><br>
							<hr />
							<hac:note>
								Do not forget to stop logging when it is not needed, to avoid performance issues. Remember also to clear the log file when it is not needed any more.
							</hac:note>
						</div>
					</div>
					<h3 class="caps">Useful Links</h3>
					<div class="box">
						<ul>
							<li> <a href="${wikiJdbcLogging}" target="_blank" class="quiet" >JDBC Logs from Database Statements</a> </li>
						</ul>
					</div>
				</div>
			</div>		
	</body>
</html>

