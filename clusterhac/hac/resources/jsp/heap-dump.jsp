<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="hac" uri="/WEB-INF/custom.tld" %>
<c:url var="triggerUrl" value="/cluster/heap-dump/trigger"/>
<c:url var="configStatusUrl" value="/cluster/heap-dump/config-status"/>
<html>
<head>
	<title>${pageTitle}</title>
	<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/console/scripting.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/thread-dump.css"/>" type="text/css" media="screen, projection"/>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/pod-picker.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/heap-dump.js"/>"></script>
</head>
	<body>
		<div class="prepend-top span-24 colborder chac chac-auto" id="content"
			data-nodesUrl="<c:url value="/cluster/monitoring/data"/>"
			data-nodesResultUrl="<c:url value="/cluster/monitoring/results"/>"
			data-triggerUrl="${triggerUrl}"
			data-configStatusUrl="${configStatusUrl}"
			data-dumpinfosUrl="<c:url value="/cluster/heap-dump/dumpinfos"/>"
			data-dumpinfosResultsUrl="<c:url value="/cluster/heap-dump/dumpinfos/results"/>"
			<c:if test="${not empty nodeId}">data-preselect-node="${nodeId}"</c:if>>
			<div class="marginLeft">
				<br/>
				<h2>${pageTitle} <c:if test="${not empty nodeId}"><span class="chac-pod-badge"><c:choose><c:when test="${not empty host}">${host}</c:when><c:otherwise>Node ${nodeId}</c:otherwise></c:choose></span></c:if></h2>
				<hac:note>
					Captures a JVM heap dump from the selected pod(s) and streams it to Azure Blob Storage &mdash; the dump
					never lands on the pod's local disk, so it survives the liveness probe. A heap dump freezes the JVM
					while it is written, so this is <strong>always targeted at specific pods</strong>, never a node group.
					The resulting blob name appears in the status table below once the dump completes.
				</hac:note>
				<br/>

				<div id="azureWarning" class="script-warning" style="display:none">
					<strong>Azure storage is not configured.</strong> Set
					<code>clusterhac.heapdump.azure.connection-string</code> (and optionally
					<code>…container</code>) in <code>local.properties</code> before triggering a heap dump.
				</div>

				<div class="script-target">
					<label class="script-target-label">Target pods <span class="quiet">(required)</span></label>
					<button type="button" id="podLoad">Load pods</button>
					<span id="podMsg" class="quiet"></span>
					<span id="hdScopeChip" class="script-scope-chip" style="display:none"></span>
					<div id="podList" class="pod-list"></div>
				</div>

				<hr>

				<button id="trigger-button" style="float:left" data-url="${triggerUrl}" disabled>Trigger heap dump</button>
				<span id="triggerMsg" class="quiet" style="float:left;margin:7px 0 0 10px"></span>
				<button id="refresh-button" style="float:right">Refresh status</button>
				<hr style="clear:both">

				<div id="respondedInfo" class="quiet" style="display:none;margin-bottom:6px"></div>
				<table id="status-table" style="width:100%"
					data-url="<c:url value="/cluster/heap-dump/dumpinfos/results"/>">
					<thead>
						<tr>
							<th>Node ID</th>
							<th>Cluster groups</th>
							<th>Status</th>
							<th>Blob</th>
							<th>Bytes</th>
							<th>Details</th>
						</tr>
					</thead>
				</table>
			</div>
		</div>
	</body>
</html>
