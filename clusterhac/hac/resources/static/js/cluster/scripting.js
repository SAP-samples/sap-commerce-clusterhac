var editor;
var nodeGroupValues = [];
var selectedNodeIds = [];   // pod-level sub-selection (node IDs), populated in pod mode
var lastRunScope = null;    // remembers the last run's target, to explain a zero-result run
$(document).ready(function() {
    var scriptUid = "";
    var token = $("meta[name='_csrf']").attr("content");

    // ---- Pod sub-selection: toggle group-mode <-> specific-pod mode ----
    $('#podMode').change(function() {
        var on = this.checked;
        $('#nodeGroups').prop('disabled', on).toggle(!on);
        $('#podLoad').toggle(on);
        $('#podList').toggle(on);
        selectedNodeIds = [];
        updateExecuteState();
        if (on && $('#podList').children().length === 0) { loadPods(); }
    });
    $('#podLoad').click(function() { loadPods(); });

    // Gather live nodes (reuses the cluster status round-trip) and render hostname checkboxes
    // grouped by node group. Pods are identified by HOSTNAME; the node id is carried for submit.
    function loadPods() {
        var c = $('#content');
        $('#podMsg').text(' loading pods…');
        $.ajax({
            url: c.attr('data-nodesUrl'), type:'GET', dataType:'json',
            headers:{'Accept':'application/json'},
            success: function(data) { pollPods(data.uuid, 0); },
            error: hac.global.err
        });
    }
    function pollPods(uuid, attempt) {
        var c = $('#content');
        $.ajax({
            url: c.attr('data-nodesResultUrl'), type:'GET', dataType:'json',
            headers:{'Accept':'application/json'}, data:{ uuid: uuid },
            success: function(nodes) {
                if ((!nodes || nodes.length === 0) && attempt < 6) {
                    setTimeout(function() { pollPods(uuid, attempt + 1); }, 1000);
                    return;
                }
                renderPods(nodes || []);
            },
            error: hac.global.err
        });
    }
    function renderPods(nodes) {
        $('#podMsg').text('');
        var byGroup = {};
        for (var i in nodes) {
            var n = nodes[i];
            var host = n.hostName || ('node ' + n.nodeID);
            var groups = (n.clusterGroup && n.clusterGroup.length) ? n.clusterGroup : ['(ungrouped)'];
            for (var g in groups) {
                var grp = groups[g];
                (byGroup[grp] = byGroup[grp] || []).push({ id: n.nodeID, host: host });
            }
        }
        var groupNames = Object.keys(byGroup);
        if (groupNames.length === 0) {
            $('#podList').html('<p class="quiet">No pods responded — try Load pods again.</p>');
            return;
        }
        var totalPods = nodes.length;
        // Toolbar: search + count — essential when a cluster has many pods.
        var html = '<div class="pod-toolbar">'
            + '<input type="text" id="podSearch" placeholder="Filter pods / groups…"/>'
            + '<span id="podSelCount" class="quiet">0 of ' + totalPods + ' selected</span>'
            + '</div>';
        for (var gi in groupNames) {
            var grp = groupNames[gi];
            html += '<div class="pod-group" data-group="' + grp.toLowerCase() + '">'
                + '<div class="pod-group-head">'
                + '<span class="pod-group-name">' + grp + '</span>'
                + '<a href="#" class="pod-group-all" data-group="' + grp + '">select all</a>'
                + '</div>';
            byGroup[grp].forEach(function(p) {
                var label = (p.host + ' (node ' + p.id + ')').toLowerCase();
                html += '<label class="pod-item" data-search="' + label + '"><input type="checkbox" class="pod-cb" value="'
                    + p.id + '"/> ' + p.host + ' <span class="quiet">(node ' + p.id + ')</span></label>';
            });
            html += '</div>';
        }
        $('#podList').html(html);

        function refreshSelection() {
            selectedNodeIds = $('.pod-cb:checked').map(function() { return this.value; }).get();
            $('#podSelCount').text(selectedNodeIds.length + ' of ' + totalPods + ' selected');
            var chip = selectedNodeIds.length ? ('Target: ' + selectedNodeIds.length + ' pod(s)') : '';
            $('#scriptScopeChip').text(chip).removeClass('scope-all').toggle(selectedNodeIds.length > 0);
            updateExecuteState();
        }
        $('.pod-cb').change(refreshSelection);

        // Per-group "select all" (toggles the visible/all pods in that group).
        $('.pod-group-all').click(function(e) {
            e.preventDefault();
            var grp = $(this).attr('data-group');
            var boxes = $(this).closest('.pod-group').find('.pod-cb');
            var anyUnchecked = boxes.filter(':not(:checked)').length > 0;
            boxes.prop('checked', anyUnchecked);
            $(this).text(anyUnchecked ? 'clear all' : 'select all');
            refreshSelection();
        });

        // Live filter across pod hostname/node-id and group name.
        $('#podSearch').on('input', function() {
            var q = this.value.toLowerCase().trim();
            $('#podList .pod-item').each(function() {
                $(this).toggle($(this).attr('data-search').indexOf(q) !== -1);
            });
            // Hide a group entirely if none of its pods match (unless the group name matches).
            $('#podList .pod-group').each(function() {
                var groupMatches = $(this).attr('data-group').indexOf(q) !== -1;
                var visibleItems = $(this).find('.pod-item:visible').length;
                $(this).toggle(groupMatches || visibleItems > 0);
                if (groupMatches) { $(this).find('.pod-item').show(); }
            });
        });
    }

    function updateExecuteState() {
        var hasTarget = $('#podMode').is(':checked')
            ? selectedNodeIds.length > 0
            : nodeGroupValues.length > 0;
        document.getElementById("execute-button").disabled = !hasTarget;
    }

    $("#textarea-container").resizable().height("250px").width("99%");
    var tabs = $("#tabs-no-sidebar").tabs();
    editor = CodeMirror.fromTextArea(document.getElementById("script"), {
        mode: "text/x-groovy",
        lineNumbers: true,
        styleActiveLine: true,
        autofocus: true
    });


    $("#commitCheckbox").change(function() {
        if($("#commitCheckbox").is(':checked')){
            document.getElementById( 'executedModeMsg' ).style.display = 'none';
        }
    });

    $('#nodeGroups').change(function() {
        // "all" and specific groups are contradictory — if "all" is picked, clear the rest
        // (and vice-versa), so the target is never ambiguous.
        var vals = $('#nodeGroups').val() || [];
        if (vals.indexOf('all') !== -1 && vals.length > 1) {
            $('#nodeGroups').val(['all']);
            vals = ['all'];
        }
        nodeGroupValues = vals;

        // Show the chosen scope as a chip next to Execute so a cluster-wide run is visible.
        var scopeText = (vals.length === 0) ? 'no target selected'
            : (vals.indexOf('all') !== -1 ? 'ALL node groups' : vals.join(', '));
        $('#scriptScopeChip').text('Target: ' + scopeText)
            .toggleClass('scope-all', vals.indexOf('all') !== -1)
            .toggle(vals.length > 0);

        updateExecuteState();
    });

    var dataTable = $('#result-table').dataTable({
        "processing": true,
        "paging": false,
        "ajax": {
            "url": $('#result-table').attr('data-url'),
            "dataSrc": "",
            "data": function(data) {
                data.uuid = scriptUid;
            }
        },
        "columns": [
            {"data": "node"},
            {"data": "clusterGroupAndHostName"},
            {"data": "executionResult"},
            {"data": "outputText"},
            {"data": "stacktraceText"}
        ]
    });
    setInterval(function() { dataTable.api().ajax.reload();}, 60000);

    $("#execute-button").click(function(event) {

        if(! $("#commitCheckbox").is(':checked')){
            document.getElementById( 'executedModeMsg' ).style.display = 'block';
            return
        }

        var podMode = $('#podMode').is(':checked');
        if (podMode) {
            if (selectedNodeIds.length === 0) { return; }
        } else if (!nodeGroupValues || nodeGroupValues.length === 0) {
            return;
        }

        $('#spinnerWrapper').show();

        var postData = { script: editor.getValue() };
        if (podMode) {
            postData.nodeIds = selectedNodeIds.join(',');
        } else {
            postData.clusterNodeGroups = nodeGroupValues.toString();
        }

        $.ajax({
            type: "POST",
            url: $('#execute-button').attr('data-url'),
            data: postData,
            dataType: 'json',
            headers: {
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            success: function(data) {
                // server returns JSON object: { uuid: "..." }
                scriptUid = data.uuid;
                dataTable.api().clear();
                $('#noMatchHint').hide();
                // Remember what we targeted, to explain a zero-result run.
                lastRunScope = podMode
                    ? { pod: true, count: selectedNodeIds.length }
                    : { pod: false, groups: nodeGroupValues.slice() };
                $("#tabs-no-sidebar").tabs("option", "active", 1);
                // Async gather across nodes — poll a few times so results appear without Refresh.
                reloadResultsWithRetry(dataTable, 0);

                $("#nodeGroups").val("");
                $('.pod-cb').prop('checked', false);
                selectedNodeIds = [];
                $('#scriptScopeChip').hide();
                $('#commitCheckbox').prop('checked', false);
                document.getElementById("execute-button").disabled = true;
            },
            error: hac.global.err
        });
    });

    $("#refresh-button").click(function(event) {
        dataTable.api().ajax.reload();
    });

    function nowTime() {
        var d = new Date();
        function p(n){ return (n < 10 ? '0' : '') + n; }
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }

    // Poll the results table a few times after execute — the cluster gather is async and the
    // first load is usually empty; retry so the user doesn't have to hit Refresh.
    function reloadResultsWithRetry(dataTable, attempt) {
        dataTable.api().ajax.reload(function(json) {
            var got = json && json.length ? json.length : dataTable.api().rows().count();
            if (got === 0 && attempt < 6) {
                setTimeout(function() { reloadResultsWithRetry(dataTable, attempt + 1); }, 1000);
            } else {
                $('#spinnerWrapper').hide();
                // Explain a genuinely empty result: usually no node matched the target groups
                // (e.g. this node belongs to none of them), not a failure.
                if (got === 0 && lastRunScope) {
                    var hint;
                    if (lastRunScope.pod) {
                        hint = 'No results from the ' + lastRunScope.count + ' selected pod(s). '
                            + 'The nodes may not have responded yet — click Refresh.';
                    } else {
                        var grp = (lastRunScope.groups.indexOf('all') !== -1)
                            ? 'the configured node groups' : lastRunScope.groups.join(', ');
                        hint = 'No node matched ' + grp + '. Nodes only run the script if they belong to a '
                            + 'selected group — check the target, or use "Target specific pods" to run on a node directly.';
                    }
                    $('#noMatchHint').text(hint).show();
                    $('#respondedInfo').hide();
                } else {
                    $('#noMatchHint').hide();
                    // Honest completeness: report how many nodes responded (we can't reliably know
                    // the expected total from the platform), plus when.
                    $('#respondedInfo').text(got + ' node(s) responded · updated ' + nowTime()).show();
                }
            }
        }, false);
    }
});
