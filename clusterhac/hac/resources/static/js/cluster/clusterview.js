var nodestable;
  var nodeGroup;
	var cacheregion;
	var scriptUid = "";

	$(document).ready(function() {

		nodestable = $('#clusternodes').dataTable({
			"paging" : false,
			"searching" : false,
			"info" : false
		});

		var token = $("meta[name='_csrf']").attr("content");

		updateClusterNodes();

		$('#nodeGroups').change(function() {
			 nodeGroup = $('#nodeGroups').val();
		});

		$('#cacheregions').change(function() {
			cacheregion = $('#cacheregions').val();
			if(nodeGroup != 'undefined' && cacheregion != 'undefined'
			    && nodeGroup != 'please-select' && cacheregion != 'please-select' ){
				if( $('#resetCache').is(':disabled')){
					document.getElementById("resetCache").disabled = false;
				}
			}
		});

		$('#resetCache').click(function(e) {

			var nodeGroupValues= [];
				nodeGroupValues.push(nodeGroup);

			$.ajax({
				type: "POST",
				url: $('#resetCache').attr('data-url'),
				data: {
					clusterNodeGroups: nodeGroupValues.toString(),
					regionName : cacheregion, 
				},
				dataType: 'json',
				headers: {
					'Accept': 'application/json',
					'X-CSRF-TOKEN': token
				},
				success: function(data) {
					$("#nodeGroups").val("please-select").change();
					$("#cacheregions").val("please-select").change();
					document.getElementById("resetCache").disabled = true;
				},
				error: hac.global.err
			});
		});

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
		debug.log('updating...');
		var url = $('#clusternodes').attr('clusterdata-updateNodesUrl');
		$.ajax({
			url:url,
			type:'GET',
			dataType: 'json',
			headers:{'Accept':'application/json'},
			success: function(data) {
				// data is a JSON object: { uuid: "..." }
				scriptUid = data.uuid;
				updateClusterResult(scriptUid);
				// intervalId = setInterval('updateClusterResult(scriptUid)', 5000);
			},
			error: hac.global.err
		});
	}

	function updateClusterResult(uuid)
	{
		updateClusterResultRetry(uuid, 0);
	}

	function updateClusterResultRetry(uuid, attempt)
	{
		debug.log('updating. result..');
		var url = $('#clusternodes').attr('clusterdata-updateResult');
		$.ajax({
			url:url,
			dataType: 'json',
			headers:{'Accept':'application/json'},
			data: {
				uuid: uuid,
			},
			success: function(data) {
				// The gather runs across nodes async; the first poll is usually empty.
				// Retry a few times before giving up, so the table populates without a
				// manual Refresh (matches the Overview page behaviour).
				if ((!data || data.length === 0) && attempt < 6) {
					setTimeout(function() { updateClusterResultRetry(uuid, attempt + 1); }, 1000);
					return;
				}
				update(data);
			},
			error: hac.global.err
		});
	}

	function updateNodeTable(data)
	{
		nodestable.api().clear();
		for (pos in data)
		{
			var node = data[pos];

			var groups = (node.clusterGroup && node.clusterGroup.length)
				? node.clusterGroup.join(', ') : '';
			var host = node.hostName ? node.hostName : node.clusterGroupAndHostName;
			var hostCell = '<strong>' + host + '</strong>'
				+ (groups ? '<br/><span class="quiet">' + groups + '</span>' : '');

			var heapCell = (typeof node.heapUsedMb !== 'undefined' && node.heapUsedMb >= 0)
				? (node.heapUsedMb + ' / ' + (node.heapMaxMb >= 0 ? node.heapMaxMb : '?') + ' MB')
				: 'n/a';

			var nonHeapCell = (typeof node.nonHeapMb !== 'undefined' && node.nonHeapMb >= 0)
				? (node.nonHeapMb + ' MB') : 'n/a';

			var logsCell = node.kibanaEndpoint
				? '<a href="' + node.kibanaEndpoint + '" target="_blank">Kibana</a>'
				: '<span class="quiet">n/a</span>';

			nodestable.api().row.add([
				node.nodeID,
				hostCell,
				node.clusterIP ? node.clusterIP : 'n/a',
				heapCell,
				nonHeapCell,
				node.threads ? node.threads : 'n/a',
				node.cpu ? node.cpu : 'n/a',
				node.dbPool ? node.dbPool : 'n/a',
				node.uptime ? node.uptime : 'n/a',
				node.gcInfo ? node.gcInfo : 'n/a',
				logsCell,
				'<a href="' + ctx + node.nodeID + '"> Cluster ID : ' + node.nodeID + '</a>'
			]);
		}
		nodestable.api().draw(false);
	}
