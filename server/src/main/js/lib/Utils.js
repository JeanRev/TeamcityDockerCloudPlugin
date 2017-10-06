
const Logger = require('Logger');
const B64 = require('base64-js');
const IP_V4_OR_V6_REGEX = /((^\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\s*$)|(^\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))(%.+)?\s*$))/;
const ELT_ID_PREFIX = 'dockerCloudImage_';

function _convertVersionToDigits(version) {
    let tokens = version.split('.');
    let digits = [];
    $j(tokens).each(function(i, token) {
        let digit;
        if (token.match('^[0-9]+$')) {
            digit = parseInt(token, 10);
        }
        if (!digit || isNaN(digit)) {
            digit = 0;
        }
        digits.push(digit);
    });
    return digits;
}

function _isArray(object) {
    return $j.type(object) === "array";
}

function _nullSafeGet(array, path) {
    if (path.length) {
        let value = array.get(path[0]);
        if (value) {
            if (path.length === 1) {
                return value;
            }
            path.splice(0);
            return _nullSafeGet(array, path);

        }
    }
}

function notEmpty(value){
    return value !== undefined && value !== null && (value.length === undefined || value.length) && ($j.type(value) !== "object" || Object.keys(value).length);
}

module.exports = {
    shortenString: function(str, maxLen) {
        if (!str) {
            return "";
        }
        if (str.length <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen).trim() + "…";
    },
    compareVersionNumbers: function(v1, v2) {
        const v1Digits = _convertVersionToDigits(v1);
        const v2Digits = _convertVersionToDigits(v2);

        for (let i = 0; i < v1Digits.length && i < v2Digits.length; i++) {
            let cmp = v1Digits[i] - v2Digits[i];
            if (cmp !== 0) {
                return cmp;
            }
        }

        return v1Digits.length - v2Digits.length;
    },

    nullSafeGet: _nullSafeGet,
    notEmpty: notEmpty,
    safeEach: function(array, fn) {
        if (_isArray(array)) {
            $j.each(array, function(i, val) {
                fn(val);
            });
        }
    },

    safeKeyValueEach: function(array, fn) {
        if (array) {
            $j.each(array, function (key, value) {
                fn(key, value);
            });
        }
    },

    getElt: function(id) {
        return $j('#' + ELT_ID_PREFIX + id);
    },

    trimIdPrefix: function(id) {
        return id.substring(ELT_ID_PREFIX.length);
    },

    str: function(obj) {
        return notEmpty(obj) ? obj.toString() : obj;
    },

    removeFromArray: function (array, value) {
        let i = array.indexOf(value);
        if (i !== -1) {
            return array.splice(i, 1);
        }
    },
    addToArrayIfAbsent: function (array, value) {
        let i = array.indexOf(value);
        if (i === -1) {
            array.push(value);
        }
    },
    // Base-64 encoding/decoding is tricky to achieve in an unicode-safe way (especially when using the atob
    // and btoa standard functions). We leverage here an all-purpose binary-based Base64 encoder and do the
    // string to UTF-16BE conversion ourselves.
    base64Utf16BEEncode: function(str) {
        if (!str) {
            return;
        }
        try {
            let arr = [];
            for (let i = 0; i < str.length; i++) {
                let charcode = str.charCodeAt(i);
                arr.push((charcode >> 8) & 0xff);
                arr.push(charcode & 0xff);
            }
            return B64.fromByteArray(arr);
        } catch (e) {
            Logger.logError("Failed to encode base64 string.");
        }
    },
    base64Utf16BEDecode: function(base64) {
        if (!base64) {
            return;
        }
        try {
            let byteArray = B64.toByteArray(base64);
            if (byteArray.length % 2 !== 0) {
                Logger.logError("Invalid content length.");
            }
            let charcodes = [];
            for (let i = 0; i < byteArray.length - 1; i+=2) {
                charcodes.push((((byteArray[i] & 0xff) << 8) | (byteArray[i+1] & 0xff)));
            }
            return String.fromCharCode.apply(null, charcodes);
        } catch (e) {
            Logger.logError(e);
        }
    },
    sanitizeURI: function (uri, windowsHost) {
        uri = uri && uri.trim();
        if (!uri) {
            return uri;
        }
        let match = uri.match(windowsHost ? /^([a-zA-Z]+?):[/\\]+(.*)/ : /^([a-zA-Z]+?):\/+(.*)/);
        let scheme;
        let schemeSpecificPart;
        let ignore = false;

        // Default: assume TCP hostname.
        scheme = 'tcp';
        schemeSpecificPart = uri;

        if (match) {
            // Some scheme detected, use it instead.
            scheme = match[1].toLowerCase();
            schemeSpecificPart = match[2];
        } if (!IP_V4_OR_V6_REGEX.test(uri)) {
            // Not an IP address, does it look like a file path ?
            if (windowsHost) {
                match = uri.match(/^[/\\]+(.*)/);
                if (match) {
                    scheme = 'npipe';
                    schemeSpecificPart = match[1];
                }
            } else {
                match = uri.match(/^\/+(.*)/);
                if (match) {
                    scheme = 'unix';
                    schemeSpecificPart = match[1];
                }
            }
        }

        let leadingSlashes;
        if (scheme === 'unix') {
            leadingSlashes = '///';
        } else if (scheme === 'npipe') {
            leadingSlashes = '////';
            schemeSpecificPart = schemeSpecificPart.replace(/\\/g, '/');
        } else {
            leadingSlashes = '//';
        }

        uri = scheme + ':' + leadingSlashes + schemeSpecificPart;

        return uri;
    },
    isValidIpv4OrIpv6: function(ip) {
        return ip && IP_V4_OR_V6_REGEX.test(ip);
    }
};