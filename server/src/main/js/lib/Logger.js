

function Logger() {

    this.logDebug = logDebug;
    this.logInfo = logInfo;
    this.logError = logError;

    const _logError = console.error || console.log;

    function logInfo(msg) {
        // Catching all errors instead of simply testing for console existence to prevent issues with IE9.
        try { console.log(msg) } catch (e) {}
    }

    function logError(msg) {
        // Catching all errors instead of simply testing for console existence to prevent issues with IE9.
        try { _logError(msg) } catch (e) {}
    }

    function logDebug(msg) {
        if (window.debugEnabled) {
            logInfo(msg);
        }
    }
}

module.exports = new Logger();