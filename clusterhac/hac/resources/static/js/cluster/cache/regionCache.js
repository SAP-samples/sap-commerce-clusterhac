var tables = {};
var intervalId;
var lastRegions = [];

// --- SAP Commerce domain knowledge for cache diagnosis ---
// System/background types churn constantly by design (Task Engine + CronJob status updates,
// sync timestamps, sessions) — their invalidations are engine NOISE, not a cache problem.
var SYSTEM_TYPES = {
	CronJob:1, CronJobHistory:1, Job:1, ServicelayerJob:1, Task:1, TaskCondition:1,
	ProcessTask:1, BusinessProcess:1, ItemSyncTimestamp:1, SavedValues:1, StoredHttpSession:1,
	ImpExMedia:1, SolrIndexOperation:1, SolrIndexerQuery:1, Trigger:1
};
// Commerce-critical types: high invalidations here during traffic usually means a catalog sync /
// ERP feed / CMS publish is blowing the storefront cache — a real, actionable signal.
var COMMERCE_TYPES = {
	Product:1, VariantProduct:1, ApparelStyleVariantProduct:1, ApparelSizeVariantProduct:1,
	PriceRow:1, StockLevel:1, Category:1, Media:1,
	AbstractCMSComponent:1, ContentSlot:1, ContentPage:1, AbstractPage:1, CMSNavigationNode:1,
	SimpleCMSComponent:1, CMSLinkComponent:1, ProductCarouselComponent:1
};
function isSystemType(name) { return !!SYSTEM_TYPES[name]; }
function isCommerceType(name) { return !!COMMERCE_TYPES[name]; }

$(document).ready(function() {

    $("#accordion").accordion({
		heightStyle: "content",
      active: false,
      collapsible: true
    });

	// Sub-tab switching (Dashboard / Entity / Query / Other)
	$('.cache-subtab').click(function(e) {
		e.preventDefault();
		var pane = $(this).attr('data-pane');
		$('.cache-subtab').removeClass('active');
		$(this).addClass('active');
		$('.cache-pane').removeClass('active');
		$('#pane-' + pane).addClass('active');
		// DataTables/accordion need a redraw when shown from display:none
		if (pane === 'other') {
			try { $("#accordion").accordion("refresh"); } catch (err) {}
		}
		redrawVisibleTables();
	});

	// Clear-cache guard: button is disabled until the user explicitly enables it (two-step).
	$('#enableClearCache').change(function() {
		$('#resetCache').prop('disabled', !this.checked);
	});

	$('#resetCache').click(function(e) {
		if ($('#resetCache').prop('disabled')) { return; }
		if (!window.confirm('Clear the ENTIRE cache on this node (all regions, not just the one shown)? This can temporarily impact performance.')) {
			return;
		}
        var token = $("meta[name='_csrf']").attr("content");
        var url = $('#resetCache').attr('data-url');
		$.ajax({
			url : url,
			dataType: 'json',
			type : 'POST',
			headers : {
				'Accept' : 'application/json',
                'X-CSRF-TOKEN' : token
			},
			success : function(data) {
				updatecache(data,false);
				// Re-arm the guard so the next clear is again a deliberate two-step action.
				$('#enableClearCache').prop('checked', false);
				$('#resetCache').prop('disabled', true);
			},
			error: hac.global.err
		});

	});

	update(true);
	intervalId = setInterval(function() { update(false); }, 20000);

	// Top-objects: on demand per region (reuses the cluster script pipeline server-side)
	$('.topObjectsBtn').click(function(e) {
		var btn = $(this);
		var region = btn.attr('data-region');
		loadTopObjects(region, btn.attr('data-url'), btn.attr('data-results-url'), btn);
	});

	// Top-objects view tabs (query region): By query / By table.
	$('.topViewBtn').click(function(e) {
		e.preventDefault();
		showTopView($(this).attr('data-region'), $(this).attr('data-view'));
	});

	// Per-region clear: two-step guard (enable checkbox arms this region's Clear button).
	$('.enableClearRegion').change(function() {
		var region = $(this).attr('data-region');
		$('.clearRegionBtn[data-region="' + region + '"]').prop('disabled', !this.checked);
	});

	$('.clearRegionBtn').click(function(e) {
		var btn = $(this);
		if (btn.prop('disabled')) { return; }
		var region = btn.attr('data-region');
		if (!window.confirm('Clear the ' + region + ' cache on this node? This can temporarily impact performance.')) {
			return;
		}
		clearRegion(region, btn.attr('data-url'), btn.attr('data-results-url'), btn);
	});

});

// Redraw DataTables in the currently-visible pane (they mis-size if drawn while hidden).
function redrawVisibleTables()
{
	for (var name in tables) {
		try {
			if ($('#' + name + '_types').is(':visible')) {
				tables[name].api().columns.adjust().draw(false);
			}
		} catch (err) {}
	}
}

var topObjectsTables = {};
var topObjectsByTableTables = {};

function loadTopObjects(region, submitUrl, resultsUrl, btn)
{
	var token = $("meta[name='_csrf']").attr("content");
	var msg = $('#' + region + '_topObjectsMsg');
	msg.html(' loading...');
	btn.prop('disabled', true);
	$.ajax({
		url : submitUrl,
		type : 'GET',
		dataType : 'json',
		headers : { 'Accept' : 'application/json', 'X-CSRF-TOKEN' : token },
		success : function(data) {
			if (data.error) {
				msg.html(' ' + data.error);
				btn.prop('disabled', false);
				return;
			}
			pollTopObjects(region, resultsUrl, data.uuid, btn, 0);
		},
		error : function(xhr) {
			msg.html(' request failed');
			btn.prop('disabled', false);
			hac.global.err(xhr);
		}
	});
}

function pollTopObjects(region, resultsUrl, uuid, btn, attempt)
{
	var token = $("meta[name='_csrf']").attr("content");
	var msg = $('#' + region + '_topObjectsMsg');
	$.ajax({
		url : resultsUrl,
		type : 'GET',
		dataType : 'json',
		headers : { 'Accept' : 'application/json', 'X-CSRF-TOKEN' : token },
		data : { uuid : uuid },
		success : function(results) {
			if ((!results || results.length === 0) && attempt < 6) {
				setTimeout(function() { pollTopObjects(region, resultsUrl, uuid, btn, attempt + 1); }, 1000);
				return;
			}
			btn.prop('disabled', false);
			if (!results || results.length === 0) {
				msg.html(' no response yet — try again');
				return;
			}
			renderTopObjects(region, results);
			msg.html('');
		},
		error : function(xhr) {
			btn.prop('disabled', false);
			msg.html(' request failed');
			hac.global.err(xhr);
		}
	});
}

// Per-region clear: submit clear-cache.groovy (scoped to one region) via the cluster script
// pipeline, then poll the shared results endpoint. Mirrors loadTopObjects/pollTopObjects.
function clearRegion(region, submitUrl, resultsUrl, btn)
{
	var token = $("meta[name='_csrf']").attr("content");
	var msg = $('#' + region + '_clearMsg');
	msg.html(' clearing...');
	btn.prop('disabled', true);
	$.ajax({
		url : submitUrl,
		type : 'GET',
		dataType : 'json',
		headers : { 'Accept' : 'application/json', 'X-CSRF-TOKEN' : token },
		success : function(data) {
			if (data.error) {
				msg.html(' ' + data.error);
				return;
			}
			pollClearRegion(region, resultsUrl, data.uuid, btn, 0);
		},
		error : function(xhr) {
			msg.html(' request failed');
			hac.global.err(xhr);
		}
	});
}

function pollClearRegion(region, resultsUrl, uuid, btn, attempt)
{
	var token = $("meta[name='_csrf']").attr("content");
	var msg = $('#' + region + '_clearMsg');
	$.ajax({
		url : resultsUrl,
		type : 'GET',
		dataType : 'json',
		headers : { 'Accept' : 'application/json', 'X-CSRF-TOKEN' : token },
		data : { uuid : uuid },
		success : function(results) {
			if ((!results || results.length === 0) && attempt < 6) {
				setTimeout(function() { pollClearRegion(region, resultsUrl, uuid, btn, attempt + 1); }, 1000);
				return;
			}
			// Re-arm the two-step guard (uncheck enable, re-disable button).
			$('.enableClearRegion[data-region="' + region + '"]').prop('checked', false);
			btn.prop('disabled', true);
			if (!results || results.length === 0) {
				msg.html(' no response yet — the region may still have been cleared; refresh to confirm');
				return;
			}
			msg.html(' cleared');
			// Refresh stats so the region's hits/fill reset in the UI.
			update(false);
		},
		error : function(xhr) {
			$('.enableClearRegion[data-region="' + region + '"]').prop('checked', false);
			btn.prop('disabled', true);
			msg.html(' request failed');
			hac.global.err(xhr);
		}
	});
}

function renderTopObjects(region, results)
{
	// Server returns per-node ScriptEvaluationResult; a single node is targeted, so use the first.
	var output = '';
	for (var i = 0; i < results.length; i++) {
		if (results[i].outputText) { output = results[i].outputText; break; }
	}

	// Query cache: the signature is a SQL query (copy is useful) and we also roll entries up by
	// FROM-clause table. Entity/other: the signature is an item type name; no roll-up/%.
	var isQuery = (region === 'queryCacheRegion');
	var totalLine = '';
	var totalEntries = 0;   // from __TOTAL__ — the true region entry count (for accurate %)
	var distinctBuckets = 0;
	var parsed = [];   // { count, label, table }
	var sumCounts = 0;
	var lines = output.split('\n');
	for (var l = 0; l < lines.length; l++) {
		var line = lines[l];
		if (!line || line.trim() === '') continue;
		var cols = line.split('\t');
		if (cols[0] === '__TOTAL__') {
			totalEntries = parseInt(cols[1], 10) || 0;
			distinctBuckets = parseInt(cols[2], 10) || 0;
			totalLine = cols[1] + ' total entries across ' + cols[2] + ' distinct ' + (isQuery ? 'query shapes' : 'types');
			continue;
		}
		if (cols[0] === '__ERROR__' || cols[0] === '__UNAVAILABLE__') {
			$('#' + region + '_topObjectsMsg').html(' ' + (cols[1] || 'unavailable'));
			continue;
		}
		// script emits: count \t signature \t typeCode \t valueType \t table \t sampleKey
		var count = parseInt(cols[0], 10) || 0;
		parsed.push({ count: count, label: cols[1] || '', table: cols[4] || '' });
		sumCounts += count;
	}

	// % is relative to the TRUE region total (from __TOTAL__) so top-N percentages reflect the
	// whole cache, not just the shown rows. Fall back to the shown sum if __TOTAL__ is missing.
	var denom = totalEntries > 0 ? totalEntries : sumCounts;
	var pct = function(c) { return denom > 0 ? (Math.round(1000 * c / denom) / 10) + '%' : '—'; };

	// --- By query (default view) ---
	var queryRows = parsed.map(function(p) {
		if (isQuery) {
			return [ p.count, getHoverSpan(truncate(p.label, 90), p.label) + copyButton(p.label), pct(p.count) ];
		}
		return [ p.count, p.label ];
	});

	var tableEl = $('#' + region + '_topObjects');
	if (topObjectsTables[region]) {
		topObjectsTables[region].clear();
		topObjectsTables[region].rows.add(queryRows).draw(false);
	} else {
		topObjectsTables[region] = tableEl.DataTable({ data: queryRows, order: [[0, 'desc']],
			pageLength: 20, lengthMenu: [10, 20, 50, 100] });
	}

	// --- Query-only extras: by-table roll-up + tabs + prominent totals ---
	if (isQuery) {
		// Aggregate entries + distinct shapes per table.
		var byTable = {};
		parsed.forEach(function(p) {
			var t = p.table || '(other)';
			if (!byTable[t]) { byTable[t] = { entries: 0, shapes: 0 }; }
			byTable[t].entries += p.count;
			byTable[t].shapes += 1;
		});
		var tableRows = Object.keys(byTable).map(function(t) {
			return [ t, byTable[t].entries, byTable[t].shapes, pct(byTable[t].entries) ];
		});
		var byTableEl = $('#' + region + '_topObjectsByTable');
		if (topObjectsByTableTables[region]) {
			topObjectsByTableTables[region].clear();
			topObjectsByTableTables[region].rows.add(tableRows).draw(false);
		} else {
			topObjectsByTableTables[region] = byTableEl.DataTable({ data: tableRows, order: [[1, 'desc']],
				pageLength: 20, lengthMenu: [10, 20, 50, 100] });
		}

		$('#' + region + '_topObjectsToggle').show();
		if (totalLine) {
			var note = (distinctBuckets > parsed.length)
				? ' <span class="quiet">(showing top ' + parsed.length + ' shapes; roll-up covers these)</span>'
				: '';
			$('#' + region + '_topObjectsTotals').html('<strong>' + totalLine + '</strong>' + note);
		}
		$('#' + region + '_topObjectsMsg').html('');
		// Respect the current toggle state (default: By query).
		showTopView(region, currentTopView[region] || 'query');
	} else if (totalLine) {
		$('#' + region + '_topObjectsMsg').html(' ' + totalLine);
	}
}

// Toggle between the by-query and by-table views for a query region. Each table lives in its own
// explicit container div (_topObjectsView_query / _topObjectsView_table) which we show/hide
// wholesale — that includes the DataTables search box + pagination, so no controls bleed through.
var currentTopView = {};
function showTopView(region, view)
{
	currentTopView[region] = view;
	var byQuery = view !== 'table';
	$('#' + region + '_topObjectsView_query').toggle(byQuery);
	$('#' + region + '_topObjectsView_table').toggle(!byQuery);
	$('.topViewBtn[data-region="' + region + '"]').removeClass('active');
	$('.topViewBtn[data-region="' + region + '"][data-view="' + view + '"]').addClass('active');
	// DataTables mis-size when drawn hidden; redraw the now-visible one.
	if (byQuery && topObjectsTables[region]) { topObjectsTables[region].columns.adjust().draw(false); }
	if (!byQuery && topObjectsByTableTables[region]) { topObjectsByTableTables[region].columns.adjust().draw(false); }
}

function truncate(str, n)
{
	return (str.length > n) ? str.substring(0, n) + '...' : str;
}

function update(init)
{
    var token = $("meta[name='_csrf']").attr("content");
    var url = $('#accordion-data').attr('data-updateDataUrl');
	$.ajax({
		url : url,
		type : 'GET',
		dataType: 'json',
		headers : {
			'Accept' : 'application/json',
            'X-CSRF-TOKEN' : token
		},
		success : function(data) {
            // server returns JSON object: { uuid: "..." } when triggering cluster run
            var scriptUid = data.uuid ? data.uuid : data;
			updatecache(scriptUid,init)

		},
		error: hac.global.err
	});			
}

function updatecache(scriptUid,init)
{
	updatecacheRetry(scriptUid, init, 0);
}

function updatecacheRetry(scriptUid, init, attempt)
{
	var token = $("meta[name='_csrf']").attr("content");
	var url = $('#accordion-data').attr('data-resultDataUrl');
	$.ajax({
		url : url,
		type : 'GET',
        dataType: 'json',
		headers : {
			'Accept' : 'application/json',
			'X-CSRF-TOKEN' : token
		},
		data: {
			uuid: scriptUid,
		},
		success : function(data) {
			// Results may not have arrived yet (server returns {} until the node responds).
			// Keep polling a few times rather than rendering an empty view.
			if ((!data || !data.regions || data.regions.length === 0) && attempt < 6) {
				setTimeout(function() { updatecacheRetry(scriptUid, init, attempt + 1); }, 1000);
				return;
			}
			if (!data || !data.regions) { return; }

			if (init)
				initTables(data.regions);

			updateRegions(data.regions);
			lastRegions = data.regions || [];
			updateDashboard(lastRegions);
			redrawVisibleTables();
		},
		error: hac.global.err
	});
}


function initTables(regions) {

	for (var pos in regions)
	{
		var regionName = regions[pos].name;
		tables[regionName] = $('#' + regions[pos].name + "_types").dataTable({
		});
	}
}

function updateRegions(regions)
{
	var pattern = /^([a-zA-Z0-9]+) \((.+)\)$/;
	for (var pos in regions)
	{
		var region = regions[pos];

		tables[region.name].api().clear();

		$('#' + region.name + '_maxEntries').html(region.maxEntries);
		$('#' + region.name + '_maxReachedSize').html(region.maxReachedSize);

		// Region hit/miss ratio with colour-coded health badge (guards divide-by-zero)
		var regionRatio = null;
		try {
			regionRatio = hitRatio(region.cacheStatistics.hits, region.cacheStatistics.misses);
		} catch (err) {
			regionRatio = safeRatioFromFactor(region.factor);
		}
		$('#' + region.name + '_factor').html(formatRatioBadge(regionRatio));

		// Fill ratio = maxReachedSize / maxEntries
		$('#' + region.name + '_fill').html(formatFillBadge(region.maxReachedSize, region.maxEntries));

		try {
			$('#' + region.name + '_Hits').html(region.cacheStatistics.hits);
			$('#' + region.name + '_Misses').html(region.cacheStatistics.misses);
			$('#' + region.name + '_Invalidations').html(region.cacheStatistics.invalidations);
			$('#' + region.name + '_Evictions').html(region.cacheStatistics.evictions);
			$('#' + region.name + '_Fetches').html(region.cacheStatistics.fetches);
			$('#' + region.name + '_InstanceCount').html(region.cacheStatistics.instanceCount);
		}
		catch(err)
		{
			//
		}

		for (var typePos in region.typesStatistics)
		{
			var type = region.typesStatistics[typePos];
			var ratio = hitRatio(type.hits, type.misses);
			var instanceCount = type.misses - type.evictions - type.invalidations;

			var matches = pattern.exec(type.typeName);

			tables[region.name].api().row.add([(matches != null) ? getHoverSpan(matches[1], matches[2]) : type.type, type.hits, type.misses, formatRatioBadge(ratio), type.invalidations, type.evictions, type.fetches, instanceCount]);
		}
		tables[region.name].api().draw(false);

		// Non-query regions: surface "types worth analysing" from the same per-type stats
		// (client-side, no server call). Query region keeps its SQL top-objects feature.
		if (region.name !== 'queryCacheRegion') {
			renderTypesWorthAnalysing(region);
		}
	}
}

// "Types worth analysing" — the top 10 types of a region, ranked by a NORMALISED COMPOSITE
// score that means "likely worth a look" (not just "busiest"):
//   Two raw signals, each on its own scale, are normalised to 0-1 relative to the set,
//   then blended so neither dominates by raw magnitude:
//     missCost = misses × (1 − hitRatio)   — DB round-trips that caching should have avoided
//     churn    = invalidations             — write-driven staleness
//   score = 100 × (0.6 × missCostNorm + 0.4 × churnNorm)
//     - weighted toward the caching problem (miss-cost), churn still counts.
//     - a low-ratio type stays competitive with a high-invalidation-but-healthy type,
//       instead of raw invalidation counts (thousands) swamping the miss term (small when
//       ratios are high). This was the bug: CronJob (97% ratio) outranked SolrSortField (65%).
//   fetches shown for context but NOT scored (each miss triggers a fetch → double-count).
//   evictions excluded — they reflect region SIZE (see fill ratio), not a per-type problem.
// The score itself is never shown (a made-up number); it only orders the rows. Each row carries
// FLAGS explaining why it's interesting (low hit ratio / high churn). Top 10 only — no paging,
// search or sort (a tuning starting point, not an exhaustive browser). Surfaces candidates;
// the consultant decides.
function renderTypesWorthAnalysing(region)
{
	var tableEl = $('#' + region.name + '_analyseTable');
	if (tableEl.length === 0) { return; }
	var pattern = /^([a-zA-Z0-9]+) \((.+)\)$/;

	// --- Pass 1: gather per-type raw signals and find the set maxima for normalisation. ---
	var entries = [];
	var maxMissCost = 0, maxChurn = 0;
	for (var p in region.typesStatistics) {
		var t = region.typesStatistics[p];
		var hits = t.hits || 0, misses = t.misses || 0, inval = t.invalidations || 0, fetches = t.fetches || 0;
		if (misses + inval + fetches <= 0) { continue; }
		var m0 = pattern.exec(t.typeName);
		var tname = (m0 != null) ? m0[1] : (t.type || t.typeName || '?');
		var ratio = hitRatio(hits, misses);           // 0-100 or null (no traffic)
		var missRate = (ratio === null) ? 0 : (1 - ratio / 100);
		var missCost = misses * missRate;             // raw signal A — impact = miss VOLUME × miss-rate
		// Churn signal is domain-weighted: system-type invalidations are engine noise (down-weight
		// hard); commerce-type invalidations are the real signal (up-weight).
		var churnWeight = isSystemType(tname) ? 0.1 : (isCommerceType(tname) ? 1.5 : 1.0);
		var churn = inval * churnWeight;              // raw signal B
		if (missCost > maxMissCost) { maxMissCost = missCost; }
		if (churn > maxChurn) { maxChurn = churn; }
		entries.push({ t: t, name: tname, hits: hits, misses: misses, inval: inval, fetches: fetches,
			ratio: ratio, missCost: missCost, churn: churn });
	}

	// --- Pass 2: normalise each signal to 0-1 vs the set, blend, and build the rows. ---
	var rows = [];
	entries.forEach(function(e) {
		var missCostNorm = maxMissCost > 0 ? (e.missCost / maxMissCost) : 0;
		var churnNorm    = maxChurn > 0 ? (e.churn / maxChurn) : 0;
		var score = Math.round(100 * (0.6 * missCostNorm + 0.4 * churnNorm));

		var m = pattern.exec(e.t.typeName);
		var full = (m != null) ? m[2] : '';

		// Flags — IMPACT-weighted, not pure percentage. A poor ratio on trivial miss volume is
		// noise; a commerce-type invalidation spike is the real signal.
		var flags = [];
		var traffic = e.hits + e.misses;
		// Low hit ratio only matters at real miss volume (floor 500) — SolrSortField at 96 misses
		// is irrelevant to the DB regardless of %.
		if (e.ratio !== null && e.ratio < 70 && e.misses >= 500) {
			flags.push('<span class="type-flag flag-ratio">low hit ratio</span>');
		}
		// High churn — generic "high churn" flag (don't assert a cause). Commerce types trigger at
		// a lower absolute bar (they matter more); system types are excluded (engine noise). Label
		// stays neutral either way — a spike may be a sync/feed, but we don't claim it.
		if ((isCommerceType(e.name) && e.inval >= 100)
			|| (!isSystemType(e.name) && e.inval > 0 && e.hits > 0 && e.inval >= e.hits * 0.25)) {
			flags.push('<span class="type-flag flag-churn">high churn</span>');
		}

		var nameCell = full ? getHoverSpan(e.name, full) : e.name;
		rows.push({
			score: score,                    // internal — drives default order, never displayed
			cells: [
				nameCell,
				e.hits, e.misses,
				(e.ratio === null ? -1 : e.ratio),   // raw ratio; rendered as a badge, sorted numerically
				e.inval, e.fetches,
				flags.join(' ')
			]
		});
	});

	// Default order = score descending (highest "worth a look" first). Top 10 is plenty for a
	// tuning starting point, so we cap and render plain rows — no DataTable (no paging/search/sort
	// needed here, and its chrome just clutters a 10-row table).
	rows.sort(function(a, b) { return b.score - a.score; });
	var top = rows.slice(0, 10);

	var tbody = tableEl.find('tbody');
	tbody.empty();
	top.forEach(function(r) {
		var c = r.cells;
		var ratioCell = formatRatioBadge(c[3] < 0 ? null : c[3]);
		tbody.append(
			'<tr><td>' + c[0] + '</td><td>' + c[1] + '</td><td>' + c[2] + '</td><td>' + ratioCell
			+ '</td><td>' + c[4] + '</td><td>' + c[5] + '</td><td>' + c[6] + '</td></tr>');
	});
}

// Returns a hit ratio 0-100, or null when there is no traffic (guards NaN on 0 hits + 0 misses).
function hitRatio(hits, misses)
{
	var total = (hits || 0) + (misses || 0);
	if (total === 0) {
		return null;
	}
	return Math.round(100 * hits / total);
}

// Fallback: the server-provided factor field, coerced to a number or null.
function safeRatioFromFactor(factor)
{
	var n = parseFloat(factor);
	return isNaN(n) ? null : Math.round(n);
}

// Maps a 0-100 ratio to a smooth red->amber->green colour (HSL hue 0->120).
// The badge colour *is* the value: a continuous signal, not three flat buckets.
function ratioColor(ratio)
{
	var clamped = Math.max(0, Math.min(100, ratio));
	var hue = Math.round(1.2 * clamped); // 0 => red(0), 50 => amber(60), 100 => green(120)
	return 'hsl(' + hue + ', 65%, 45%)';
}

function formatRatioBadge(ratio)
{
	if (ratio === null) {
		return '<span class="cache-kpi cache-kpi-neutral" title="No cache traffic yet">n/a</span>';
	}
	var color = ratioColor(ratio);
	// Tinted background derived from the same hue for a subtle, readable badge.
	var bg = 'hsl(' + Math.round(1.2 * Math.max(0, Math.min(100, ratio))) + ', 70%, 92%)';
	return '<span class="cache-kpi" style="color:' + color + ';background:' + bg + '">' + ratio + '%</span>';
}

// Fill ratio badge: NEUTRAL, not colour-coded. Unlike hit ratio, fill % has no good/bad
// direction — 100% fill can be perfectly healthy (fully used, no evictions). Whether a full
// region is a concern depends on evictions + hit ratio, which the tuning hint covers.
function formatFillBadge(maxReachedSize, maxEntries)
{
	var reached = parseFloat(maxReachedSize);
	var max = parseFloat(maxEntries);
	if (isNaN(reached) || isNaN(max) || max <= 0) {
		return '<span class="cache-kpi cache-kpi-neutral">n/a</span>';
	}
	var fill = Math.round(100 * reached / max);
	return '<span class="cache-kpi cache-kpi-neutral" title="' + reached + ' / ' + max + '">'
		+ fill + '%</span>';
}

function getHoverSpan(name, fullName)
{
	return '<span title="' + fullName + '">' + name + '</span>';
}

// Renders a small "copy" button carrying the full text (base64-encoded to survive HTML).
function copyButton(text)
{
	if (!text) { return ''; }
	var enc = encodeURIComponent(text);
	return ' <button type="button" class="copy-btn" data-copy="' + enc + '" title="Copy to clipboard">copy</button>';
}

// Delegated handler: copies the query/signature text to the clipboard for further analysis.
$(document).on('click', '.copy-btn', function() {
	var btn = $(this);
	var text = decodeURIComponent(btn.attr('data-copy') || '');
	var done = function() {
		var orig = btn.text();
		btn.addClass('copied').text('copied');
		setTimeout(function() { btn.removeClass('copied').text(orig); }, 1200);
	};
	if (navigator.clipboard && navigator.clipboard.writeText) {
		navigator.clipboard.writeText(text).then(done, function() { fallbackCopy(text, done); });
	} else {
		fallbackCopy(text, done);
	}
});

function fallbackCopy(text, done)
{
	var ta = document.createElement('textarea');
	ta.value = text;
	ta.style.position = 'fixed';
	ta.style.opacity = '0';
	document.body.appendChild(ta);
	ta.select();
	try { document.execCommand('copy'); done(); } catch (e) { /* ignore */ }
	document.body.removeChild(ta);
}

// Populates the Dashboard pane: aggregate hit ratio + totals across all regions, and a
// ranked "regions needing attention" table (lowest hit ratio first, then highest fill).
function updateDashboard(regions)
{
	if (!regions) { return; }
	var totalHits = 0, totalMisses = 0, totalEvictions = 0;
	var rows = [];

	for (var pos in regions) {
		var region = regions[pos];
		var stats = region.cacheStatistics || {};
		var hits = stats.hits || 0;
		var misses = stats.misses || 0;
		var evictions = stats.evictions || 0;
		totalHits += hits;
		totalMisses += misses;
		totalEvictions += evictions;

		var ratio = hitRatio(hits, misses);
		if (ratio === null) { ratio = safeRatioFromFactor(region.factor); }

		var reached = parseFloat(region.maxReachedSize);
		var max = parseFloat(region.maxEntries);
		var fill = (!isNaN(reached) && !isNaN(max) && max > 0) ? Math.round(100 * reached / max) : null;

		rows.push({ name: region.name, ratio: ratio, fill: fill, evictions: evictions,
			hits: hits, misses: misses, hasTraffic: (hits + misses) > 0 });
	}

	$('#dash-regionCount').text(regions.length);
	$('#dash-totalHits').text(totalHits.toLocaleString());
	$('#dash-totalMisses').text(totalMisses.toLocaleString());

	var aggRatio = hitRatio(totalHits, totalMisses);
	if (aggRatio === null) {
		$('#dash-hitRatio').text('n/a');
		$('#dash-hitRatioBar').css({ width: '0%' });
	} else {
		$('#dash-hitRatio').text(aggRatio + '%');
		$('#dash-hitRatioBar').css({ width: aggRatio + '%', background: ratioColor(aggRatio) });
	}

	// --- Hit-ratio-by-region chart (Chart.js) ---
	renderHitChart(rows);

	// --- Regions worth a look: ranked by a NORMALISED COMPOSITE score (same approach as the
	//     per-type table) so the genuinely interesting regions rise to the top. ---
	//   Two raw signals per region, each scaled 0-1 across the set, then blended:
	//     missCost  = misses × (1 − hitRatio)   — DB round-trips caching should have avoided
	//     evictions = raw eviction count        — capacity pressure / churn
	//   score = 100 × (0.6 × missCostNorm + 0.4 × evictionsNorm)
	//   Regions with NO traffic are excluded entirely — a 0% hit ratio on an empty region
	//   (scriptsCacheRegion, backofficeDeletedItemCacheRegion) is not a problem, just unused.
	//   This replaces the old "sort by hit ratio ascending", which floated empty 0% regions to
	//   the top and buried entityCacheRegion (90% but millions of evictions).
	var active = rows.filter(function(r) { return r.hasTraffic && r.ratio !== null; });
	var maxMissCost = 0, maxEvict = 0;
	active.forEach(function(r) {
		r.missCost = r.misses * (1 - r.ratio / 100);
		if (r.missCost > maxMissCost) { maxMissCost = r.missCost; }
		if (r.evictions > maxEvict) { maxEvict = r.evictions; }
	});
	active.forEach(function(r) {
		var mc = maxMissCost > 0 ? (r.missCost / maxMissCost) : 0;
		var ev = maxEvict > 0 ? (r.evictions / maxEvict) : 0;
		r.score = Math.round(100 * (0.6 * mc + 0.4 * ev));
	});
	// Only surface regions with a non-trivial score (something actually stands out).
	active.forEach(function(r) { r.reason = reasonForRegion(r); });
	var flagged = active.filter(function(r) { return r.score > 0 && r.reason; })
		.sort(function(a, b) { return b.score - a.score; });

	// --- Cache findings: the "answers" layer at the top of the Dashboard. Confidence-gated
	//     pointers (state the numbers as fact; give direction, not commands). ---
	renderCacheFindings(active);

	var body = $('#dash-worst');
	body.empty();
	if (flagged.length === 0) {
		var msg = rows.some(function(r) { return r.hasTraffic; })
			? 'No active region stands out on hit ratio, misses or evictions.'
			: 'No cache traffic yet.';
		body.append('<tr><td colspan="5" class="quiet">' + msg + '</td></tr>');
		return;
	}
	for (var i = 0; i < flagged.length; i++) {
		var r = flagged[i];
		var fillCell = (r.fill === null) ? '<span class="cache-kpi cache-kpi-neutral">n/a</span>'
			: formatFillBadgeFromPct(r.fill);
		body.append('<tr><td>' + r.name + '</td><td>' + formatRatioBadge(r.ratio) + '</td><td>'
			+ fillCell + '</td><td>' + r.evictions.toLocaleString() + '</td><td>' + r.reason + '</td></tr>');
	}
}

// The concrete driver behind flagging a region — never list a region without one. Impact-first:
// low hit ratio only counts at real miss volume; evictions when the region is full; else nothing.
function reasonForRegion(r) {
	var reasons = [];
	if (r.ratio !== null && r.ratio < 80 && r.misses >= 1000) {
		reasons.push('Low hit ratio (' + r.ratio + '%, ' + r.misses.toLocaleString() + ' misses)');
	}
	if (r.evictions > 0 && r.fill !== null && r.fill >= 98) {
		reasons.push('High evictions, region full (' + r.evictions.toLocaleString() + ')');
	}
	return reasons.join('; ');
}

// Cache findings — mirrors the thread-dump findings layer. Facts stated; heuristics phrased as
// pointers with direction, never commands (consistent with the tool's confidence-gated tone).
function renderCacheFindings(active) {
	var box = $('#dash-findings');
	if (box.length === 0) { return; }
	var findings = [];

	active.forEach(function(r) {
		// Region distress — poor hit ratio at real miss volume (impact, not just %).
		if (r.ratio !== null && r.ratio < 70 && r.misses >= 1000) {
			var sev = (r.ratio < 50 && r.misses >= 5000) ? 'red' : 'amber';
			findings.push({ sev: sev,
				title: r.name + ' hit ratio ' + r.ratio + '% (' + r.misses.toLocaleString() + ' misses)',
				detail: 'Low ratio at high miss volume — may be worth reviewing.' });
		}
		// Thrashing — full region still evicting.
		if (r.fill !== null && r.fill >= 98 && r.evictions > 0) {
			findings.push({ sev: 'amber',
				title: r.name + ' full, ' + r.evictions.toLocaleString() + ' evictions',
				detail: 'Region may be undersized — potentially worth reviewing regioncache.' + r.name + '.size.' });
		}
	});

	box.empty();
	if (findings.length === 0) {
		box.append('<div class="chac-finding chac-info">'
			+ '<strong>No cache region in distress</strong>'
			+ '<div class="chac-finding-detail">Checked hit ratio, misses and evictions.</div></div>');
		return;
	}
	// Sort red first, then amber; cap noise.
	findings.sort(function(a, b) { return (a.sev === 'red' ? 0 : 1) - (b.sev === 'red' ? 0 : 1); });
	findings.slice(0, 8).forEach(function(f) {
		box.append('<div class="chac-finding chac-' + f.sev + '">'
			+ '<strong>' + f.title + '</strong><div class="chac-finding-detail">' + f.detail + '</div></div>');
	});
}

var dashHitChart = null;

// Bar chart of hit ratio per region (regions with traffic), coloured by the same gradient.
function renderHitChart(rows)
{
	if (typeof Chart === 'undefined') { return; }
	var el = document.getElementById('dash-hitChart');
	if (!el) { return; }

	var withTraffic = rows.filter(function(r) { return r.hasTraffic && r.ratio !== null; })
		.sort(function(a, b) { return a.ratio - b.ratio; });
	var labels = withTraffic.map(function(r) { return r.name; });
	var values = withTraffic.map(function(r) { return r.ratio; });
	var colors = withTraffic.map(function(r) { return ratioColor(r.ratio); });

	if (dashHitChart) {
		dashHitChart.data.labels = labels;
		dashHitChart.data.datasets[0].data = values;
		dashHitChart.data.datasets[0].backgroundColor = colors;
		dashHitChart.update();
		return;
	}
	dashHitChart = new Chart(el.getContext('2d'), {
		type: 'bar',
		data: { labels: labels, datasets: [{ label: 'Hit ratio %', data: values, backgroundColor: colors }] },
		options: {
			indexAxis: 'y',
			responsive: true,
			plugins: { legend: { display: false },
				tooltip: { callbacks: { label: function(c) { return c.parsed.x + '% hit ratio'; } } } },
			scales: { x: { min: 0, max: 100, title: { display: true, text: 'Hit ratio %' } } }
		}
	});
}

// Fill badge from an already-computed percentage (dashboard reuse). Neutral — see formatFillBadge.
function formatFillBadgeFromPct(fill)
{
	return '<span class="cache-kpi cache-kpi-neutral">' + fill + '%</span>';
}
