// Cluster Heap Dump tab. Heap dump is always pod-targeted (it freezes the JVM), so this tab has no
// node-group option: the operator must pick one or more pods. Trigger POSTs the selected node ids
// to /trigger (fire-and-forget on those nodes), then a status round-trip (/dumpinfos +
// /dumpinfos/results) polls every node's current heap-dump state and the resulting Azure blob name.
var selectedNodeIds = [];
var token;
var azureConfigured = false;
var heapStatusIntervalId;

function humanBytes(n) {
    n = Number(n) || 0;
    if (n < 1024) { return n + ' B'; }
    var units = ['KB', 'MB', 'GB', 'TB'], i = -1;
    do { n /= 1024; i++; } while (n >= 1024 && i < units.length - 1);
    return n.toFixed(1) + ' ' + units[i];
}

function statusLabel(s) {
    switch (s) {
        case 'IN_PROGRESS': return '<span style="color:#b8860b">● in progress</span>';
        case 'DONE':        return '<span style="color:green">✔ done</span>';
        case 'FAILED':      return '<span style="color:#c00">✖ failed</span>';
        default:            return '<span class="quiet">idle</span>';
    }
}

function updateTriggerEnabled() {
    $('#trigger-button').prop('disabled', selectedNodeIds.length === 0 || !azureConfigured);
}

$(document).ready(function() {
    token = $("meta[name='_csrf']").attr("content");

    var content = $('#content');

    // Config guard: warn (and keep Trigger disabled) if Azure storage isn't configured.
    $.ajax({
        url: content.attr('data-configStatusUrl'), type: 'GET', dataType: 'json',
        headers: { 'Accept': 'application/json' },
        success: function(data) {
            azureConfigured = !!(data && data.configured);
            $('#azureWarning').toggle(!azureConfigured);
            updateTriggerEnabled();
        },
        error: hac.global.err
    });

    // Pod picker (mandatory selection; node-id targeting).
    var podCfg = {
        nodesUrl: content.attr('data-nodesUrl'),
        nodesResultUrl: content.attr('data-nodesResultUrl'),
        listSelector: '#podList', msgSelector: '#podMsg',
        onChange: function(ids) {
            selectedNodeIds = ids;
            var chip = ids.length ? ('Target: ' + ids.length + ' pod(s)') : '';
            $('#hdScopeChip').text(chip).toggle(ids.length > 0);
            updateTriggerEnabled();
        }
    };
    $('#podLoad').click(function() { PodPicker.load(podCfg); });
    // Auto-load the pod list on open so the picker is ready.
    PodPicker.load(podCfg);

    // Trigger: confirm (heavy, JVM-freezing op), then POST the selected node ids.
    $('#trigger-button').click(function() {
        if (selectedNodeIds.length === 0) { return; }
        var msg = 'Trigger a heap dump on ' + selectedNodeIds.length + ' pod(s)?\n\n'
            + 'The JVM freezes while the dump is written and streamed to Azure. '
            + 'Run only on pods you are actively investigating.';
        if (!window.confirm(msg)) { return; }

        var btn = $(this);
        btn.prop('disabled', true);
        $('#triggerMsg').text('Triggering…');
        $.ajax({
            url: btn.attr('data-url'), type: 'POST', dataType: 'json',
            headers: { 'X-CSRF-TOKEN': token, 'Accept': 'application/json' },
            data: { nodeIds: selectedNodeIds.join(',') },
            success: function(resp) {
                if (resp && resp.ok) {
                    $('#triggerMsg').text('Heap dump triggered on ' + selectedNodeIds.length + ' pod(s). Watch the status table.');
                    refreshHeapDumpInfo();
                } else {
                    $('#triggerMsg').text((resp && resp.error) ? resp.error : 'Trigger failed.');
                }
                updateTriggerEnabled();
            },
            error: function(xhr, s, e) { $('#triggerMsg').text('Trigger failed.'); updateTriggerEnabled(); hac.global.err(xhr, s, e); }
        });
    });

    $('#status-table').dataTable({
        "processing": true,
        "ajax": { "url": "", "dataSrc": function() { return []; } }, // populated by the status round-trip
        "columns": [
            { "data": "nodeID" },
            { "data": "clusterGroup", "render": function(d) { return (d || []).join(', '); } },
            { "data": "status", "render": function(d) { return statusLabel(d); } },
            { "data": "blobName", "render": function(d) { return d ? d : '<span class="quiet">—</span>'; } },
            { "data": "bytesStreamed", "render": function(d) { return humanBytes(d); } },
            { "data": null, "orderable": false, "render": function(row) {
                var parts = [];
                if (row.message) { parts.push(row.message); }
                if (row.startedAt) { parts.push('started ' + row.startedAt); }
                if (row.finishedAt) { parts.push('finished ' + row.finishedAt); }
                return parts.length ? parts.join(' · ') : '<span class="quiet">—</span>';
            } }
        ]
    });

    $('#refresh-button').click(function(e) { e.preventDefault(); refreshHeapDumpInfo(); });

    refreshHeapDumpInfo();
});

// Submit a status request (returns a uuid), then poll the per-node results and render them.
function refreshHeapDumpInfo() {
    var content = $('#content');
    $.ajax({
        url: content.attr('data-dumpinfosUrl'), type: 'GET', dataType: 'json',
        headers: { 'Accept': 'application/json' },
        success: function(data) { pollHeapDumpInfo(data.uuid); },
        error: hac.global.err
    });
}

function pollHeapDumpInfo(uuid) {
    var content = $('#content');
    $.ajax({
        url: content.attr('data-dumpinfosResultsUrl'), type: 'GET', dataType: 'json',
        headers: { 'Accept': 'application/json' }, data: { uuid: uuid },
        success: function(rows) {
            rows = rows || [];
            var dt = $('#status-table').DataTable();
            dt.clear();
            dt.rows.add(rows);
            dt.draw(false);
            $('#respondedInfo').text(rows.length + ' node(s) responded').show();

            // Keep refreshing while any node is still dumping.
            var anyInProgress = rows.some(function(r) { return r.status === 'IN_PROGRESS'; });
            if (heapStatusIntervalId) { clearTimeout(heapStatusIntervalId); heapStatusIntervalId = null; }
            if (anyInProgress) {
                heapStatusIntervalId = setTimeout(refreshHeapDumpInfo, 5000);
            }
        },
        error: hac.global.err
    });
}
