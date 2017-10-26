
const Utils = require('Utils');
const Validators = require('Validators');
const html = require('image-swarm-settings.html');

function SwarmSchema() {

    const $html = $j(html);

    const $image = Utils.getElt('Image', $html);
    const $useOfficialDockerImage = Utils.getElt('UseOfficialTCAgentImage', $html);
    const $registryUser = Utils.getElt('RegistryUser', $html);
    const $registryPassword = Utils.getElt('RegistryPassword', $html);
    const $memoryUnit = Utils.getElt('MemoryBytesLimitUnit', $html);
    const $memory = Utils.getElt('Memory', $html);

    this.initializeSettings = initializeSettings;
    this.getImage = getImage;
    this.convertViewModelToSettings = convertViewModelToSettings;
    this.convertSettingsToViewModel = convertSettingsToViewModel;
    this.migrateSettings = migrateSettings;

    _initHandlers();

    function initializeSettings(context) {
        return {
            Administration: {
                UseOfficialTCAgentImage: !context.daemonOs || context.daemonOs.toLowerCase() !== 'windows',
                MaxInstanceCount: 2
            },
            Editor: {
                MemoryBytesLimitUnit: 'bytes',
                StopGracePeriodUnit: 's'
            }
        };
    }

    function getImage(agentHolderSpec) {
        return agentHolderSpec.TaskTemplate &&
            agentHolderSpec.TaskTemplate.ContainerSpec &&
            agentHolderSpec.TaskTemplate.ContainerSpec.Image;
    }

    function _copy(source, target, fieldName, conversionFunction) {
        _copyFromTo(source, target, fieldName, fieldName, conversionFunction);
    }

    function _copyFromTo(source, target, fromFieldName, toFieldName, conversionFunction) {
        let value = source[fromFieldName];
        if (Utils.notEmpty(value)) {
            target[toFieldName] =  conversionFunction ? conversionFunction(value) : value;
        }
    }

    function convertViewModelToSettings(viewModel) {
        let settings = {};

        let admin = {};

        _copy(viewModel, admin, 'MaxInstanceCount', parseInt);
        _copy(viewModel, admin, 'Profile');
        _copy(viewModel, admin, 'UseOfficialTCAgentImage');
        _copy(viewModel, admin, 'RegistryUser');
        _copy(viewModel, admin, 'RegistryPassword', Utils.base64Utf16BEEncode);

        if (Utils.notEmpty(admin)) {
            settings.Administration = admin;
        }

        let service = {};
        let taskTemplate = {};
        let containerSpec = {};
        let editor = {};

        _copy(viewModel, containerSpec, 'Image');
        _copy(viewModel, containerSpec, 'User');
        _copy(viewModel, containerSpec, 'Hostname');
        _copy(viewModel, containerSpec, 'Command');
        _copy(viewModel, containerSpec, 'Dir');
        _copy(viewModel, containerSpec, 'StopSignal');
        _copy(viewModel, editor, 'StopGracePeriodUnit');
        if (Utils.notEmpty(viewModel.StopGracePeriod)) {
            containerSpec.StopGracePeriod = parseInt(viewModel.StopGracePeriod) * Validators.time_units_multiplier[viewModel.StopGracePeriodUnit];
        }

        if (Utils.notEmpty(viewModel.Env)) {
            containerSpec.Env = [];
            Utils.safeEach(viewModel.Env, function (envEntry) {
                containerSpec.Env.push(envEntry.Name + '=' + envEntry.Value);
            });
        }

        if (Utils.notEmpty(viewModel.Labels)) {
            containerSpec.Labels = {};
            Utils.safeEach(viewModel.Labels, function (label) {
                containerSpec.Labels[label.Key] = label.Value;
            });
        }

        let dnsConfig = {};
        _copyFromTo(viewModel, dnsConfig, 'Dns', 'Nameservers');
        _copyFromTo(viewModel, dnsConfig, 'DnsSearch', 'Search');
        if (Utils.notEmpty(dnsConfig)) {
            containerSpec.DNSConfig = dnsConfig;
        }

        _copyFromTo(viewModel, containerSpec, 'Hosts', 'Hosts');

        if (Utils.notEmpty(viewModel.Ports)) {
            service.EndpointSpec = { Ports: [] };
            Utils.safeEach(viewModel.Ports, function(port) {
                let portSettings = {};
                _copy(port, portSettings, 'PublishedPort', parseInt);
                _copy(port, portSettings, 'TargetPort', parseInt);
                _copy(port, portSettings, 'Protocol');
                service.EndpointSpec.Ports.push(portSettings);
            });
        }


        _copy(viewModel, editor, 'MemoryBytesLimitUnit');
        let limits = {};
        if (Utils.notEmpty(viewModel.MemoryBytesLimit)) {
            limits.MemoryBytes = parseInt(viewModel.MemoryBytesLimit) * Validators.units_multiplier[viewModel.MemoryBytesLimitUnit];
        }
        if (Utils.notEmpty(viewModel.CPUs)) {
            limits.NanoCPUs = Math.floor(parseFloat(viewModel.CPUs) * 1e9);
        }

        if (Utils.notEmpty(limits)) {
            taskTemplate.Resources = { Limits: limits };
        }


        let logDriver = {};
        _copyFromTo(viewModel, logDriver, 'LogDriver', 'Name');
        if (Utils.notEmpty(viewModel.LogOptions)) {
            logDriver.Options = {};
            Utils.safeEach(viewModel.LogOptions, function(logOption) {
                logDriver.Options[logOption.Key] = logOption.Value;
            });
        }
        if (Utils.notEmpty(logDriver)) {
            containerSpec.LogDriver = logDriver;
        }

        let mounts = [];
        Utils.safeEach(viewModel.Volumes, function(volume) {
            let mount = { Target: volume.PathInContainer,  Type: 'volume',
                ReadOnly: volume.ReadOnly};
            _copyFromTo(volume, mount, 'PathOnHost', 'Source');
            mounts.push(mount);
        });
        if (Utils.notEmpty(mounts)) {
            containerSpec.Mounts = mounts;
        }

        if (Utils.notEmpty(containerSpec)) {
            taskTemplate.ContainerSpec = containerSpec;
        }
        if (Utils.notEmpty(taskTemplate)) {
            service.TaskTemplate = taskTemplate;
        }
        if (Utils.notEmpty(service)) {
            settings.AgentHolderSpec = service;
        }
        if (Utils.notEmpty(editor)) {
            settings.Editor = editor;
        }
        return settings;
    }

    function convertSettingsToViewModel(settings) {
        let viewModel = {};

        let admin = settings.Administration || {};
        let editor = settings.Editor || {};
        let service = settings.AgentHolderSpec || {};
        let taskTemplate = service.TaskTemplate || {};
        let containerSpec = taskTemplate.ContainerSpec || {};

        _copy(admin, viewModel, 'Profile');
        _copy(admin, viewModel, 'MaxInstanceCount');
        _copy(admin, viewModel, 'UseOfficialTCAgentImage');
        _copy(admin, viewModel, 'RegistryUser');
        _copy(admin, viewModel, 'RegistryPassword', Utils.base64Utf16BEDecode);

        _copy(containerSpec, viewModel, 'Image');
        _copy(containerSpec, viewModel, 'User');
        _copy(containerSpec, viewModel, 'Hostname');

        _copy(containerSpec, viewModel, 'Command');
        _copy(containerSpec, viewModel, 'Dir');
        _copy(containerSpec, viewModel, 'StopSignal');
        _copy(editor, viewModel, 'StopGracePeriodUnit');
        if (Utils.notEmpty(containerSpec.StopGracePeriod)) {
            viewModel.StopGracePeriod = Utils.str(Math.floor(containerSpec.StopGracePeriod / Validators.time_units_multiplier[viewModel.StopGracePeriodUnit]));
        }

        let env = [];
        Utils.safeEach(containerSpec.Env, function(envEntry) {
            let sepIndex = envEntry.indexOf('=');
            if (sepIndex !== -1) {
                env.push({ Name: envEntry.substring(0, sepIndex), Value: envEntry.substring(sepIndex + 1)});
            }
        });

        if (env.length) {
            viewModel.Env = env;
        }

        let labels = [];
        Utils.safeKeyValueEach(containerSpec.Labels, function(key, value) {
            labels.push({ Key: key, Value: value});
        });
        if (labels.length) {
            viewModel.Labels = labels;
        }

        let dnsConfig = containerSpec.DNSConfig || {};
        _copyFromTo(dnsConfig, viewModel, 'Nameservers', 'Dns');
        _copyFromTo(dnsConfig, viewModel, 'Search', 'DnsSearch');

        _copy(containerSpec, viewModel, 'Hosts');

        let endpointSpec = service.EndpointSpec || {};
        _copy(endpointSpec, viewModel, 'Ports');

        _copy(editor, viewModel, 'MemoryBytesLimitUnit');
        let limits = (taskTemplate.Resources && taskTemplate.Resources.Limits) || {};
        if (Utils.notEmpty(limits.MemoryBytes)) {
            viewModel.MemoryBytesLimit = Utils.str(Math.floor(limits.MemoryBytes / Validators.units_multiplier[viewModel.MemoryBytesLimitUnit]));
        }
        if (Utils.notEmpty(limits.NanoCPUs)) {
            viewModel.CPUs = Utils.str(limits.NanoCPUs / 1e9);
        }

        let logDriver = containerSpec.LogDriver || {};
        _copyFromTo(logDriver, viewModel, 'Name', 'LogDriver');

        let logOptions = [];
        Utils.safeKeyValueEach(logDriver.Options, function(key, value) {
            logOptions.push({ Key: key, Value: value });
        });

        if (logOptions.length) {
            viewModel.LogOptions = logOptions;
        }
        let volumes = [];
        Utils.safeEach(containerSpec.Mounts, function(mount) {
            let volume = { PathInContainer: mount.Target, ReadOnly: mount.ReadOnly };
            _copyFromTo(mount, volume, 'Source', 'PathOnHost');
            volumes.push(volume);
        });

        if (volumes.length) {
            viewModel.Volumes = volumes;
        }

        return viewModel;
    }

    function migrateSettings(imageData) {
        // Nothing to migrate yet.
    }

    const validators = {
        Profile: Validators.profileValidators,
        Image: Validators.imageValidators,
        UseOfficialTCAgentImage: Validators.useOfficialTCAgentImageValidators,
        MaxInstanceCount: Validators.maxInstanceCountValidators,
        RegistryUser: Validators.registryUserValidatorsFn($registryPassword),
        RegistryPassword: Validators.registryPasswordValidatorsFn($registryUser),
        StopGracePeriod: [Validators.positiveIntegerValidator],
        Dns_IDX: [Validators.requiredValidator],
        DnsSearch_IDX: [Validators.requiredValidator],
        Hosts_IDX_Name: [Validators.requiredValidator],
        LogOptions_IDX_Key: [Validators.requiredValidator],
        Env_IDX_Name: [Validators.requiredValidator],
        Labels_IDX_Key: [Validators.requiredValidator],
        MemoryBytesLimit: Validators.memoryValidatorsFn($memoryUnit),
        Ports_IDX_PublishedPort: Validators.portNumberValidators,
        Ports_IDX_TargetPort: [Validators.requiredValidator].concat(Validators.portNumberValidators),
        Hosts_IDX: [Validators.requiredValidator],
        Volumes_IDX_PathInContainer: [Validators.requiredValidator]
    };

    const arrayTemplates = {
        imagesTableRow: '<tr class="imagesTableRow"><td class="image_data_Name highlight"></td>' +
        '<td class="maxInstance highlight"></td>' +
        '<td class="reusable highlight"></td>' +
        '<td class="edit highlight"><a href="#/" class="editImageLink">edit</a></td>\
<td class="remove"><a href="#/" class="removeImageLink">delete</a></td>' +
        '</tr>',
        Entrypoint: '<td><input type="text" id="dockerCloudImage_Entrypoint_IDX"/><span class="error" id="dockerCloudImage_Entrypoint_IDX_error"></span></td>',
        Command: '<td><input type="text" id="dockerCloudImage_Command_IDX"/></td>',
        Env: '<td><input type="text" id="dockerCloudImage_Env_IDX_Name" /><span class="error" id="dockerCloudImage_Env_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Env_IDX_Value" /></td>',
        Volumes: '<td><input type="text" id="dockerCloudImage_Volumes_IDX_PathOnHost" /></td>\
        <td><input type="text" id="dockerCloudImage_Volumes_IDX_PathInContainer" /><span class="error" id="dockerCloudImage_Volumes_IDX_PathInContainer_error"></span></td>\
        <td class="center"><input type="checkbox" id="dockerCloudImage_Volumes_IDX_ReadOnly" /></td>',
        Labels: '<td><input type="text" id="dockerCloudImage_Labels_IDX_Key" /><span class="error" id="dockerCloudImage_Labels_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Labels_IDX_Value" /></td>',
        LogOptions: '<td><input type="text" id="dockerCloudImage_LogOptions_IDX_Key" /><span class="error"\
         id="dockerCloudImage_LogOptions_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_LogOptions_IDX_Value" /></td>',
        Dns: '<td><input type="text" id="dockerCloudImage_Dns_IDX"/><span class="error"' +
        ' id="dockerCloudImage_Dns_IDX_error"></span></td>',
        DnsSearch: '<td><input type="text" id="dockerCloudImage_DnsSearch_IDX"/><span class="error"' +
        ' id="dockerCloudImage_DnsSearch_IDX_error"></span></td>',
        Ports: '<td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_PublishedPort" size="5"/><span\
         class="error"\
         id="dockerCloudImage_Ports_IDX_PublishedPort_error"></td>\
        <td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_TargetPort" size="5"/><span class="error"\
         id="dockerCloudImage_Ports_IDX_TargetPort_error"></span></td>\
        <td class="center"><select id="dockerCloudImage_Ports_IDX_Protocol">\
            <option value="tcp" selected="selected">tcp</option>\
            <option value="udp">udp</option>\
        </select></td>',
        Hosts: ' <td><input type="text" id="dockerCloudImage_Hosts_IDX" /><span class="error"\
         id="dockerCloudImage_Hosts_IDX_error"></span></td>'
    };

    const tabs = [{ id: 'dockerCloudImageTab_general', lbl: 'General' },
        { id: 'dockerCloudImageTab_run', lbl: 'Run' },
        { id: 'dockerCloudImageTab_network', lbl: 'Network' },
        { id: 'dockerCloudImageTab_resources', lbl: 'Resources' },
        { id: 'dockerCloudImageTab_advanced', lbl: 'Advanced' }];

    const translations = {
        'test.create.success': 'Service {0} successfully created',
        'test.create.warning': 'Service {0} created with warnings:',
        'test.logs': 'Service logs:',
        'test.wait.success': 'Agent connection detected for service {0}.',
        'test.wait.warning': 'Agent connection detected for service {0}:'
    };

    this.html = $html;
    this.validators = validators;
    this.arrayTemplates = arrayTemplates;
    this.tabs = tabs;
    this.translations = translations;
    this.cloudType = 'SWARM';

    function _initHandlers() {
        $useOfficialDockerImage.change(function () {
            let useOfficialAgentImage = $useOfficialDockerImage.is(':checked');
            $image.prop('disabled', useOfficialAgentImage);
            $registryUser.prop('disabled', useOfficialAgentImage);
            $registryPassword.prop('disabled', useOfficialAgentImage);
            if (useOfficialAgentImage) {
                $registryUser.val('');
                $registryPassword.val('');
            }
            $image.blur();
        }).change();

        $registryUser.change(function () {
            $registryPassword.blur();
        }).change();
        $registryPassword.change(function () {
            $registryUser.blur();
        }).change();

        $memoryUnit.change(function () {
            $memory.blur();
        });
    }
}

module.exports = SwarmSchema;
