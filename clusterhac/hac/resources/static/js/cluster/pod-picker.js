// Shared pod-picker: gathers the live cluster nodes and renders a filterable, grouped
// hostname checkbox list for pod-level (node-id) targeting. Used by the Scripting and
// Thread Dump tabs. Pods are identified by HOSTNAME; the node id is carried for submit.
//
// Usage:
//   PodPicker.init({
//     nodesUrl, nodesResultUrl,   // the cluster gather endpoints (data + results)
//     listSelector,               // container to render into (e.g. '#podList')
//     onChange: function(nodeIds){...}  // called with the selected node-id array
//   });
//   PodPicker.load(cfg);          // (re)gather + render
//   PodPicker.clear(cfg);         // clear selection + rendered list
var PodPicker = (function($) {

	function load(cfg) {
		var msg = cfg.msgSelector ? $(cfg.msgSelector) : $();
		msg.text(' loading pods…');
		$.ajax({
			url: cfg.nodesUrl, type: 'GET', dataType: 'json',
			headers: { 'Accept': 'application/json' },
			success: function(data) { poll(cfg, data.uuid, 0); },
			error: hac.global.err
		});
	}

	function poll(cfg, uuid, attempt) {
		$.ajax({
			url: cfg.nodesResultUrl, type: 'GET', dataType: 'json',
			headers: { 'Accept': 'application/json' }, data: { uuid: uuid },
			success: function(nodes) {
				if ((!nodes || nodes.length === 0) && attempt < 6) {
					setTimeout(function() { poll(cfg, uuid, attempt + 1); }, 1000);
					return;
				}
				render(cfg, nodes || []);
			},
			error: hac.global.err
		});
	}

	function render(cfg, nodes) {
		if (cfg.msgSelector) { $(cfg.msgSelector).text(''); }
		var list = $(cfg.listSelector);
		var byGroup = {};
		for (var i in nodes) {
			var n = nodes[i];
			var host = n.hostName || ('node ' + n.nodeID);
			var groups = (n.clusterGroup && n.clusterGroup.length) ? n.clusterGroup : ['(ungrouped)'];
			for (var g in groups) {
				(byGroup[groups[g]] = byGroup[groups[g]] || []).push({ id: n.nodeID, host: host });
			}
		}
		var groupNames = Object.keys(byGroup);
		if (groupNames.length === 0) {
			list.html('<p class="quiet">No pods responded — try Load pods again.</p>');
			return;
		}
		var total = nodes.length;
		var html = '<div class="pod-toolbar">'
			+ '<input type="text" class="pod-search" placeholder="Filter pods / groups…"/>'
			+ '<span class="pod-sel-count quiet">0 of ' + total + ' selected</span></div>';
		groupNames.forEach(function(grp) {
			html += '<div class="pod-group" data-group="' + grp.toLowerCase() + '">'
				+ '<div class="pod-group-head"><span class="pod-group-name">' + grp + '</span>'
				+ '<a href="#" class="pod-group-all">select all</a></div>';
			byGroup[grp].forEach(function(p) {
				var label = (p.host + ' (node ' + p.id + ')').toLowerCase();
				html += '<label class="pod-item" data-search="' + label + '"><input type="checkbox" class="pod-cb" value="'
					+ p.id + '"/> ' + p.host + ' <span class="quiet">(node ' + p.id + ')</span></label>';
			});
			html += '</div>';
		});
		list.html(html);

		function refresh() {
			var ids = list.find('.pod-cb:checked').map(function() { return this.value; }).get();
			list.find('.pod-sel-count').text(ids.length + ' of ' + total + ' selected');
			if (typeof cfg.onChange === 'function') { cfg.onChange(ids); }
		}
		list.find('.pod-cb').change(refresh);
		list.find('.pod-group-all').click(function(e) {
			e.preventDefault();
			var boxes = $(this).closest('.pod-group').find('.pod-cb');
			var anyUnchecked = boxes.filter(':not(:checked)').length > 0;
			boxes.prop('checked', anyUnchecked);
			$(this).text(anyUnchecked ? 'clear all' : 'select all');
			refresh();
		});
		list.find('.pod-search').on('input', function() {
			var q = this.value.toLowerCase().trim();
			list.find('.pod-item').each(function() {
				$(this).toggle($(this).attr('data-search').indexOf(q) !== -1);
			});
			list.find('.pod-group').each(function() {
				var groupMatches = $(this).attr('data-group').indexOf(q) !== -1;
				$(this).toggle(groupMatches || $(this).find('.pod-item:visible').length > 0);
				if (groupMatches) { $(this).find('.pod-item').show(); }
			});
		});
	}

	function clear(cfg) {
		$(cfg.listSelector).empty();
		if (typeof cfg.onChange === 'function') { cfg.onChange([]); }
	}

	return { load: load, clear: clear, render: render };
})(jQuery);
