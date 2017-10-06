
const Logger = require('Logger');
const Utils = require('Utils');

function TableHelper(templates, arrayTemplates) {

    this.addTableRow = addTableRow;
    this.updateTableMandoryStarsVisibility = updateTableMandoryStarsVisibility;
    this.updateAllTablesMandoryStarsVisibility = updateAllTablesMandoryStarsVisibility;

    function addTableRow($tableBody) {
        let key = Utils.trimIdPrefix($tableBody.attr("id"));
        let index = $j.data($tableBody.get(0), "index") || 0;
        index++;
        Logger.logDebug("Adding row #" + index + " to table " + key + ".");

        let newRow = '<tr>' + arrayTemplates[key].replace(/IDX/g, index) + '<td' +
            ' class="center dockerCloudCtrlCell">' + templates.deleteCell + '</td></tr>';

        let $table = $tableBody.closest("table");
        // Add the new line as the last line before the table controls, or as the last table line if no
        // controls are available.
        let $lastRow = $tableBody.children('tr').last();
        if ($lastRow.hasClass('dockerCloudAddItem')) {
            $lastRow.before(newRow);
        } else {
            $tableBody.append(newRow);
        }
        $j.data($tableBody.get(0), "index", index);
        updateTableMandoryStarsVisibility($table);
    }

    function updateTableMandoryStarsVisibility($table) {
        $j(".mandatoryAsterix", $table).toggle($j('input, select', $table).length > 0)
    }

    function updateAllTablesMandoryStarsVisibility($tables) {
        $tables.each(function(i, table) {
            updateTableMandoryStarsVisibility($j(table));
        });
    }
}

module.exports = TableHelper;