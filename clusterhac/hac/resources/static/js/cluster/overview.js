// Cluster Overview landing page. Aggregates existing data: node/pod-health strip (reuses the
// cluster-view event round-trip) + cache health summary (reuses the cache data endpoints).

$(document).ready(function() {
	loadNodes();
	loadCacheHealth();

	$('#ov-refresh').click(function() { loadNodes(); });
});

/* ---------- Node / pod-health strip ---------- */
function loadNodes() {
	var c = $('#content');
	$.ajax({
		url: c.attr('data-nodesUrl'), type:'GET', dataType:'json',
		headers:{'Accept':'application/json'},
		success: function(data) { pollNodes(data.uuid, 0); },
		error: hac.global.err
	});
}

function pollNodes(uuid, attempt) {
	var c = $('#content');
	$.ajax({
		url: c.attr('data-nodesResultUrl'), type:'GET', dataType:'json',
		headers:{'Accept':'application/json'}, data:{ uuid: uuid },
		success: function(data) {
			if ((!data || data.length === 0) && attempt < 6) {
				setTimeout(function() { pollNodes(uuid, attempt + 1); }, 1000);
				return;
			}
			renderNodes(data || []);
		},
		error: hac.global.err
	});
}

function renderNodes(data) {
	renderAttention(data || []);
	var ctx = $('#content').attr('data-ctx') || '';
	var container = $('#ov-nodes');
	container.empty();
	if (!data || data.length === 0) {
		container.append('<p class="quiet">No nodes responded yet — click Refresh.</p>');
		return;
	}
	for (var i in data) {
		var n = data[i];
		var id = n.nodeID;
		var host = n.hostName || n.clusterGroupAndHostName || '?';
		var groups = (n.clusterGroup && n.clusterGroup.length) ? n.clusterGroup.join(', ') : '';
		var heap = (typeof n.heapUsedMb !== 'undefined' && n.heapUsedMb >= 0)
			? (n.heapUsedMb + ' / ' + (n.heapMaxMb >= 0 ? n.heapMaxMb : '?') + ' MB') : 'n/a';
		var nonHeap = (typeof n.nonHeapMb !== 'undefined' && n.nonHeapMb >= 0) ? (n.nonHeapMb + ' MB') : 'n/a';

		var actions =
			'<a class="node-action" href="' + ctx + '/cluster/monitoring/cache/' + id + '?host=' + encodeURIComponent(host) + '">Cache</a>'
			+ '<a class="node-action" href="' + ctx + '/cluster/thread-dump?nodeId=' + id + '&host=' + encodeURIComponent(host) + '">Thread dump</a>'
			+ '<a class="node-action" href="' + ctx + '/cluster/database?nodeId=' + id + '&host=' + encodeURIComponent(host) + '">JDBC</a>';

		var metric = function(label, val) {
			return '<div class="node-metric"><span class="nm-label">' + label + '</span>'
				+ '<span class="nm-val">' + val + '</span></div>';
		};

		var card =
			'<div class="node-card">'
			+   '<div class="node-card-head">'
			+     '<div class="node-id-host"><span class="node-badge">Node ' + id + '</span> '
			+       '<strong>' + host + '</strong>'
			+       (groups ? ' <span class="quiet">[' + groups + ']</span>' : '')
			+     '</div>'
			+     '<div class="node-actions">' + actions + '</div>'
			+   '</div>'
			+   '<div class="node-metrics">'
			+     metric('IP', n.clusterIP || 'n/a')
			+     metric('Heap', heap)
			+     metric('Non-heap', nonHeap)
			+     metric('Threads (live/peak/daemon)', n.threads || 'n/a')
			+     metric('CPU', n.cpu || 'n/a')
			+     metric('DB pool', n.dbPool || 'n/a')
			+     metric('Uptime', n.uptime || 'n/a')
			+     metric('GC (count/time)', n.gcInfo || 'n/a')
			+   '</div>'
			+ '</div>';
		container.append(card);
	}
}

/* ---------- Nodes needing attention ---------- */
// Applies health thresholds to the already-gathered per-node metrics and surfaces breaching
// nodes with the explicit reason(s). An internal score orders the list (worst first) but is
// never shown — the reasons are the message. Thresholds:
//   CPU aggressive (>50%), heap conservative (>90% — usage alone is a weak signal), DB pool
//   near-full (>=90%), last GC pause long (>1000ms). Unknown (-1) signals are skipped.
function evaluateNodeHealth(n) {
	var reasons = [], score = 0;

	if (typeof n.cpuPct === 'number' && n.cpuPct >= 0 && n.cpuPct > 50) {
		reasons.push('CPU ' + n.cpuPct + '%');
		score += n.cpuPct;
	}
	if (typeof n.heapMaxMb === 'number' && n.heapMaxMb > 0 && n.heapUsedMb >= 0) {
		var heapPct = Math.round(100 * n.heapUsedMb / n.heapMaxMb);
		if (heapPct > 90) { reasons.push('Heap ' + heapPct + '%'); score += heapPct; }
	}
	if (typeof n.poolMax === 'number' && n.poolMax > 0 && n.poolActive >= 0) {
		var poolPct = Math.round(100 * n.poolActive / n.poolMax);
		if (poolPct >= 90) { reasons.push('DB pool ' + n.poolActive + '/' + n.poolMax); score += poolPct; }
	}
	if (typeof n.lastGcMs === 'number' && n.lastGcMs > 1000) {
		reasons.push('Last GC ' + n.lastGcMs + 'ms');
		score += Math.round(n.lastGcMs / 20);   // scale into the same rough range as the %s
	}
	return { reasons: reasons, score: score };
}

function renderAttention(data) {
	var container = $('#ov-attention');
	container.empty();

	var flagged = [];
	for (var i in data) {
		var h = evaluateNodeHealth(data[i]);
		if (h.reasons.length > 0) { flagged.push({ n: data[i], reasons: h.reasons, score: h.score }); }
	}
	if (!data || data.length === 0) {
		container.append('<p class="quiet">Waiting for node health…</p>');
		return;
	}
	if (flagged.length === 0) {
		container.append('<p class="quiet">No thresholds breached (CPU, heap, DB pool, GC checked).</p>');
		return;
	}
	flagged.sort(function(a, b) { return b.score - a.score; });

	var rows = '';
	for (var j = 0; j < flagged.length; j++) {
		var f = flagged[j];
		var host = f.n.hostName || f.n.clusterGroupAndHostName || '?';
		var badges = f.reasons.map(function(r) {
			return '<span class="node-attention">' + r + '</span>';
		}).join(' ');
		rows += '<tr><td><span class="node-badge">Node ' + f.n.nodeID + '</span></td>'
			+ '<td>' + host + '</td><td>' + badges + '</td></tr>';
	}
	container.append(
		'<table class="cache-worst"><thead><tr><th>Node</th><th>Host</th><th>Reasons</th></tr></thead>'
		+ '<tbody>' + rows + '</tbody></table>');
}

/* ---------- Cache health summary ---------- */
function loadCacheHealth() {
	var c = $('#content');
	$.ajax({
		url: c.attr('data-cacheDataUrl'), type:'GET', dataType:'json',
		headers:{'Accept':'application/json'},
		success: function(data) {
			var uuid = data.uuid ? data.uuid : data;
			pollCacheHealth(uuid, 0);
		},
		error: hac.global.err
	});
}

function pollCacheHealth(uuid, attempt) {
	var c = $('#content');
	$.ajax({
		url: c.attr('data-cacheResultUrl'), type:'GET', dataType:'json',
		headers:{'Accept':'application/json'}, data:{ uuid: uuid },
		success: function(data) {
			if ((!data || !data.regions || data.regions.length === 0) && attempt < 6) {
				setTimeout(function() { pollCacheHealth(uuid, attempt + 1); }, 1000);
				return;
			}
			if (data && data.regions) { renderCacheHealth(data.regions); }
		},
		error: hac.global.err
	});
}

function renderCacheHealth(regions) {
	var totalHits = 0, totalMisses = 0;
	for (var i in regions) {
		var s = regions[i].cacheStatistics || {};
		totalHits += (s.hits || 0);
		totalMisses += (s.misses || 0);
	}
	$('#ov-regionCount').text(regions.length);
	$('#ov-totalHits').text(totalHits.toLocaleString());
	$('#ov-totalMisses').text(totalMisses.toLocaleString());
	var agg = ovRatio(totalHits, totalMisses);
	if (agg === null) { $('#ov-hitRatio').text('n/a'); $('#ov-hitRatioBar').css('width','0%'); }
	else { $('#ov-hitRatio').text(agg + '%'); $('#ov-hitRatioBar').css({ width: agg + '%', background: ovColor(agg) }); }
}

function ovRatio(hits, misses) {
	var t = (hits||0) + (misses||0);
	return t === 0 ? null : Math.round(100 * hits / t);
}
function ovColor(v) { return 'hsl(' + Math.round(1.2 * Math.max(0, Math.min(100, v))) + ', 65%, 45%)'; }
