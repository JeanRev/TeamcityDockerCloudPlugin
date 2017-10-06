
const Utils = require('Utils');


function _osExclusionValidator(excludedOs, elt, context) {
    let daemonOs = context.daemonOs;
    if (!daemonOs) {
        return;
    }
    if (_isEmptyInput(elt)) {
        return;
    }
    if (daemonOs.toLowerCase() === excludedOs.toLowerCase()) {
        return {
            msg: 'This configuration field is not compatible with the daemon operating system ('
            + excludedOs + ').', warning: true
        };
    }
}

function _isEmptyInput($elt) {
    if ($elt.is(':checkbox') || $elt.is(':radio')) {
        return !$elt.is(':checked');
    } else {
        return !$elt.val();
    }
}

function autoTrim($elt) {
    let value = $elt.val().trim();
    $elt.val(value);
    return value;
}

module.exports = {
    autoTrim: autoTrim,
    requiredValidator: function ($elt) {
        let value = autoTrim($elt);
        if (!value) {
            return {msg: "This field is required."};
        }
    },

    ipv4OrIpv6Validator: function ($elt) {
        let value = autoTrim($elt);
        if (!Utils.isValidIpv4OrIpv6(value)) {
            return {msg: "Please specify a valid IPv4 or IPv6 address."};
        }
    },

    positiveIntegerValidator: function ($elt) {
        let value = $elt.val().trim().replace(/^0+(.+)/, '$1');
        $elt.val(value);
        if (!value) {
            return;
        }
        if (/^[0-9]+$/.test(value)) {
            // Check that we are in the positive range of a golang int64 max value, which is
            // 9223372036854775807 minus a safety marge due to comparison rounding errors.
            if (parseInt(value) > 9000000000000000000) {
                return {msg: "Value out of bound."};
            }
        } else {
            return {msg: "Value must be a positive integer."};
        }
    },

    portNumberValidator: function ($elt) {
        let value = $elt.val();
        if (!value) {
            return;
        }
        let number = parseInt($elt.val());
        if (number >= 1 && number <= 65535) {
            return;
        }
        return {msg: "Port number must be between 1 and 65535."};
    },

    cpusValidator: function($elt) {
        let value = autoTrim($elt).replace(/^0+\B/, '');
        $elt.val(value);
        if (!value) {
            return;
        }
        if (/^[0-9]+(\.[0-9]+)?$/.test(value)) {
            let number = parseFloat(value) * 1e9;
            if (number > 9000000000000000000) {
                return {msg: "Value out of bound."};
            }
            if (number % 1 !== 0) { // Should have no decimal part left.
                return {msg: "Value is too precise."};
            }
        } else {
            return {msg: "Value must be a positive decimal number."};
        }
    },

    cpuSetValidator: function($elt) {
        let value = autoTrim($elt);
        if (value && !/^[0-9]+(?:[-,][0-9]+)*$/.test(value)) {
            return {msg: "Invalid Cpuset specification."};
        }
    },

    versionValidator: function(targetVersion, $elt, context) {
        let daemonVersion = context.effectiveApiVersion;
        if (!daemonVersion) {
            return;
        }
        if (_isEmptyInput($elt)) {
            return;
        }

        if (Utils.compareVersionNumbers(daemonVersion, targetVersion) < 0) {
            return {
                msg: 'The daemon API version (v' + daemonVersion + ') is lower than required for ' +
                'this configuration field (v' + targetVersion + ').', warning: true
            };
        }
    },

    noWindowsValidator: _osExclusionValidator.bind(this, 'windows'),

    units_multiplier:  {
        GiB: 1073741824,
        MiB: 1048576,
        KiB: 1024,
        bytes: 1
    }
};