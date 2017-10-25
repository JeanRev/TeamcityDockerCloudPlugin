
const Utils = require('Utils');

const units_multiplier =  {
    GiB: 1073741824,
        MiB: 1048576,
        KiB: 1024,
        bytes: 1
};

const time_units_multiplier = {
    h: 3600000000000,
        s: 1000000000,
        ms: 1000000,
        us: 1000,
        ns: 1
};

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

function requiredValidator($elt) {
    let value = autoTrim($elt);
    if (!value) {
        return {msg: "This field is required."};
    }
}

function noWindowsValidator(elt, context) {
    return _osExclusionValidator('windows', elt, context);
}

function positiveIntegerValidator($elt) {
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
}

function registryUserValidatorsFn($registryPassword) {
    return [function ($elt) {
        autoTrim($elt);
        let pass = $registryPassword.val();
        let user = $elt.val();
        if (pass && !user) {
            return {msg: 'Must specify user if password set.'}
        }
    }]
}

function registryPasswordValidatorsFn($registryUser) {
    return [function ($elt){
        let user = $registryUser.val().trim();
        let pass = $elt.val();
        if (user && !pass) {
            return {msg: 'Must specify password if user set.'}
        }
    }]
}

function memoryValidatorsFn($memoryUnit) {
    return [positiveIntegerValidator, function ($elt) {
        let value = $elt.val();
        if (!value) {
            return;
        }
        let number = parseInt(value);
        let multiplier = units_multiplier[$memoryUnit.val()];
        if ((number * multiplier) < 4194304) {
            return {msg: "Memory must be at least 4Mb."}
        }
    }]
}

function _portNumberValidator($elt) {
    let value = $elt.val();
    if (!value) {
        return;
    }
    let number = parseInt($elt.val());
    if (number >= 1 && number <= 65535) {
        return;
    }
    return {msg: "Port number must be between 1 and 65535."};
}

function _versionValidator(targetVersion, $elt, context) {
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
}

function api1_25RequiredValidator($elt, context) {
    return _versionValidator('1.25', $elt, context);
}

module.exports = {
    autoTrim: autoTrim,
    requiredValidator: requiredValidator,
    positiveIntegerValidator: positiveIntegerValidator,
    ipv4OrIpv6Validator: function ($elt) {
        let value = autoTrim($elt);
        if (!Utils.isValidIpv4OrIpv6(value)) {
            return {msg: "Please specify a valid IPv4 or IPv6 address."};
        }
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
    api1_25RequiredValidator: api1_25RequiredValidator,
    profileValidators: [requiredValidator, function($elt) {
        if (!/^\w+$/.test($elt.val())) {
            return {msg: 'Only alphanumerical characters (without diacritic) and underscores' +
            ' allowed.'}
        }
    }, function($elt, context) {
        let newProfile = $elt.val();
        let currentProfile = context.profile;
        if (newProfile !== currentProfile && context.getImagesData()[newProfile]) {
            return {msg: 'An image profile with this name already exists.'}
        }
    }],
    noWindowsValidator: noWindowsValidator,
    imageValidators: [requiredValidator],
    useOfficialTCAgentImageValidators: [noWindowsValidator],
    maxInstanceCountValidators: [positiveIntegerValidator, function($elt) {
        let value = $elt.val();
        if (value && parseInt(value) < 1) {
            return {msg: "At least one instance must be permitted."};
        }
    }],
    registryUserValidatorsFn: registryUserValidatorsFn,
    registryPasswordValidatorsFn: registryPasswordValidatorsFn,
    memoryValidatorsFn: memoryValidatorsFn,
    portNumberValidators: [positiveIntegerValidator, _portNumberValidator],
    units_multiplier: units_multiplier,
    time_units_multiplier: time_units_multiplier
};