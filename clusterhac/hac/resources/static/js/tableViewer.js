'use strict';
(function () {

    function setupTableViewers() {
        let tableViewers = {};
        let toolbarWidgets = {};

        initView();

        function initView() {
            initTabsIfExist();
            initToolbarWidgets();
            populateAllTables();
        }

        function initTabsIfExist() {
            var tabsNode = $("#tabs")
            if (tabsNode.length) {
                tabsNode.tabs({});
            }
        }

        function initToolbarWidgets() {
            toolbarWidgets.dropdown = initDropDownWidget();
            toolbarWidgets.dateRangePicker = initDateRangePickerWidget();
        }

        function initDropDownWidget() {
            return function (root, id, name, values) {
                var label = $(`<label>${name}&nbsp;</label>`);
                var combo = $("<select></select>").attr("id", id).attr("name", name);
                $.each(values, function (i, el) {
                    combo.append("<option>" + el + "</option>");
                });
                label.append(combo);
                root.append(label);
                combo.change(function () {
                    var params = {};
                    params[id] = this.value;
                    populateTable($(root).parent().find('table.tableViewer').get(0), params);
                });
            }
        }

        function initDateRangePickerWidget() {
            function createDateRangePicker(id, name, values) {
                var label = $(`<label>${name}&nbsp;</label>`).css("margin-right", "10px");
                var picker = $("<input></input>").attr("type", "text").attr("id", `${id}`).attr("name", name);
                label.append(picker);
                return label;
            }

            return function (root, id, name, values) {
                var minHourDiff = 2;
                var idFrom = id + "From";
                var idTo = id + "To";
                var fromPicker = createDateRangePicker(idFrom, "from", values);
                var toPicker = createDateRangePicker(idTo, "to", values);
                root.append(fromPicker);
                root.append(toPicker);
                var applyButton = $("<button>Apply</button>").css("display", "inline");
                root.append(applyButton);

                var toDate = new Date()
                var fromDate = new Date(toDate.getTime());
                fromDate.setHours(fromDate.getHours() - minHourDiff);

                var pickerConfigFrom = {
                    maxDate: fromDate,
                    maxDateTime: fromDate,
                    minDate: "-30d",
                };

                var pickerConfigTo = {
                    maxDate: toDate,
                    maxDateTime: toDate,
                    minDate: "-30d",
                    onClose: function (selectedDate) {
                        var newMaxDate = new Date($(this).datepicker('getDate').getTime());
                        newMaxDate.setHours(newMaxDate.getHours() - minHourDiff);
                        var fromPicker = $("#" + idFrom);
                        fromPicker.datetimepicker("option", "maxDate", newMaxDate);
                        fromPicker.datetimepicker("option", "maxDateTime", newMaxDate);
                        var currentFromDate = $("#" + idFrom).datepicker('getDate');
                        if (currentFromDate >= newMaxDate) {
                            fromPicker.datetimepicker('setDate', newMaxDate);
                        }
                    }
                };

                $("#" + idFrom).datetimepicker(pickerConfigFrom);
                $("#" + idTo).datetimepicker(pickerConfigTo);

                $("#" + idFrom).datepicker('setDate', fromDate);
                $("#" + idTo).datepicker('setDate', toDate);

                applyButton.click(function () {
                    var params = {};
                    params[idFrom] = $("#" + idFrom).datepicker('getDate').toISOString();
                    params[idTo] = $("#" + idTo).datepicker('getDate').toISOString();
                    populateTable($(root).parent().find('table.tableViewer').get(0), params);
                });
            }
        }

        function populateAllTables() {
            $('table.tableViewer').each(function (index) {
                populateTable(this);
            });
        }

        function populateTable(root, data) {
            var token = $("meta[name='_csrf']").attr("content");
            var url = root.dataset.url;
            $.ajax({
                url: url,
                type: 'GET',
                data: data,
                headers: {
                    'Accept': 'application/json',
                    'X-CSRF-TOKEN': token
                },
                success: function (data) {
                    if (data.exception) {
                        tableError(data.exception);
                    } else {
                        var tableViewer = getTableViewer(root);
                        if (!tableViewer) {
                            tableViewer = createTableViewer(root, data);
                        }
                        tableViewer.fnClearTable();
                        if (notEmpty(data.rows)) {
                            tableViewer.fnAddData(data.rows);
                        }
                    }

                },
                error: function (xht, textStatus, ex) {
                    tableError(ex);
                }
            });
        }

        function getTableViewer(root) {
            return tableViewers[id(root)];
        }

        function notEmpty(array) {
            return Array.isArray(array) && array.length;
        }

        function id(root) {
            return $(root).attr('id');
        }

        function tableError(error) {
            if (error) {
                hac.global.error(`Error populating the table: ${error}`);
            } else {
                hac.global.error("Error populating the table");
            }
        }

        function createTableViewer(element, data) {
            processActionColumns(data.columns)
            var tableViewer = $(element).dataTable({
                "order": [],
                "columns": data.columns,
                "bStateSave": false,
                "aLengthMenu": [[10, 25, 50, 100, -1], [10, 25, 50, 100, 'all']],
                "dom": 'Bl<"toolbar">frtip',
                "buttons": [
                    {
                        text: 'Refresh',
                        action: function (e, dt, node, config) {
                            populateTable(dt.table().node());
                        }
                    }
                ],
                "fnInitComplete": function () {
                    if (notEmpty(data.toolbarItems)) {
                        processToolbarItems($(this).parent().find('div.toolbar'), data.toolbarItems);
                    }
                }
            });
            tableViewers[id(element)] = tableViewer;
            return tableViewer;
        }

        function processActionColumns(columns) {
            $.each(columns, function (index, column) {
                if (column.action) {
                    column.render = function (data, type, row, meta) {
                        var actionLink = column.actionLink.interpolate({
                            row: row
                        });
                        return `<a href="${actionLink}">${column.actionName}</a>`;
                    }
                }
            });
        }

        function processToolbarItems(toolbarNode, toolbarItems) {
            $.each(toolbarItems, function (index, toolbarItem) {
                toolbarWidgets[toolbarItem.type](toolbarNode, toolbarItem.id, toolbarItem.name, toolbarItem.values);
            });
        }
    }

    String.prototype.interpolate = function (params) {
        const names = Object.keys(params);
        const values = Object.values(params);
        return new Function(...names, `return \`${this}\`;`)(...values);
    }

    function domReady(fn) {
        document.addEventListener("DOMContentLoaded", fn);
        if (document.readyState === "interactive" || document.readyState === "complete") {
            fn();
        }
    }

    domReady(setupTableViewers);
})();