
const Logger = require('Logger');
const Utils = require('Utils');

function ViewModelAccessor(templates, arrayTemplates) {

    this.restoreViewModel = restoreViewModel;
    this.applyViewModel = applyViewModel;

    function applyViewModel(viewModel) {
        $j('[id^="dockerCloudImage_"], [name^="dockerCloudImage_"]').each(function (i, elt) {
            let $elt = $j(elt);
            // TODO: handle attribute name according to input type.
            let key = $elt.attr('id') || $elt.attr('name');
            let match = /^dockerCloudImage_([^_]+)$/.exec(key);
            if (match) {
                _applyViewModelGroup(viewModel, match[1], $elt);
            }
        });
    }

    function _applyViewModelGroup(parentObject, key, $elt) {
        let tagName = $elt.prop("tagName");
        if (tagName === "TBODY") {

            let arrayTemplate = arrayTemplates[key];

            let colCount = arrayTemplate.match(/<td/g).length;
            // Clear the table content.
            $elt.empty();
            let value = parentObject[key];
            Utils.safeEach(value, function(val) {
                let $row = $j('<tr>').appendTo($elt);
                let index = $elt.data("index") || 0;
                $elt.data("index", index + 1);

                $row.append(arrayTemplate.replace(/IDX/g, index));
                let $rowItems = $j('input, select', $row);
                if ($rowItems.length === 1) {
                    $rowItems.each(function(i, rowItem) {
                        $j(rowItem).val(val);
                    });
                } else {
                    $rowItems.each(function(i, rowItem) {
                        let $rowItem  = $j(rowItem);
                        let regex = new RegExp('^dockerCloudImage_' + key + '_[0-9]+_(.*)$');
                        let match = regex.exec($rowItem.attr('id'));
                        if (match) {
                            _applyViewModelGroup(val, match[1], $rowItem);
                        }
                    });
                }
                $row.append('<td class="center dockerCloudCtrlCell">' + templates.deleteCell + '</td>');

            });
            templates.insertAddButton($elt, colCount);
        } else if ($elt.is(':text') || $elt.is(':password')) {
            $elt.val(parentObject[key]);
        } else if ($elt.is(':checkbox')) {
            $elt.prop('checked', parentObject[key] === true);
        } else if ($elt.is(':radio')) {
            $elt.prop('checked', parentObject[key] === $elt.val());
        } else if (tagName === 'SELECT') {
            $elt.val(parentObject[key]);
        } else {
            Logger.logError("Unhandled tag type: " + tagName);
        }
    }

    function restoreViewModel(){
        const viewModel = {};
        $j('[id^="dockerCloudImage_"], [name^="dockerCloudImage_"]').each(function(i, elt) {
            let $elt = $j(elt);
            let id = $elt.attr('id') || $elt.attr('name');
            let match = /^dockerCloudImage_([^_]+)$/.exec(id);
            if (match) {
                _restoreViewModelGroup(viewModel, match[1], $elt);
            }
        });
        return viewModel;
    }

    function _restoreViewModelGroup(parentObject, key, $elt){
        let tagName = $elt.prop('tagName');
        if (tagName === "TBODY") {
            $j('tr', $elt).each(function (i, row) {

                let $row = $j(row);
                let $rowItems = $j('input, select', $row);

                let rowObject = parentObject[key];
                if (!rowObject) {
                    rowObject = parentObject[key] = [];
                }

                if (!$rowItems.length) {
                    // Filter table rows without items (eg. table rows holding controls).
                    return;
                }

                if ($rowItems.length === 1) {
                    $rowItems.each(function (i, rowItem) {
                        parentObject[key].push($j(rowItem).val());
                    });
                } else {
                    let childObject = {};
                    $rowItems.each(function (i, rowItem) {
                        let $rowItem = $j(rowItem);
                        let regex = new RegExp('^dockerCloudImage_' + key + '_[0-9]+_(.*)$');
                        let match = regex.exec($rowItem.attr('id'));
                        if (match) {
                            _restoreViewModelGroup(childObject, match[1], $rowItem);
                        }
                    });
                    rowObject.push(childObject);
                }
            });
        } else if (tagName === 'SELECT') {
            parentObject[key] = $elt.val();
        } else if ($elt.is(':text') || $elt.is(':password')) {
            parentObject[key] = $elt.val();
        } else if ($elt.is(':checkbox')) {
            parentObject[key] = $elt.is(':checked');
        } else if ($elt.is(':radio')) {
            if ($elt.is(':checked')) {
                parentObject[key] = $elt.attr('value');
            }
        }
    }
}

module.exports = ViewModelAccessor;