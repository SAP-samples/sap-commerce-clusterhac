var editor;
var nodeGroupValues= [];
var configKey = '';
var configValue = '';
$(document).ready(function() {
    var scriptUid = "";
    var token = $("meta[name='_csrf']").attr("content");

    var tabs = $("#tabs-no-sidebar").tabs();

    var xssEntityMap = {
        "<": "&lt;",
        ">": "&gt;",
        "/": '&#x2F;',
        "&": "&amp;",
        '"': '&quot;',
        "'": '&#39;'
    };

    var dataTable = $('#result-table').dataTable({
        "processing": true,
        "paging": false,
        "ajax": {
            "url": $('#result-table').attr('config-data-url'),
            "dataSrc": "",
            "dataType": "json",
            "data": function(data) {
                data.uuid = scriptUid;
            }
        },
        "columns": [
            {"data": "node"},
            {"data": "clusterGroupAndHostName"},
            {
                "data": "outputText",
                "render": function(data, type, row) {
                    return type === 'display' ? sanitizeInput(data) : data;
                }
            }
        ],
        // After each draw, flag config DRIFT — where the fetched value differs across nodes.
        "drawCallback": function() {
            renderDriftBanner(this.api());
        }
    });

    // Compares the per-node result values; shows a drift banner (values differ) or a
    // consistent banner (all match). Client-side over the already-fetched results.
    function renderDriftBanner(api) {
        var banner = $('#configDrift');
        var rows = api.rows().data().toArray();
        if (!rows || rows.length === 0) { banner.hide(); return; }
        var values = {};
        rows.forEach(function(r) {
            var v = (r.outputText === null || r.outputText === undefined) ? '' : String(r.outputText);
            values[v] = (values[v] || 0) + 1;
        });
        var distinct = Object.keys(values);
        if (rows.length < 2) {
            banner.hide();  // single node — nothing to compare
        } else if (distinct.length === 1) {
            banner.attr('class', 'config-consistent')
                .html('<strong>Consistent</strong> — same value on all ' + rows.length + ' responding node(s).')
                .show();
        } else {
            var detail = distinct.map(function(v) {
                return '<li>' + values[v] + '× <code>' + (sanitizeInput(v) || '(empty)') + '</code></li>';
            }).join('');
            banner.attr('class', 'config-drift')
                .html('<strong>Drift detected</strong> — the value differs across nodes ('
                    + distinct.length + ' distinct values on ' + rows.length + ' nodes):<ul>' + detail + '</ul>')
                .show();
        }
    }

    $('#nodeGroups').change(function() {
        // "all" and specific groups are contradictory — normalise to one or the other.
        var vals = $('#nodeGroups').val() || [];
        if (vals.indexOf('all') !== -1 && vals.length > 1) {
            $('#nodeGroups').val(['all']);
            vals = ['all'];
        }
        nodeGroupValues = vals;

        // Scope chip so the target is visible before a group-wide change.
        var scopeText = (vals.length === 0) ? '' :
            (vals.indexOf('all') !== -1 ? 'Target: ALL node groups' : 'Target: ' + vals.join(', '));
        $('#configScopeChip').text(scopeText)
            .toggleClass('scope-all', vals.indexOf('all') !== -1)
            .toggle(vals.length > 0);

        if(configKey !== undefined && configKey.length > 0
            && nodeGroupValues.length > 0  ){
            if( $('#fetchButton').is(':disabled')){
                document.getElementById("fetchButton").disabled = false;
            }
            if(configValue !== undefined && configValue.length > 0){
                if( $('#addButton').is(':disabled')){
                    document.getElementById("addButton").disabled = false;
                }
            }
        }
    });

    // "all" is preselected in the markup; sync the JS state (nodeGroupValues,
    // scope chip, button enablement) by firing the change handler once on load.
    $('#nodeGroups').trigger('change');

   // setInterval(function() { dataTable.api().ajax.reload();}, 60000);

    function sanitizeInput(string) {
        return String(string).replace(/[&<>"'\/]/g, function (s) {
            return xssEntityMap[s];
        });
    }

    function isNotValidConfigKey(string) {
        var regex = new RegExp("\\s+"); // java property pattern, ex.: 'this.is.my.property.key'
        return regex.test(string);
    }

    $("#configKey").change(function(){
         configKey = $("#configKey").val();

        if(configKey !== undefined && configKey.length > 0 && nodeGroupValues.length > 0  ){
            if( $('#fetchButton').is(':disabled')){
                document.getElementById("fetchButton").disabled = false;
            }
        } else{
            document.getElementById("addButton").disabled = true;
            document.getElementById("fetchButton").disabled = true;
        }
    });

    $("#configValue").change(function(){
        configValue = $("#configValue").val();

        if(configValue !== undefined && configValue.length > 0 && nodeGroupValues.length > 0  ){
            if( $('#addButton').is(':disabled')){
                document.getElementById("addButton").disabled = false;
            }
        }
        else{
            document.getElementById("addButton").disabled = true;
        }
    });

    $("#addButton").click(function(event){
        if (isNotValidConfigKey(configKey))
        {
            hac.global.error("Please enter a valid key!");
            $(this).removeAttr('disabled');
            return;
        }
        updateProperties(configKey, configValue , false);
    });

    $("#fetchButton").click(function(event){

        if (isNotValidConfigKey(configKey))
        {
            hac.global.error("Please enter a valid key!");
            $(this).removeAttr('disabled');
            return;
        }
        updateProperties(configKey, configValue , true);
    });

    function updateProperties(configKey, configValue,fetchkey){
        var url = $("#content").attr("updateprops-url");
        var token = $("meta[name='_csrf']").attr("content");

        $('#spinnerWrapper').show();

        var responseData = null;
        $.ajax({
            url : url,
            type : 'POST',
            async: false,
            data : {
                key: configKey,
                val: configValue,
                clusterNodeGroups: nodeGroupValues.toString(),
                fetchkey: fetchkey
            },
            dataType: 'json',
            headers : {
                'Accept' : 'application/json',
                'X-CSRF-TOKEN' : token
            },
            success : function(data) {
                // enableButtons(data.hasEdited);
                responseData = data;
                nodeGroupValues= [];
                configKey = '';
                configValue = '';
                $("#configKey").val("");
                $("#configValue").val("");
                $('#fetchButton').attr('disabled', true);
                $('#addButton').attr('disabled', true);

                // Re-apply the "all" default; handler keeps buttons disabled since
                // the key/value vars were just cleared.
                $('#nodeGroups').val(['all']).trigger('change');
                if (data && data.error) {
                    hac.global.error(data.error === 'validationError' ? "Validation error. Please enter a valid key!" : data.error);
                    $('#spinnerWrapper').hide();
                    return;
                }
                if(fetchkey){
                    hac.global.notify("Key/Value retrieved.");
                }else{
                    hac.global.notify("Key/Value updated.");
                }
                // server returns JSON object: { uuid: "..." }
                scriptUid = data.uuid;
                dataTable.api().clear();
                setTimeout(function() {
                    dataTable.api().ajax.reload(function(json){
                        $('#spinnerWrapper').hide();
                        // Honest completeness: report how many nodes responded. The platform gives
                        // no reliable expected-node count, so we report responders only.
                        var got = (json && json.length) ? json.length : dataTable.api().rows().count();
                        $('#respondedInfo').text(got + ' node(s) responded').show();
                        $("#tabs-no-sidebar").tabs("option", "active", 1);
                    });
                }, 800);
            },
            error : hac.global.err
        });
    }


    $("#refresh-button").click(function(event) {
        dataTable.api().ajax.reload();
    });
});
