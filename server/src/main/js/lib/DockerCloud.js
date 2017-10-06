
const Controller = require('Controller');
const Logger = require('Logger');
const Schema = require('Schema');

module.exports = {
    init: function (bs, oo, tabbedPane, params) {

        // Tells webpack where from where to load our external chunks.
        __webpack_public_path__ = params.assetsBasePath;

        // Global flag to enable debug settings.
        window.debugEnabled = params.debugEnabled;

        $j(window).on("error", function(msg, url, lineNo, columnNo, error) {
            if (error) {
                Logger.logError(error);
                return;
            }
            if (msg.originalEvent && msg.originalEvent.error) {
                Logger.logError(msg.originalEvent.error);
                return;
            }
            let logMsg = $j.type(msg) === 'string' ? msg : "Error encountered.";
            Logger.logError(logMsg + "@" + url + ":" + lineNo + ":" + columnNo);
        });

        const schema = new Schema();
        new Controller(bs, oo, tabbedPane, params, schema);
    },
};