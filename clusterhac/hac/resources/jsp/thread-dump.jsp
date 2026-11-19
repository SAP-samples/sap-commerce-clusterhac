<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="hac" uri="/WEB-INF/custom.tld" %>
<c:url var="startUrl" value="/cluster/thread-dump/start"/>
<c:url var="stopUrl" value="/cluster/thread-dump/stop"/>
<c:url var="saveMediaUrl" value="/cluster/thread-dump/save-media"/>
<c:url var="cleanUpMediaUrl" value="/cluster/thread-dump/clean-up-media"/>
<c:url var="resultsUrl" value="/cluster/thread-dump/results"/>
<html>
<head>
	<title>${pageTitle}</title>
	<link rel="stylesheet" href="<c:url value="/static/css/table.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/cluster/clusterhac.css"/>" type="text/css" media="screen, projection" />
	<link rel="stylesheet" href="<c:url value="/static/css/console/scripting.css"/>" type="text/css" media="screen, projection" />
    <link rel="stylesheet" href="<c:url value="/static/css/thread-dump.css"/>" type="text/css"
          media="screen, projection"/>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/jquery.dataTables.new.min.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/pod-picker.js"/>"></script>
	<script type="text/javascript" src="<c:url value="/static/js/cluster/thread-dump.js"/>"></script>

</head>
	<body>
		<div class="prepend-top span-24 colborder chac chac-auto" id="content"
			data-nodesUrl="<c:url value="/cluster/monitoring/data"/>"
			data-nodesResultUrl="<c:url value="/cluster/monitoring/results"/>">
			<div class="marginLeft">
                <br/>
                <h2>${pageTitle} <c:if test="${not empty nodeId}"><span class="chac-pod-badge"><c:choose><c:when test="${not empty host}">${host}</c:when><c:otherwise>Node ${nodeId}</c:otherwise></c:choose></span></c:if></h2>
                <hac:note>
                    This page provides the possibility to select thread dumps over a period of time. You can
                    select in which time periods the thread dumps are being collected. When you click stop,
                    the thread dumps will be stored locally as a zip file. Dumps will be stored to Media once
                    you click Save Thread Dump.
                </hac:note>
                <br/>

				<div>
                    <div id="spinnerWrapper">
                        <img id="spinner" src="<c:url value="/static/img/spinner.gif"/>" alt="spinner"/>
                    </div>
                    <div class="td-interval">Interval:
                        <select id="interval">
                            <option value="5000">5 sec</option>
                            <option value="10000">10 sec</option>
                            <option value="30000">30 sec</option>
                            <option value="60000">60 sec</option>
                            <option value="300000">5 min</option>
                            <option value="900000">15 min</option>
                            <option value="1800000">30 min</option>
                        </select>

                	</div>
                    <div class="script-target td-clear">
                        <label class="script-target-label" for="nodeGroups">Target node groups</label>
                        <select id="nodeGroups" multiple>
                            <c:forEach items="${nodeGroups}" var="nodeGroup">
                                <option value="${nodeGroup}">${nodeGroup}</option>
                            </c:forEach>
                        </select>
                        <span id="tdScopeChip" class="script-scope-chip"></span>
                        <div class="script-pods">
                            <label><input type="checkbox" id="podMode"/> Target specific pods instead</label>
                            <button type="button" id="podLoad">Load pods</button>
                            <span id="podMsg" class="quiet"></span>
                            <div id="podList" class="pod-list"></div>
                        </div>
                    </div>

                    <hr>

                    <button id="start-button" class="td-btn-left" data-start-url="${startUrl}" data-stop-url="${stopUrl}" disabled>Start Thread Dump</button>
                    <button id="save-media-button" class="td-btn-left" data-url="${saveMediaUrl}">Save Thread Dump</button>
                    <button id="clean-up-media-button" class="td-btn-right" data-url="${cleanUpMediaUrl}">Clean up thread Dump</button>
                    <hr>
                    <div id="accordion"
                         data-refreshThreadDumpInfoUrl="<c:url value="/cluster/thread-dump/dumpinfos"/>"
                         data-refreshThreadDumpInfoUrlResults="<c:url value="/cluster/thread-dump/dumpinfos/results"/>" >
                    </div>
                    <div class="chac td-analyze-bar">
                        <button id="analyze-selected-button" type="button" disabled>Analyze selected (0)</button>
                        <span class="quiet">Select up to 5 dumps to correlate them (states, stuck threads, trend over time).</span>
                    </div>
                    <table id="result-table" data-url="${resultsUrl}"
                        data-analyze-url="<c:url value="/cluster/thread-dump/analyze"/>">
                        <thead>
                            <tr>
                                <th></th>
                                <th>Creation time</th>
                                <th>URL</th>
                                <th></th>
                                <th></th>
                            </tr>
                        </thead>
                	</table>
                    <button id="refresh-button" class="td-btn-right">Refresh</button>
                    <%-- Analysis summary (populated when a dump's "Analyze" is clicked) --%>
                    <div id="td-analysis" class="chac marginTop"></div>
                </div>
			</div>
		</div>
	</body>
</html>

