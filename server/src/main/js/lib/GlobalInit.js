


function _babelPolyfillRequired() {
    return !(window.Promise && window.fetch && window.Symbol);
}

function init(params, ready) {

// Tells webpack where from where to load our external chunks.
    __webpack_public_path__ = params.assetsBasePath;

// Global flag to enable debug settings.
    window.debugEnabled = params.debugEnabled;

    if (_babelPolyfillRequired()) {
        import(/* webpackChunkName: "babel-polyfill" */ 'babel-polyfill').then(_ => {
            done(true);
        });
    } else {
        done(false);
    }

    function done(polyfillLoaded) {
        // Global error logger.
        const Logger = require('Logger');
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

        if (polyfillLoaded) {
            Logger.logInfo("Babel polyfill loaded.");
        }

        ready();
    }
}


module.exports = init;



