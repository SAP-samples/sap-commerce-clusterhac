
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="hac" uri="/WEB-INF/custom.tld" %>

<c:url var="resultsUrl" value="/cluster/config/results"/>
<html>
<head>
	<title>${pageTitle}</title>
	<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/onoff.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/config/config.css"/>" type="text/css" media="screen, projection" />

	<script type="text/javascript" src="<c:url value="/static/js/cluster/history.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/config/clusterconfig.js"/>"></script>
</head>
	<body>
		<div class="prepend-top span-17 colborder chac chac-auto" id="content" edited-url="<c:url value="/cluster/config/edited/"/>" applyall-url="<c:url value="/cluster/config/applyedited/"/>" resetall-url="<c:url value="/platform/config/resetedited/"/>" valuechanged-url="<c:url value="/platform/config/valuechanged/"/>"
			 updateprops-url="<c:url value="/cluster/config/updateprops/"/>" >
			<div class="marginLeft">
				<div id="tabs-no-sidebar">
					<ul>
						<li><a id="nav-tabs-properties" href="#tabs-properties">Cluster Configuration Properties</a></li>
						<li><a id="nav-tabs-result" href="#tabs-result">Results</a></li>
					</ul>

					<div id="tabs-properties">
						<div class="script-target">
							<label class="script-target-label" for="nodeGroups">Target node groups</label>
							<select id="nodeGroups" multiple>
							<c:forEach items="${nodeGroups}" var="nodeGroup">
								<option value="${nodeGroup}" <c:if test="${nodeGroup eq 'all'}">selected</c:if>>${nodeGroup}</option>
							</c:forEach>
							</select>
							<span id="configScopeChip" class="script-scope-chip" style="display:none"></span>
							<p class="quiet script-usage"><strong>Usage:</strong> change a property key/value across one or more node groups. <strong>Update</strong> sets/adds a property; <strong>Fetch</strong> reads the current value per node.</p>
						</div>
						<div class="script-warning">
							<strong>Runtime &amp; group-wide.</strong> Changes are in-memory only (reset on restart &mdash; persist via <code>local.properties</code>) and apply to <em>every</em> node in the selected group(s). Config is intended to be uniform per group; avoid setting different values on individual pods.
						</div>

						<div >
							<button id="toggleSidebarButton">&gt;</button>
							<div class="marginLeft">

								<div style="margin-top:30px;">

									<br/>
									<hr/>
								</div>
								<div id="configTableWrapper" style="margin-top: 10px;">
									<table id="cluster-props" data-configUpdUrl="<c:url value="/cluster/platform/configstore/"/>" data-configDelUrl="<c:url value="/cluster/configdelete/"/>">
										<thead>
										<tr>
											<th>Key</th>
											<th>Value</th>
										</tr>
										<t>
											<th style="background:#dedede !important;">
												<div style="width:300px">
													<input type="text" class="addInput" id="configKey" placeholder="New key..." style="padding:6px 8px;"/>
												</div>
											</th>
											<th style="background:#dedede !important;border-left-style:solid;border-left-width:1px;border-left-color:#bcbcbc;">
												<div style="width: 100%;">
													<input type="text" id="configValue" placeholder="New value..." style="width:280px;padding:6px 8px;"/>
													<div style="float:right;">
														<button id="addButton" disabled >Update Key/Value</button>

														<button id="fetchButton" disabled >Fetch Key/Value</button>
													</div>
												</div>
											</th>
										</tr>
										</thead>

									</table>
								</div>
							</div>
						</div>


					</div>

					<div id="tabs-result">
						<div id="configDrift" style="display:none"></div>
						<div id="respondedInfo" class="quiet" style="display:none;margin-bottom:6px"></div>
						<table id="result-table" style="width:100%" config-data-url="${resultsUrl}">
							<thead>
								<tr>
									<th>Node ID</th>
									<th>Cluster Details</th>
									<th>Result</th>
								</tr>
							</thead>
						</table>

						<span style="float:inherit">
							<p><strong>Note:</strong> Click <strong>refresh</strong> button if the result has not yet been displayed.
								</span>

						<button id="refresh-button" class="marginTop" style="float:right">Refresh</button>
					</div>



				</div>
			</div>
		</div>

		<div class="span-6 last" id="sidebar">
			<div class="prepend-top" id="recent-reviews">
				<h3 class="caps">
					Page description
				</h3>
				<div class="box">
					<div class="quiet">
						This page provides the list of all currently set hybris properties and their values. You can modify these properties or add a new one.
						<br />
						<hr />
						<hac:note>
							Changes entered on this page are stored only in the memory. That means all values are reloaded from the <span class="filecol">local.properties</span> or <span class="filecol">project.properties</span> file when restarting the application server. To make them persistent, enter changes in <span class="filecol">local.properties</span> or <span class="filecol">project.properties</span> file directly.
						</hac:note>
					</div>
				</div>

				<h3 class="caps">
					Useful Links
				</h3>
				<div class="box">
					<div class="quiet">
						<ul>
							<li> <a href="${wikiProjectProperties}" target="_blank" class="quiet">Configuring the Behavior of SAP Commerce</a> </li>
							<li> <a href="${wikiConfiguration}" target="_blank" class="quiet">Configuration Properties</a> </li>
						</ul>
					</div>
				</div>
			</div>
		</div>

	</body>

</html>

