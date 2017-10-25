
const TableHelper = require('TableHelper');

function ValidatorTester(validationHandler, arrayTemplates) {

    const emptyTemplates = {
        deleteCell: '',
        settingsCell: '<span></span></a>',
        insertAddButton: function() {}
    };

    const tableHelper = new TableHelper(emptyTemplates, arrayTemplates);

    this.loadElt = loadElt;
    this.Table = Table;
    this.verifyOk = verifyOk;
    this.verifyFailure = verifyFailure;
    this.verifyMandatory = verifyMandatory;
    this.verifyInteger = verifyInteger;
    this.verifyProfile = verifyProfile;
    this.verifyImage = verifyImage;
    this.verifyUseOfficialTCAgentImage = verifyUseOfficialTCAgentImage;
    this.verifyMaxInstanceCount = verifyMaxInstanceCount;
    this.verifyRegistryUserAndPassword = verifyRegistryUserAndPassword;
    this.verifyMemory = verifyMemory;
    this.verifyCpuSet = verifyCpuSet;
    this.verifyPortNumber = verifyPortNumber;
    this.verifyIPAddress = verifyIPAddress;
    this.verifyNoWindows = verifyNoWindows;
    this.verifyMinApi1_25 = verifyMinApi1_25;

    function loadElt(id) {
        let effectiveId = 'dockerCloudImage_' + id;
        let $elt = $j('#' + effectiveId);
        expect($elt.length).toEqual(1);
        return { $elt: $elt, eltId: id }
    }

    function Table(id) {
        this.addRow = function() {
            let $tbody = $j('#dockerCloudImage_' + id);
            tableHelper.addTableRow($tbody);
            return new Row($j.data($tbody.get(0), "index"), id);
        }
    }

    function Row(index, eltId) {
        this.singleField = function() {
            return { $elt: $j('#dockerCloudImage_' + eltId + '_' + index), eltId: eltId + '_IDX' };
        };
        this.field = function(name){
            return { $elt: $j('#dockerCloudImage_' + eltId + '_' + index + '_' + name),
                eltId: eltId + '_IDX_' + name };
        };
    }

    function verifyFailure(eltHandle, value, context) {
        context = context || {};
        eltHandle.$elt.val(value);
        let result = validationHandler.validate(eltHandle.$elt, eltHandle.eltId, context);
        expect(result.error).not.toEqual(null);
    }

    function verifyAutoTrim(eltHandle, context) {
        context = context || {};
        validationHandler.validate(eltHandle.$elt.val('  '), eltHandle.eltId, context);
        expect(eltHandle.$elt.val()).toEqual('');
        validationHandler.validate(eltHandle.$elt.val('\t'), eltHandle.eltId, context);
        expect(eltHandle.$elt.val()).toEqual('');
        validationHandler.validate(eltHandle.$elt.val(' abc\t'), eltHandle.eltId, context);
        expect(eltHandle.$elt.val()).toEqual('abc');
    }

    function verifyMandatory(eltHandle, context) {
        verifyAutoTrim(eltHandle, context);
        verifyFailure(eltHandle, '', context);
        verifyFailure(eltHandle, '  ', context);
        verifyFailure(eltHandle, '\t', context);
    }

    function verifyOk(eltHandle, value, context) {
        context = context || {};
        eltHandle.$elt.val(value);
        let result = validationHandler.validate(eltHandle.$elt, eltHandle.eltId, context);
        expect(result).toEqual({ error: null, warnings: [] });
    }

    function verifySingleWarn(eltHandle, value, context) {
        eltHandle.$elt.val(value);
        let result = validationHandler.validate(eltHandle.$elt, eltHandle.eltId, context);
        expect(result.error).toEqual(null);
        expect(result.warnings.length).toEqual(1);
    }

    function verifyInteger(eltHandle, lowerBound, upperBound) {
        if (typeof lowerBound === 'undefined') {
            lowerBound = 0;
        }
        let upperBoundTestValue;
        if (typeof upperBound === 'undefined') {
            upperBound = 9000000000000000000;
            upperBoundTestValue = 9100000000000000000;
        } else {
            upperBoundTestValue = upperBound + 1;
        }
        verifyAutoTrim(eltHandle);

        // Lower bound
        verifyFailure(eltHandle, lowerBound - 1);
        verifyOk(eltHandle, lowerBound);

        // Upper bound
        verifyOk(eltHandle, upperBound);
        verifyFailure(eltHandle, upperBoundTestValue);

        // Strict parsing.
        verifyFailure(eltHandle, '1.0');
        verifyFailure(eltHandle, '1a');
        verifyFailure(eltHandle, 'a1');

        // Trim leading zeroes
        validationHandler.validate(eltHandle.$elt.val(0), eltHandle.eltId, {});
        expect(eltHandle.$elt.val()).toEqual('0');
        validationHandler.validate(eltHandle.$elt.val('00'), eltHandle.eltId, {});
        expect(eltHandle.$elt.val()).toEqual('0');
        validationHandler.validate(eltHandle.$elt.val('0042'), eltHandle.eltId, {});
        expect(eltHandle.$elt.val()).toEqual('42');
    }

    function verifyProfile(eltHandle) {
        let imagesData = {};
        let context = {
            getImagesData: function() {
                return imagesData;
            }
        };

        verifyOk(eltHandle, 'profile_name', context);

        verifyMandatory(eltHandle, context);

        // Invalid chars.
        verifyFailure(eltHandle, 'profile name', context);
        verifyFailure(eltHandle, 'profile%name', context);
        verifyFailure(eltHandle, 'profilé_name', context);

        // Existing profile.
        context.profile = 'profile_name';
        imagesData['profile_name'] = {};

        verifyOk(eltHandle, 'profile_name', context);

        // Duplicated profile name.
        context.profile = 'profile_name2';
        imagesData['profile_name2'] = {};

        verifyFailure(eltHandle, 'profile_name', context);
    }

    function verifyImage(eltHandle) {
        verifyOk(eltHandle, 'image:1.0');
        verifyMandatory(eltHandle);
    }

    function verifyNoWindows(eltHandle, value) {

        let context = {};

        let checkbox = eltHandle.$elt.is(':checkbox');
        if (checkbox) {
            eltHandle.$elt.prop('checked', false);
            verifyOk(eltHandle, undefined, context);
        } else {
            verifyOk(eltHandle, '', context);
        }

        if (checkbox) {
            eltHandle.$elt.prop('checked', true);
            verifyOk(eltHandle, undefined, context);
        } else {
            verifyOk(eltHandle, value, context);
        }

        context.daemonOs = 'windows';

        if (checkbox) {
            eltHandle.$elt.prop('checked', true);
            verifySingleWarn(eltHandle, undefined, context);
        } else {
            verifySingleWarn(eltHandle, value, context);
        }
    }

    function verifyUseOfficialTCAgentImage(eltHandle) {
        verifyNoWindows(eltHandle);
    }

    function verifyMaxInstanceCount(eltHandle) {
        verifyInteger(eltHandle, 1);
    }

    function verifyRegistryUserAndPassword(userEltHandle, pwdEltHandle) {
        verifyAutoTrim(userEltHandle);

        // Both user and password must be specified or none.
        verifyOk(userEltHandle, '');
        verifyOk(pwdEltHandle, '');

        verifyOk(userEltHandle, 'user');
        verifyFailure(pwdEltHandle, '');

        verifyOk(pwdEltHandle, 'pwd');
        verifyFailure(userEltHandle, '');

        // Whitespaces are preserved.
        verifyOk(pwdEltHandle, ' ');
        expect(pwdEltHandle.$elt.val()).toEqual(' ');
    }

    function verifyMemory(eltHandle, $memoryUnit) {
        // Test 4MiB limit
        $memoryUnit.val('bytes');
        verifyInteger(eltHandle, 4194304);
        $memoryUnit.val('KiB');
        verifyInteger(eltHandle, 4096);
        $memoryUnit.val('MiB');
        verifyInteger(eltHandle, 4);
    }

    function verifyCpuSet(eltHandle) {
        verifyAutoTrim(eltHandle);
        verifyOk(eltHandle, '');
        verifyOk(eltHandle, '0,1,2');
        verifyOk(eltHandle, '0-1');
        verifyOk(eltHandle, '0-1,2-3,4-5');
        verifyFailure(eltHandle, '0-1, 2-3,4-5');
        verifyFailure(eltHandle, '-1');
        verifyFailure(eltHandle, '0-a');
        verifyNoWindows(eltHandle, 0);
    }

    function verifyPortNumber(eltHandle) {
        verifyInteger(eltHandle, 1, 65535);
    }

    function verifyIPAddress(eltHandle) {
        verifyAutoTrim(eltHandle);
        verifyOk(eltHandle, '127.0.0.1');
        verifyOk(eltHandle, '0000:0000:0000:0000:0000:0000:0000:0001');
        verifyOk(eltHandle, 'aced::a11:7e57');
        verifyOk(eltHandle, '::1');
        verifyFailure(eltHandle, 'hostname');
    }

    function verifyMinApi1_25(eltHandle, value) {
        verifyOk(eltHandle, '');

        verifyOk(eltHandle, value);

        let context = {};

        context.effectiveApiVersion = '1.25';
        verifyOk(eltHandle, value, context);

        context.effectiveApiVersion = '1.26';
        verifyOk(eltHandle, value, context);

        context.effectiveApiVersion = '1.24';
        verifySingleWarn(eltHandle, value, context);
    }
}

module.exports = ValidatorTester;