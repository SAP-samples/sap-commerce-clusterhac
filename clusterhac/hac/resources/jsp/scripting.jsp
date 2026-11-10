<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<c:url var="executeUrl" value="/cluster/scripting/execute"/>
<c:url var="resultsUrl" value="/cluster/scripting/results"/>
<html>
<head>
	<title>${pageTitle}</title>
	<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/console/scripting.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/webjars/codemirror/lib/codemirror.css"/>" type="text/css"media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/console/codemirror3-custom.css"/>" type="text/css"media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/onoff.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/clusterscripting.css"/>" type="text/css" media="screen, projection" />

	<script type="text/javascript" src="<c:url value="/static/js/history.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/webjars/codemirror/lib/codemirror.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/webjars/codemirror/mode/groovy/groovy.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/webjars/codemirror/addon/selection/active-line.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/scripting.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/console/flexsearch.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/console/historyHandler.js"/>"></script>
</head>
	<body>
		<div class="prepend-top span-17 colborder chac chac-auto" id="content"
			data-nodesUrl="<c:url value="/cluster/monitoring/data"/>"
			data-nodesResultUrl="<c:url value="/cluster/monitoring/results"/>"
			encryptUrl="<c:url value="/console/encrypt" />" decryptUrl="<c:url value="/console/decrypt" />">
			<div class="marginLeft">
				<h2>${pageTitle}</h2>
				<div id="tabs-no-sidebar">
					<ul>
						<li><a id="nav-tabs-edit" href="#tabs-edit">Edit Statement</a></li>
						<li><a id="nav-tabs-result" href="#tabs-result">Results</a></li>
					</ul>

					<div id="tabs-edit">
						<div class="script-target">
							<label class="script-target-label" for="nodeGroups">Target node groups</label>
							<select id="nodeGroups" multiple>
							<c:forEach items="${nodeGroups}" var="nodeGroup">
								<option value="${nodeGroup}">${nodeGroup}</option>
							</c:forEach>
							</select>
							<span id="scriptScopeChip" class="script-scope-chip" style="display:none"></span>
							<p class="quiet script-usage"><strong>Usage:</strong> run a script on one or more node groups &mdash; change log level, invalidate cache, change a property, inspect a bean, read a value per node, etc.</p>

							<div class="script-pods">
								<label><input type="checkbox" id="podMode"/> Target specific pods instead</label>
								<button type="button" id="podLoad" style="display:none">Load pods</button>
								<span id="podMsg" class="quiet"></span>
								<div id="podList" class="pod-list" style="display:none"></div>
							</div>
						</div>
						<div class="script-warning">
							<strong>Warning &mdash; runtime changes only.</strong> Do <strong>not</strong> use
							<code>modelService.save/remove</code> (or other persistence writes) here: the script runs on
							<em>every</em> node in the selected group(s), so writes execute repeatedly and can corrupt data.
						</div>

						<div id="textarea-container">
							<div id="spinnerWrapper">
								<img id="spinner" src="<c:url value="/static/img/spinner.gif"/>" alt="spinner"/>
							</div>
							<textarea id="script" name="script"> println clusterService.getClusterGroups() </textarea>
						</div>


						<div class="script-actionbar">
							<div class="onoffswitch-large" style="float:left;margin-top:7px">
								<input type="checkbox" class="onoffswitch-checkbox" id="commitCheckbox"/>
								<label class="onoffswitch-label" for="commitCheckbox">
									<div class="onoffswitch-inner" _on="EXECUTE" _off="DRYRUN"></div>
									<div class="onoffswitch-switch-large"></div>
								</label>
							</div>
							<span id="modeWarningMsg" class="quiet">EXECUTE runs on the cluster &middot; DRYRUN runs nowhere (switch to prevent accidental execution).</span>
							<button id="execute-button" style="float:right" data-url="${executeUrl}" disabled >Execute</button>
						</div>

						<div style="display:none" id="executedModeMsg">
							<a style="float:left;padding:1em .2em 0;">Currently in <strong>DRYRUN</strong> mode &mdash; nothing was run. Switch to EXECUTE to trigger on the cluster.<hr/>
							</a>
						</div>

						<div style="clear:both"></div>
					</div>

					<div id="tabs-result">
						<div id="noMatchHint" class="script-warning" style="display:none"></div>
						<div id="respondedInfo" class="quiet" style="display:none;margin-bottom:6px"></div>
						<table id="result-table" style="width:100%" data-url="${resultsUrl}">
							<thead>
								<tr>
									<th>Node ID</th>
									<th>Cluster Details</th>
									<th>Result</th>
									<th>Output</th>
									<th>Stacktrace</th>
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
						This page provides the  Cluster Scripting Languages console. It comes with implicit variables:<br><br>
						<ul>
							<li><strong>spring</strong> - Spring ApplicationContext</li>
							<li><strong>out</strong> - Standard Output Stream</li>
						</ul>
						<hr/>
						<strong>Result</strong> tab: Displays results for the executed script. You can return a result using the <strong>return</strong> keyword. If the result is in a <strong>String</strong>, then you can read it directly. For all other objects the <strong>toString()</strong> method is called and the result is displayed.<br><br>
						<strong>Output</strong> tab: Displays a string if the script uses a method to display text. During script execution all output to <strong>System.out</strong> is redirected and shows up in this tab. The implicit variable <strong>out</strong> is available and is of <strong>PrintStream</strong> type.<br><br>
						<strong>Stacktrace</strong> tab: Displays the stack trace for the corrupted script.  <br><br>
						<strong>History</strong> tab: Displays the local script history and the timestamp of the last execution.  <br><br>
					</div>
				</div>
				<h3 class="caps">Useful Links</h3>
				<div class="box">
					<ul>
						<li><a href="${scriptingHelpUrl}" target="_blank" class="quiet">Scripting Engine</a></li>
					</ul>
				</div>
			</div>
		</div>

	</body>

</html>

