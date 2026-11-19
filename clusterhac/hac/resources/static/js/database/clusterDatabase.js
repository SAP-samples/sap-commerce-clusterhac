var clusterLogEnabled, clusterLogUpdateIntervalId;
var kibanaUrl = "View logs in CLS";
var nodestable;
var scriptUid = "";
const traceParamsMap = new Map();

$(document).ready(function() {
	nodestable = $('#clusternodes').dataTable({
		"paging" : false,
		"searching" : false,
		"info" : false
	});
	var token = $("meta[name='_csrf']").attr("content");
	updateClusterNodes();
	$("#refresh-button").click(function(event) {
		updateClusterResult(scriptUid);
	});
});

function update(data)
{
	updateNodeTable(data);
}


function updateClusterNodes()
{
    var url = $('#clusternodes').attr('clusterdata-updateNodesUrl');
    $.ajax({
        url:url,
        type:'GET',
        dataType: 'json',
        headers:{'Accept':'application/json'},
        success: function(data) {
            // server returns JSON object: { uuid: "..." }
            scriptUid = data.uuid;
            updateClusterResult(scriptUid);
        },
        error: hac.global.err
    });

}

function updateClusterResult(uuid)
{
    debug.log('updating. result..');
    var url = $('#clusternodes').attr('clusterdata-updateResult');
    $.ajax({
        url:url,
        type:'GET',
        dataType:'json',
        headers:{'Accept':'application/json'},
        data: {
            uuid: uuid,
        },
        success: function(data) {
            update(data)
        },
        error: hac.global.err
    });
}

function updateNodeTable(data)
{
	nodestable.api().clear();
	var count = 0;
	for (pos in data)
	{
		var node = data[pos];
		var nodeID = node.nodeID;
		if(! traceParamsMap.has("tracesEnabled."+nodeID)){
			traceParamsMap.set("tracesEnabled."+nodeID, false);
		}
		if(! traceParamsMap.has("paramsEnabled."+nodeID)){
			traceParamsMap.set("paramsEnabled."+nodeID, false);
		}
		if(! traceParamsMap.has("logEnabled."+nodeID)){
			traceParamsMap.set("logEnabled."+nodeID, false);
		}
		nodestable.api().row.add([node.nodeID, node.clusterGroupAndHostName ,
			'<button id="toggleLoggingCluster_'+nodeID+'" onclick="toggleLoggingOnclick('+nodeID+')" class="floatLeft" style="float: left"  ></button>'
			+ '<button id="toggleTracesCluster_'+nodeID+'" onclick="toggleTracesOnclick('+nodeID+')" class="floatLeft" style="float: left" ></button>\n'
			+ '<button id="toggleParamsCluster_'+nodeID+'" onclick="toggleParamsOnclick('+nodeID+')" class="floatLeft" style="float: left" ></button>'
			+ (clsURL !== "" ? '<a id="jdbclink" target="_blank" class="btn btn-success" href='+node.kibanaEndpoint+' >' + kibanaUrl+'</a>' : '')
		]);
		++ count;
	}
	nodestable.api().draw(false);
	for (pos in data)
	{
		updateClusterLoggingUI(data[pos].nodeID);
	}

	if(count > 0){
		refreshClusterLoggingInfo()
	}

}
// clean

function getRefreshClusterLoggingInfo(scriptUid)
{
    clusterLogEnabled = false;
    var token = $("meta[name='_csrf']").attr("content");
    var url = $('#accordion').attr('data-refreshLoggingInfoUrlResults');
    $.ajax({
        url:url,
        type:'GET',
        dataType:'json',
        headers:{
            'Accept':'application/json',
            'X-CSRF-TOKEN' : token
        },
        data:{
           uuid: scriptUid,
        },
        success: function(data) {

			var logEnabledCount = 0;
			var tracesEnabledCount = 0;
			for (pos in data) {
				var cluster = data[pos];

				updateLoggingUI(cluster);
				// checking log enable
				if(cluster.logEnabled){
					++ logEnabledCount;
				}
				if(cluster.tracesEnabled){
					++ tracesEnabledCount;
				}
			}
			if(logEnabledCount > 0){
				clusterLogEnabled = true
			}
				if (clusterLogEnabled && !clusterLogUpdateIntervalId){
					clearInterval(clusterLogUpdateIntervalId);
					clusterLogUpdateIntervalId = setInterval("refreshClusterLoggingInfo()", 10000);
				}
				else if(!clusterLogEnabled && clusterLogUpdateIntervalId ){
					clearInterval(clusterLogUpdateIntervalId);
				}
		},
		error: hac.global.err
	});		
}
///
function refreshClusterLoggingInfo()
{
    var token = $("meta[name='_csrf']").attr("content");
    var url = $('#accordion').attr('data-refreshLoggingInfoUrl');
    $.ajax({
        url : url,
        type : 'GET',
        dataType: 'json',
        headers : {
            'Accept' : 'application/json',
            'X-CSRF-TOKEN' : token
        },
        success : function(data) {
            // server returns JSON object: { uuid: "..." }
            var uid = data.uuid;
            getRefreshClusterLoggingInfo(uid)

        },
        error: hac.global.err
    });
}

///

function updateLoggingUI(cluster)
{
	$('#toggleLoggingCluster_'+cluster.nodeID).html(cluster.logEnabled ? 'Stop Logging ' + hac.global.getSpinnerImg() : "Start logging");
	$('#toggleTracesCluster_'+cluster.nodeID).html(cluster.tracesEnabled ? "Disable Traces" : "Enable traces");
	$('#toggleParamsCluster_'+cluster.nodeID).html( cluster.paramsEnabled ? "Disable Params" : "Enable Params");
}

function toggleLoggingOnclick(nodeID)
{
	var token = $("meta[name='_csrf']").attr("content");
	var url = $('#accordion').attr('toggleLogging') ;
	$.ajax({
		url:url,
		type:'POST',
		data: {
			nodeID:nodeID,
			logEnabled: !traceParamsMap.get("logEnabled."+nodeID),
			tracesEnabled: traceParamsMap.get("tracesEnabled."+nodeID),
			paramsEnabled: traceParamsMap.get("paramsEnabled."+nodeID)
		},
		headers:{
			'Accept':'application/json',
			'X-CSRF-TOKEN' : token
		},
		success: function(data) {
			debug.log(data);

			if(traceParamsMap.has("logEnabled."+nodeID)){
				traceParamsMap.set("logEnabled."+nodeID, !traceParamsMap.get("logEnabled."+nodeID));
			}
			updateClusterLoggingUI(nodeID);
			if (traceParamsMap.get("logEnabled."+nodeID)){
				clearInterval(clusterLogUpdateIntervalId);
				clusterLogUpdateIntervalId = setInterval("refreshClusterLoggingInfo()", 10000);
			}
		},
		error: hac.global.err
	});

}

function toggleTracesOnclick(nodeID)
{
	if(traceParamsMap.has("tracesEnabled."+nodeID)){
		traceParamsMap.set("tracesEnabled."+nodeID, !traceParamsMap.get("tracesEnabled."+nodeID));
	}
	updateClusterLoggingUI(nodeID);
}

function toggleParamsOnclick(nodeID)
{
	if(traceParamsMap.has("paramsEnabled."+nodeID)){
		traceParamsMap.set("paramsEnabled."+nodeID, !traceParamsMap.get("paramsEnabled."+nodeID));
	}
	updateClusterLoggingUI(nodeID);
}

function updateClusterLoggingUI(nodeID)
{
	$('#toggleLoggingCluster_'+nodeID).html(traceParamsMap.get("logEnabled."+nodeID) ? 'Stop Logging ' + hac.global.getSpinnerImg() : "Start logging");
	$('#toggleTracesCluster_'+nodeID).html(traceParamsMap.get("tracesEnabled."+nodeID)  ? "Disable Traces" : "Enable traces");
	$('#toggleParamsCluster_'+nodeID).html(traceParamsMap.get("paramsEnabled."+nodeID)  ? "Disable Params" : "Enable Params");
}
