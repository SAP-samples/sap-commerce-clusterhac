var threadDumpEnabled, threadDumpUpdateIntervalId;
var nodeGroupValues = [];
var selectedNodeIds = [];
var token;

var REFRESH_INTERVAL_MS = 10000;

function csrfToken() {
    return $("meta[name='_csrf']").attr("content");
}

$(document).ready(function() {
    token = csrfToken();

    refreshClusterThreadDumpInfo();

    // Pod sub-selection (shared PodPicker). When on, targeting is by node id, not group.
    var podCfg = {
        nodesUrl: $('#content').attr('data-nodesUrl'),
        nodesResultUrl: $('#content').attr('data-nodesResultUrl'),
        listSelector: '#podList', msgSelector: '#podMsg',
        onChange: function(ids) {
            selectedNodeIds = ids;
            var chip = ids.length ? ('Target: ' + ids.length + ' pod(s)') : '';
            $('#tdScopeChip').text(chip).toggle(ids.length > 0);
            $('#start-button').prop('disabled', ids.length === 0);
        }
    };
    $('#podMode').change(function() {
        var on = this.checked;
        $('#nodeGroups').prop('disabled', on).toggle(!on);
        $('#podLoad').toggle(on);
        $('#podList').toggle(on);
        selectedNodeIds = [];
        $('#start-button').prop('disabled', true);
        if (on && $('#podList').children().length === 0) { PodPicker.load(podCfg); }
    });
    $('#podLoad').click(function() { PodPicker.load(podCfg); });

    var dataTable = $('#result-table').dataTable({
        "processing": true,
        "ajax": {
            "url": $('#result-table').attr('data-url'),
            "dataSrc": ""
        },
        "columns": [
            {
                "data": "code",
                "orderable": false,
                "render": function(data) {
                    return '<input type="checkbox" class="td-select" data-code="'
                        + encodeURIComponent(data) + '">';
                }
            },
            {
                "data": "creationTime",
                "render": function(data) { return escapeHtml(data); }
            },
            {
                "data": "url",
                "render": function(data) { return escapeHtml(data); }
            },
            {
                "data": "url",
                "render": function(data) {
                    return '<a href="' + escapeHtml(data) + '">Download</a>'
                }
            },
            {
                "data": "code",
                "render": function(data) {
                    return '<a href="#" class="td-analyze-btn" data-code="'
                        + encodeURIComponent(data) + '">Analyze</a>';
                }
            }
        ]
    });

    // Multi-select: enable "Analyze selected (N)" when 1..5 checked; disable at 0 or >5.
    function selectedCodes() {
        var codes = [];
        $('#result-table').find('.td-select:checked').each(function() {
            codes.push(decodeURIComponent($(this).attr('data-code')));
        });
        return codes;
    }
    function refreshSelectionUi() {
        var n = selectedCodes().length;
        var btn = $('#analyze-selected-button');
        btn.text('Analyze selected (' + n + ')');
        btn.prop('disabled', n === 0 || n > 5);
    }
    $('#result-table').on('change', '.td-select', refreshSelectionUi);
    $('#analyze-selected-button').click(function(e) {
        e.preventDefault();
        var codes = selectedCodes();
        if (codes.length >= 1 && codes.length <= 5) { analyzeThreadDumps(codes); }
    });

    // Analyze a saved dump in place (streams the media, parses server-side).
    $('#result-table').on('click', '.td-analyze-btn', function(e) {
        e.preventDefault();
        var code = decodeURIComponent($(this).attr('data-code'));
        analyzeThreadDumps([code]);
    });

    $('#nodeGroups').change(function() {
        nodeGroupValues = $('#nodeGroups').val() || [];
        if (nodeGroupValues.length > 0 && $('#start-button').is(':disabled')) {
            $('#start-button').prop('disabled', false);
        }
    });

    $("#start-button").click(function(event) {
        threadDumpEnabled = !threadDumpEnabled;

        updateThreadDumpUI();
        if (threadDumpEnabled) {
            startThreadDump();
            threadDumpUpdateIntervalId = setInterval(refreshClusterThreadDumpInfo, REFRESH_INTERVAL_MS);
        } else {
            stopThreadDump();
            if ($("#start-button").is(':disabled') && nodeGroupValues.length > 0) {
                $('#start-button').prop('disabled', false);
            } else if (!$("#start-button").is(':disabled') && nodeGroupValues.length === 0) {
                $('#start-button').prop('disabled', true);
            }
        }
    });

    $("#save-media-button").click(function(event) {

        $('#spinnerWrapper').show();
        var saveData = { "interval": $('#interval').val() };
        if ($('#podMode').is(':checked')) { saveData.nodeIds = selectedNodeIds.join(','); }
        else { saveData.clusterNodeGroups = nodeGroupValues.toString(); }
        $.ajax({
            type: "POST",
            url: $('#save-media-button').attr('data-url'),
            data: saveData,
            headers: {
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            success: function(data) {
                // Re-enable Start once the save completed, if a target is still selected. Handle
                // BOTH targeting modes: pod mode uses selectedNodeIds, group mode uses nodeGroupValues.
                var hasTarget = $('#podMode').is(':checked')
                    ? (selectedNodeIds && selectedNodeIds.length > 0)
                    : (nodeGroupValues && nodeGroupValues.length > 0);
                if (hasTarget) { $('#start-button').prop('disabled', false); }
                setTimeout(function() {
                    dataTable.api().ajax.reload(function() {
                        dataTable.api().columns.adjust();
                        $('#spinnerWrapper').hide();
                    });
                }, 2500);
            },
            error: hac.global.err
        });
    });

    $("#clean-up-media-button").click(function(event) {
        $('#spinnerWrapper').show();
        $.ajax({
            type: "POST",
            url: $('#clean-up-media-button').attr('data-url'),
            headers: {
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            success: function(data) {
                setTimeout(function() {
                    dataTable.api().ajax.reload(function() {
                        $('#spinnerWrapper').hide();
                    });
                }, 250);
            },
            error: hac.global.err
        });
    });

    $("#refresh-button").click(function(event) {
        dataTable.api().ajax.reload(function(){
            dataTable.api().columns.adjust();
        });
    });
});

function startThreadDump()
{
    var podMode = $('#podMode').is(':checked');
    var data = { "interval": $('#interval').val() };
    if (podMode) { data.nodeIds = selectedNodeIds.join(','); }
    else { data.clusterNodeGroups = nodeGroupValues.toString(); }

    $.ajax({
        type: "POST",
        url: $('#start-button').attr('data-start-url'),
        data: data,
        headers: {
            'Accept': 'application/json',
            'X-CSRF-TOKEN': token
        },
        success: function(data) {
            setTimeout(function() {
                $('#spinnerWrapper').hide();
            }, 250);
            hac.global.notify("Cluster Thread dump has been triggered successfully.");
        },
        error: hac.global.err
    });
}

function stopThreadDump()
{
    $.ajax({
        type: "POST",
        url: $('#start-button').attr('data-stop-url'),
        headers: {
            'Accept': 'application/json',
            'X-CSRF-TOKEN': token
        },
        success: function(data) {
            setTimeout(function() {
                $('#spinnerWrapper').hide();
            }, 250);
        },
        error: hac.global.err
    });
}

function refreshClusterThreadDumpInfo()
{
    var url = $('#accordion').attr('data-refreshThreadDumpInfoUrl');
    $.ajax({
        url : url,
        type : 'GET',
        dataType: 'json',
        headers : {
            'Accept' : 'application/json',
            'X-CSRF-TOKEN' : csrfToken()
        },
        success : function(data) {
            // server returns JSON object: { uuid: "..." }
            getRefreshClusterThreadDumpInfo(data.uuid);
        },
        error: hac.global.err
    });
}

function getRefreshClusterThreadDumpInfo(scriptUid)
{
    threadDumpEnabled = false;
    var url = $('#accordion').attr('data-refreshThreadDumpInfoUrlResults');
    $.ajax({
        url: url,
        type: 'GET',
        dataType: 'json',
        headers: {
            'Accept': 'application/json',
            'X-CSRF-TOKEN': csrfToken()
        },
        data: {
            uuid: scriptUid
        },
        success: function(data) {
            var threadDumpEnabledCount = 0;

            for (var pos in data) {
                if (!data.hasOwnProperty(pos)) { continue; }
                var cluster = data[pos];
                if (cluster.dumpProcessing) {
                    ++threadDumpEnabledCount;
                    if (nodeGroupValues.length < threadDumpEnabledCount) {
                        nodeGroupValues.push(cluster.clusterGroup);
                        var objSelect = document.getElementById("nodeGroups");
                        cluster.clusterGroup.forEach(function(item) { setSelectedValue(objSelect, item); });
                    }
                }
            }

            threadDumpEnabled = threadDumpEnabledCount > 0;
            if (threadDumpEnabled && $("#start-button").is(':disabled')) {
                $('#start-button').prop('disabled', false);
            }
            updateThreadDumpUI();
            if (threadDumpEnabled && !threadDumpUpdateIntervalId) {
                threadDumpUpdateIntervalId = setInterval(refreshClusterThreadDumpInfo, REFRESH_INTERVAL_MS);
            } else if (!threadDumpEnabled && threadDumpUpdateIntervalId) {
                clearInterval(threadDumpUpdateIntervalId);
                threadDumpUpdateIntervalId = null;
            }
        },
        error: hac.global.err
    });
}

function setSelectedValue(selectObj, valueToSet) {
    for (var i = 0; i < selectObj.options.length; i++) {
        if (selectObj.options[i].text === valueToSet) {
            selectObj.options[i].selected = true;
            return;
        }
    }
}

function updateThreadDumpUI()
{
    $('#start-button').html(threadDumpEnabled ? 'Stop Thread Dump ' + hac.global.getSpinnerImg() : "Start Thread Dump");
}

/* ---------- Thread-dump analysis ---------- */
function analyzeThreadDumps(codes) {
    var out = $('#td-analysis');
    out.html('<p class="quiet">Analyzing…</p>');
    // jQuery serialises an array param as repeated code=... which Spring binds to List<String>.
    $.ajax({
        url: $('#result-table').attr('data-analyze-url'),
        type: 'GET', dataType: 'json', traditional: true,
        headers: { 'Accept': 'application/json', 'X-CSRF-TOKEN': csrfToken() },
        data: { code: codes },
        success: function(a) { renderAnalysis(a); },
        error: function(xhr) { out.html('<p class="quiet">Analysis request failed.</p>'); hac.global.err(xhr); }
    });
}

function renderAnalysis(a) {
    var out = $('#td-analysis');
    if (!a || a.error) {
        out.html('<p class="quiet">' + escapeHtml(a && a.error ? a.error : 'No analysis.') + '</p>');
        return;
    }
    var scope = (a.dumpCount > 1) ? (a.dumpCount + ' dumps') : '1 dump';
    var html = '<h3>Thread-dump analysis</h3>'
        + '<p class="quiet td-note">' + scope + ', ' + a.snapshotCount + ' snapshot(s) pooled, '
        + a.threadCount + ' threads (latest snapshot).</p>';

    // 1. FINDINGS — the answers, up top. Colour signals severity; wording carries confidence
    //    (deadlock asserts as fact; everything else is softened as a possibility).
    html += '<h4>Findings</h4>';
    if (a.findings && a.findings.length) {
        for (var f = 0; f < a.findings.length; f++) {
            var fd = a.findings[f];
            html += '<div class="td-finding td-' + escapeHtml(fd.severity || 'info') + '">'
                + '<strong>' + escapeHtml(fd.title) + '</strong>'
                + '<div class="td-finding-detail">' + escapeHtml(fd.detail) + '</div></div>';
        }
    } else {
        html += '<p class="quiet td-note">No findings.</p>';
    }

    // 2. Deadlock chain (if any) — the raw JVM section, right under the confirmed finding.
    if (a.deadlockFound && a.deadlocks && a.deadlocks.length) {
        html += '<h4>Deadlock detail</h4>';
        for (var dk = 0; dk < a.deadlocks.length; dk++) {
            html += '<pre class="td-deadlock">' + escapeHtml(a.deadlocks[dk].text || '') + '</pre>';
        }
    }

    // 3. Hybris pools — active/total per logical pool (the headline data, not flat aggregates).
    if (a.pools && a.pools.length) {
        html += '<h4>Thread pools (active / total, latest snapshot)</h4>'
            + '<p class="quiet td-note">Active = RUNNABLE. Watch a work pool (HTTP workers, Hybris worker pool) running near its total.</p>'
            + '<table class="cache-worst"><thead><tr><th>Pool</th><th>Active</th><th>Total</th></tr></thead><tbody>';
        for (var p = 0; p < a.pools.length; p++) {
            var pl = a.pools[p];
            html += '<tr><td>' + escapeHtml(pl.pool) + '</td><td>' + pl.active + '</td><td>' + pl.total + '</td></tr>';
        }
        html += '</tbody></table>';
    }

    // 4. Stuck + 5. BLOCKED.
    html += analysisTable('Stuck across all snapshots (possible stalls)', a.stuck,
        ['name', 'state', 'topFrame'], ['Thread', 'State', 'Top frame'],
        'Nothing stuck across snapshots — no obvious stalls.',
        'Application threads in the same frame across every snapshot. Idle infrastructure (pollers, schedulers, JVM/container threads, parked pools) is filtered out.');
    html += analysisTable('BLOCKED / lock contention', a.blocked,
        ['name', 'waitingOn', 'topFrame'], ['Thread', 'Waiting on', 'Top frame'],
        'No BLOCKED threads.');

    // 6. Frame clusters — top-3 frames + resolved Hybris component.
    html += '<h4>Top frame clusters (where threads cluster)</h4>'
        + '<p class="quiet td-note">Threads grouped by their top-3 frames; component is a best-effort label.</p>';
    if (a.topFrames && a.topFrames.length) {
        html += '<table class="cache-worst"><thead><tr><th>Threads</th><th>Component</th><th>Top frames</th></tr></thead><tbody>';
        for (var fc = 0; fc < a.topFrames.length; fc++) {
            var cl = a.topFrames[fc];
            var stack = (cl.frames || []).map(escapeHtml).join('<br><span class="quiet">&nbsp;&nbsp;&larr; </span>');
            html += '<tr><td>' + cl.count + '</td><td><strong>' + escapeHtml(cl.component) + '</strong></td><td>' + stack + '</td></tr>';
        }
        html += '</tbody></table>';
    } else {
        html += '<p class="quiet td-note">No frames.</p>';
    }

    // 7. Top threads by CPU (delta) + window note.
    var cpuNote;
    if (a.cpuMode === 'delta') {
        var win = (a.spanMinutes >= 0)
            ? (a.spanMinutes >= 60 ? (' over a ~' + Math.round(a.spanMinutes / 60) + 'h window') : (' over a ~' + a.spanMinutes + 'min window'))
            : '';
        cpuNote = 'CPU consumed <em>during this recording</em> (delta, first&rarr;last snapshot)' + win + '.'
            + (a.spanMinutes > 15 ? ' Wide window — this is total CPU across the range, not a tight sample; a short recording gives a sharper "hot now" read.' : '');
    } else {
        cpuNote = 'Cumulative CPU <em>since each thread started</em> (single snapshot). Long-lived threads read high even when idle now.';
    }
    html += analysisTable('Top threads by CPU', a.topByCpu,
        ['name', 'state', 'cpu', 'topFrame'], ['Thread', 'State', 'CPU (ms)', 'Top frame'],
        'No CPU data.', cpuNote);

    // 8. Supporting detail: trend + per-snapshot states (demoted to the bottom).
    if (a.trend && a.trend.length > 1) {
        html += '<h4>Trend across dumps (supporting detail)</h4>'
            + '<p class="quiet td-note">One row per dump, oldest first — watch for rising thread or BLOCKED counts.</p>'
            + '<table class="cache-worst"><thead><tr><th>Dump</th><th>Threads</th>'
            + '<th>RUNNABLE</th><th>BLOCKED</th><th>WAITING</th><th>TIMED_WAITING</th></tr></thead><tbody>';
        for (var d = 0; d < a.trend.length; d++) {
            var tr = a.trend[d];
            html += '<tr><td>' + escapeHtml(tr.label) + '</td><td>' + tr.total + '</td><td>' + tr.RUNNABLE
                + '</td><td>' + tr.BLOCKED + '</td><td>' + tr.WAITING + '</td><td>' + tr.TIMED_WAITING + '</td></tr>';
        }
        html += '</tbody></table>';
    }
    html += '<h4>Thread states per snapshot (supporting detail)</h4>'
        + '<table class="cache-worst"><thead><tr><th>Snapshot</th>'
        + '<th>RUNNABLE</th><th>BLOCKED</th><th>WAITING</th><th>TIMED_WAITING</th><th>Total</th></tr></thead><tbody>';
    for (var i = 0; i < a.stateHistogram.length; i++) {
        var c = a.stateHistogram[i].counts || {};
        html += '<tr><td>#' + (i + 1) + '</td><td>' + (c.RUNNABLE || 0) + '</td><td>' + (c.BLOCKED || 0)
            + '</td><td>' + (c.WAITING || 0) + '</td><td>' + (c.TIMED_WAITING || 0) + '</td><td>'
            + a.stateHistogram[i].total + '</td></tr>';
    }
    html += '</tbody></table>';

    out.html(html);
}

function escapeHtml(s) {
    if (s === undefined || s === null) { return ''; }
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function analysisTable(title, rows, keys, headers, emptyMsg, note) {
    var html = '<h4>' + title + '</h4>';
    if (note) { html += '<p class="quiet td-note">' + note + '</p>'; }
    if (!rows || rows.length === 0) { return html + '<p class="quiet td-note">' + emptyMsg + '</p>'; }
    html += '<table class="cache-worst"><thead><tr>';
    for (var h = 0; h < headers.length; h++) { html += '<th>' + headers[h] + '</th>'; }
    html += '</tr></thead><tbody>';
    for (var r = 0; r < rows.length; r++) {
        html += '<tr>';
        for (var k = 0; k < keys.length; k++) {
            var v = rows[r][keys[k]];
            html += '<td>' + (v === undefined || v === null ? '' : escapeHtml(v)) + '</td>';
        }
        html += '</tr>';
    }
    return html + '</tbody></table>';
}