
const Logger = require('Logger');
const Utils = require('Utils');
const Validators = require('Validators');

function Schema() {

    const $image = Utils.getElt('Image');
    const $useOfficialDockerImage = Utils.getElt('UseOfficialTCAgentImage');
    const $registryUser = Utils.getElt('RegistryUser');
    const $registryPassword = Utils.getElt('RegistryPassword');
    const $swapUnit = Utils.getElt('MemorySwapUnit');
    const $swapUnlimited = Utils.getElt('MemorySwapUnlimited');
    const $memoryUnit = Utils.getElt('MemoryUnit');
    const $memory = Utils.getElt('Memory');

    this.initializeSettings = initializeSettings;
    this.convertViewModelToSettings = convertViewModelToSettings;
    this.convertSettingsToViewModel = convertSettingsToViewModel;
    this.migrateSettings = migrateSettings;

    _initHandlers();

    function initializeSettings(context) {
        return {
            Administration: {
                PullOnCreate: true,
                UseOfficialTCAgentImage: !context.daemonOs || context.daemonOs.toLowerCase() !== 'windows',
                MaxInstanceCount: 2
            },
            Editor: {
                MemoryUnit: 'bytes',
                MemorySwapUnit: 'bytes'
            }
        };
    }

    function _copy(source, target, fieldName, conversionFunction) {
        let value = source[fieldName];
        if (Utils.notEmpty(value)) {
            target[fieldName] =  conversionFunction ? conversionFunction(value) : value;
        }
    }

    function convertViewModelToSettings(viewModel) {
        let settings = {};

        let admin = {};
        let editor = {};

        _copy(viewModel, admin, 'RmOnExit');
        _copy(viewModel, admin, 'PullOnCreate');

        if (Utils.notEmpty(viewModel.MaxInstanceCount)) {
            admin.MaxInstanceCount = parseInt(viewModel.MaxInstanceCount);
        }
        _copy(viewModel, admin, 'UseOfficialTCAgentImage');
        _copy(viewModel, admin, 'RegistryUser');
        _copy(viewModel, admin, 'RegistryPassword', Utils.base64Utf16BEEncode);
        _copy(viewModel, admin, 'Profile');

        if (Utils.notEmpty(admin)) {
            settings.Administration = admin;
        }

        let container = {};
        _copy(viewModel, container, 'Hostname');
        _copy(viewModel, container, 'Domainname');
        _copy(viewModel, container, 'User');

        if (Utils.notEmpty(viewModel.Env)) {
            container.Env = [];
            Utils.safeEach(viewModel.Env, function (envEntry) {
                container.Env.push(envEntry.Name + '=' + envEntry.Value);
            });
        }

        if (Utils.notEmpty(viewModel.Labels)) {
            container.Labels = {};
            Utils.safeEach(viewModel.Labels, function (label) {
                container.Labels[label.Key] = label.Value;
            });
        }

        _copy(viewModel, container, 'Cmd');
        _copy(viewModel, container, 'Entrypoint');
        _copy(viewModel, container, 'Image');

        if (Utils.notEmpty(viewModel.Volumes)) {
            let volumes = {};
            Utils.safeEach(viewModel.Volumes, function (volume) {
                if (!volume.PathOnHost) {
                    volumes[volume.PathInContainer] = {};
                }
            });
            if (Object.keys(volumes).length) {
                container.Volumes = volumes;
            }
        }

        _copy(viewModel, container, 'WorkingDir');

        if (Utils.notEmpty(viewModel.Ports)) {
            let exposedPorts = {};
            Utils.safeEach(viewModel.Ports, function (port) {
                if (!port.HostIp && !port.HostPort) {
                    container.ExposedPorts[port.HostPort + '/' + port.Protocol] = {};
                }
            });
            if (Object.keys(exposedPorts).length) {
                container.ExposedPorts = exposedPorts;
            }
        }

        _copy(viewModel, container, 'StopSignal');

        if (Utils.notEmpty(viewModel.StopTimeout)) {
            container.StopTimeout = parseInt(viewModel.StopTimeout);
        }

        let hostConfig = {};

        if (Utils.notEmpty(viewModel.Volumes)) {
            let editorBinds = [];
            let hostConfigBinds = [];
            Utils.safeEach(viewModel.Volumes, function (volume) {
                if (volume.PathOnHost) {
                    let readOnly = volume.ReadOnly ? 'ro' : 'rw';
                    hostConfigBinds.push(volume.PathOnHost + ':' + volume.PathInContainer + ':' + readOnly);
                    editorBinds.push({ PathOnHost: volume.PathOnHost,
                        PathInContainer: volume.PathInContainer, ReadOnly: readOnly});
                }
            });

            if (editorBinds.length) {
                editor.Binds = editorBinds;
                hostConfig.Binds = hostConfigBinds;
            }
        }

        if (Utils.notEmpty(viewModel.Links)) {
            hostConfig.Links = [];
            Utils.safeEach(viewModel.Links, function (link) {
                hostConfig.Links.push(link.Container + ':' + link.Alias);
            });
        }

        if (Utils.notEmpty(viewModel.Memory)) {
            hostConfig.Memory = parseInt(viewModel.Memory) * Validators.units_multiplier[viewModel.MemoryUnit];
        }

        if (viewModel.MemorySwapUnlimited) {
            hostConfig.MemorySwap = -1;
        } else if (Utils.notEmpty(viewModel.MemorySwap)) {
            hostConfig.MemorySwap = parseInt(viewModel.MemorySwap) * Validators.units_multiplier[viewModel.MemorySwapUnit];
        }
        if (Utils.notEmpty(viewModel.CPUs)) {
            hostConfig.NanoCPUs = Math.floor(parseFloat(viewModel.CPUs) * 1e9);
        }
        if (Utils.notEmpty(viewModel.CpuQuota)) {
            hostConfig.CpuQuota = parseInt(viewModel.CpuQuota);
        }
        if (Utils.notEmpty(viewModel.CpuShares)) {
            hostConfig.CpuShares = parseInt(viewModel.CpuShares);
        }
        if (Utils.notEmpty(viewModel.CpuPeriod)) {
            hostConfig.CpuPeriod = parseInt(viewModel.CpuPeriod);
        }
        _copy(viewModel, hostConfig, 'CpusetCpus');
        _copy(viewModel, hostConfig, 'CpusetMems');
        if (Utils.notEmpty(viewModel.BlkioWeight)) {
            hostConfig.BlkioWeight = parseInt(viewModel.BlkioWeight);
        }
        _copy(viewModel, hostConfig, 'OomKillDisable');

        if (Utils.notEmpty(viewModel.Ports)) {
            let portBindings = {};
            Utils.safeEach(viewModel.Ports, function (port) {
                if (port.HostIp || port.HostPort) {
                    let key = port.ContainerPort + '/' + port.Protocol;
                    let binding = portBindings[key];
                    if (!binding) {
                        binding = portBindings[key] = [];
                    }
                    binding.push({HostIp: port.HostIp, HostPort: port.HostPort});
                }
            });

            if (Object.keys(portBindings).length) {
                hostConfig.PortBindings = portBindings;
            }
        }

        _copy(viewModel, hostConfig, 'PublishAllPorts');
        _copy(viewModel, hostConfig, 'Privileged');
        _copy(viewModel, hostConfig, 'Dns');
        _copy(viewModel, hostConfig, 'DnsSearch');

        if (Utils.notEmpty(viewModel.ExtraHosts)) {
            hostConfig.ExtraHosts = [];
            Utils.safeEach(viewModel.ExtraHosts, function (extraHost) {
                hostConfig.ExtraHosts.push(extraHost.Name + ':' + extraHost.Ip);
            });
        }

        _copy(viewModel, hostConfig, 'CapAdd');
        _copy(viewModel, hostConfig, 'CapDrop');

        if (Utils.notEmpty(viewModel.NetworkMode)) {
            let networkMode = viewModel.NetworkMode;
            if (networkMode === 'bridge' || networkMode === 'host' || networkMode === 'none') {
                hostConfig.NetworkMode = networkMode;
            } else if (networkMode === 'container') {
                hostConfig.NetworkMode = 'container:' + viewModel.NetworkContainer;
            } else if (networkMode) {
                hostConfig.NetworkMode = viewModel.NetworkCustom;
            }
        }

        _copy(viewModel, hostConfig, 'Devices');
        _copy(viewModel, hostConfig, 'Ulimits');

        if (Utils.notEmpty(viewModel.Ulimits)) {
            hostConfig.Ulimits = [];
            Utils.safeEach(viewModel.Ulimits, function (ulimit) {
                hostConfig.Ulimits.push({ Name: ulimit.Name, Hard: parseInt(ulimit.Hard), Soft: parseInt(ulimit.Soft)});
            });
        }

        if (Utils.notEmpty(viewModel.LogType)) {
            let config = {};
            hostConfig.LogConfig = {
                Type: viewModel.LogType
            };

            Utils.safeEach(viewModel.LogConfig, function (logConfig) {
                config[logConfig.Key] = logConfig.Value;
            });

            if (Object.keys(config).length) {
                hostConfig.LogConfig.Config = config;
            }
        }

        _copy(viewModel, hostConfig, 'SecurityOpt');

        if (Utils.notEmpty(viewModel.StorageOpt)) {
            hostConfig.StorageOpt = {};
            Utils.safeEach(viewModel.StorageOpt, function (storageOpt) {
                hostConfig.StorageOpt[storageOpt.Key] = storageOpt.Value;
            });
        }

        _copy(viewModel, hostConfig, 'CgroupParent');
        _copy(viewModel, editor, 'MemoryUnit');
        _copy(viewModel, editor, 'MemorySwapUnit');

        if (Object.keys(hostConfig).length) {
            container.HostConfig = hostConfig;
        }
        if (Object.keys(container).length) {
            settings.Container = container;
        }
        if (Object.keys(editor).length) {
            settings.Editor = editor;
        }

        return settings;
    }

    function convertSettingsToViewModel(settings) {
        let viewModel = {};

        let admin = settings.Administration || {};
        let container = settings.Container || {};
        let hostConfig = container.HostConfig || {};
        let editor = settings.Editor || {};

        _copy(admin, viewModel, 'Profile');
        _copy(admin, viewModel, 'PullOnCreate');
        _copy(admin, viewModel, 'RmOnExit');
        _copy(admin, viewModel, 'MaxInstanceCount');
        _copy(admin, viewModel, 'UseOfficialTCAgentImage');
        _copy(admin, viewModel, 'RegistryUser');

        _copy(admin, viewModel, 'RegistryPassword', Utils.base64Utf16BEDecode);

        _copy(container, viewModel, 'Hostname');
        _copy(container, viewModel, 'Domainname');
        _copy(container, viewModel, 'User');

        let env = [];
        Utils.safeEach(container.Env, function(envEntry) {
            let sepIndex = envEntry.indexOf('=');
            if (sepIndex !== -1) {
                env.push({ Name: envEntry.substring(0, sepIndex), Value: envEntry.substring(sepIndex + 1)});
            }
        });

        if (env.length) {
            viewModel.Env = env;
        }

        _copy(container, viewModel, 'Cmd');
        _copy(container, viewModel, 'Entrypoint');
        _copy(container, viewModel, 'Image');


        let volumes = [];
        Utils.safeKeyValueEach(container.Volumes, function(volume) {
            volumes.push({ PathInContainer: volume });
        });

        _copy(container, viewModel, 'WorkingDir');

        let labels = [];
        Utils.safeKeyValueEach(container.Labels, function(key, value) {
            labels.push({ Key: key, Value: value});
        });
        if (labels.length) {
            viewModel.Labels = labels;
        }

        let ports = [];
        Utils.safeEach(container.ExposedPorts, function(exposedPort) {
            let tokens = exposedPort.split('/');
            ports.push({ ContainerPort: tokens[0], Protocol: tokens[1] })
        });

        _copy(container, viewModel, 'StopSignal');
        _copy(container, viewModel, 'StopTimeout');


        Utils.safeEach(editor.Binds, function(bind) {
            volumes.push({ PathOnHost: bind.PathOnHost, PathInContainer: bind.PathInContainer,  ReadOnly: bind.ReadOnly === 'ro' });
        });
        if (volumes.length) {
            viewModel.Volumes = volumes;
        }

        let links = [];
        Utils.safeEach(hostConfig.Links, function(link) {
            let tokens = link.split(':');
            links.push({ Container: tokens[0], Alias: tokens[1] })
        });
        if (links.length) {
            viewModel.Links = links;
        }

        _copy(editor, viewModel, 'MemoryUnit');

        if (Utils.notEmpty(hostConfig.Memory)) {
            viewModel.Memory = Utils.str(Math.floor(hostConfig.Memory / Validators.units_multiplier[viewModel.MemoryUnit]));
        }

        _copy(editor, viewModel, 'MemorySwapUnit');
        if (Utils.notEmpty(hostConfig.MemorySwap)) {
            if (hostConfig.MemorySwap === -1) {
                viewModel.MemorySwapUnlimited = true;
            } else {
                viewModel.MemorySwap = Utils.str(Math.floor(hostConfig.MemorySwap / Validators.units_multiplier[viewModel.MemorySwapUnit]));
            }
        }

        if (Utils.notEmpty(hostConfig.NanoCPUs)) {
            viewModel.CPUs = Utils.str(hostConfig.NanoCPUs / 1e9);
        }

        _copy(hostConfig, viewModel, 'CpuQuota', Utils.str);
        _copy(hostConfig, viewModel, 'CpuShares', Utils.str);
        _copy(hostConfig, viewModel, 'CpuPeriod', Utils.str);
        _copy(hostConfig, viewModel, 'CpusetCpus');
        _copy(hostConfig, viewModel, 'CpusetMems');
        _copy(hostConfig, viewModel, 'BlkioWeight');
        _copy(hostConfig, viewModel, 'OomKillDisable');

        Utils.safeKeyValueEach(hostConfig.PortBindings, function(port, bindings) {
            let tokens = port.split("/");
            let containerPort = tokens[0];
            let protocol = tokens[1];
            Utils.safeEach(bindings, function(binding) {
                ports.push({ HostIp: binding.HostIp, HostPort: binding.HostPort, ContainerPort: containerPort, Protocol: protocol })
            });
        });

        if (ports.length) {
            viewModel.Ports = ports;
        }

        _copy(hostConfig, viewModel, 'PublishAllPorts');
        _copy(hostConfig, viewModel, 'Privileged');
        _copy(hostConfig, viewModel, 'Dns');
        _copy(hostConfig, viewModel, 'DnsSearch');

        let extraHosts = [];
        Utils.safeEach(hostConfig.ExtraHosts, function(extraHost) {
            let tokens = extraHost.split(':');
            extraHosts.push({ Name: tokens[0], Ip: tokens[1] });
        });
        if (extraHosts.length) {
            viewModel.ExtraHosts = extraHosts;
        }

        _copy(hostConfig, viewModel, 'CapAdd');
        _copy(hostConfig, viewModel, 'CapDrop');

        let networkMode = hostConfig.NetworkMode;
        if (networkMode === "bridge" || networkMode === "host" || networkMode === "none") {
            viewModel.NetworkMode = networkMode;
        } else if (/^container:/.test(networkMode)) {
            viewModel.NetworkMode = "container";
            viewModel.NetworkContainer = networkMode.substring('container:'.length);
        } else if (networkMode) {
            viewModel.NetworkMode = 'custom';
            viewModel.NetworkCustom = networkMode;
        }

        let devices = [];
        Utils.safeEach(hostConfig.Devices, function(device) {
            devices.push(device);
        });
        if (devices.length) {
            viewModel.Devices = devices;
        }

        let ulimits = [];
        Utils.safeEach(hostConfig.Ulimits, function(ulimit) {
            ulimits.push(ulimit);
        });
        if (ulimits.length) {
            viewModel.Ulimits = ulimits;
        }

        let logConfig = hostConfig.LogConfig;
        if (logConfig) {
            viewModel.LogType = logConfig.Type;
            let logConfigProps = [];
            Utils.safeKeyValueEach(logConfig.Config, function (key, value) {
                logConfigProps.push({ Key: key, Value: value});
            });
            if (logConfigProps.length) {
                viewModel.LogConfig = logConfigProps;
            }
        }

        _copy(hostConfig, viewModel, 'SecurityOpt');

        let storageOpt = [];
        Utils.safeKeyValueEach(hostConfig.StorageOpt, function(key, value) {
            storageOpt.push({ Key: key, Value: value});
        });
        if (storageOpt.length) {
            viewModel.StorageOpt = storageOpt;
        }

        _copy(hostConfig, viewModel, 'CgroupParent');

        return viewModel;
    }

    function migrateSettings(imageData) {
        let editor;
        switch(imageData.Administration.Version) {
            case 1:
                // V1: 'Binds' must be exported from the container configuration into the editor configuration,
                // where they will not be stored using the Docker syntax ([host_path]:[container_path]:[mode])
                // but splitted into JSON fields. This allow us to avoid to handle specially with colons in
                // filename for unix (see docker issue #8604, still open today) and windows drive letters
                // (solved in Docker using complexes regexes).
                Logger.logInfo("Performing migration to version 2.");
                let container = imageData.Container || {};
                let hostConfig = container.HostConfig || {};
                editor = imageData.Editor || {};
                imageData.Editor = editor;

                editor.Binds = [];

                Utils.safeEach(hostConfig.Binds, function(bind) {
                    Logger.logDebug("Processing: " + bind);
                    let tokens = bind.split(':');
                    if (tokens.length > 3) {
                        // We are in difficulty as soon as we have more than three tokens: we will then not
                        // evaluate the whole binding definition. This is less crucial for unix file paths,
                        // because the Docker daemon will consider such definition invalid and reject them
                        // anyway.
                        // For Windows file paths, we apply a simple heuristic that should be "good enough":
                        // if a definition token looks like a drive letter then we merge it with the following
                        // token.
                        let copy = tokens.slice();
                        let newTokens = [];
                        let mode = copy.pop();
                        while(copy.length) {
                            let token = copy.shift();
                            if (token.match('^[a-zA-Z0-9]$') && copy.length) {
                                token += ':' + copy.shift();
                            }
                            newTokens.push(token);
                        }
                        if (newTokens.length >= 2 && (mode === 'ro' || mode === 'rw')) {
                            tokens = [newTokens[0], newTokens[1], mode];
                            Logger.logInfo("Binding fix attempt: " + newTokens[0] + ":" + newTokens[1] + ":" + mode);
                        }
                    }
                    editor.Binds.push({ PathOnHost: tokens[0], PathInContainer: tokens[1],  ReadOnly: tokens[2] });
                });
            case 2:
                Logger.logInfo("Performing migration to version 3.");
                imageData.Administration.PullOnCreate = true;
            case 3:
                Logger.logInfo("Performing migration to version 4.");

                editor = imageData.Editor || {};
                let migrationInfo = [];
                imageData.Editor = editor;
                $j([
                    {value: 'Memory', unit: 'MemoryUnit'},
                    {value: 'MemorySwap', unit: 'MemorySwapUnit'}]).each(function(i, mem) {
                    let value;
                    if (imageData.Container && imageData.Container.HostConfig) {
                        value = imageData.Container.HostConfig[mem.value];
                    }
                    let unit = editor[mem.unit];
                    if (unit === 'MiB' || unit === 'GiB') {
                        migrationInfo.push(editor);
                        if (value && value !== -1) {
                            imageData.Container.HostConfig[mem.value] = value * 8;
                        }
                    }
                });
        }
    }

    const validators = {
        Profile: [Validators.requiredValidator, function($elt) {
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
        Image: [Validators.requiredValidator],
        UseOfficialTCAgentImage: [Validators.noWindowsValidator],
        MaxInstanceCount: [Validators.positiveIntegerValidator, function($elt) {
            let value = $elt.val();
            if (value && parseInt(value) < 1) {
                return {msg: "At least one instance must be permitted."};
            }
        }],
        RegistryUser: [function ($elt, context){
            Validators.autoTrim($elt);
            let pass = $registryPassword.val();
            let user = $elt.val();
            if (pass && !user) {
                return {msg: 'Must specify user if password set.'}
            }
        }],
        RegistryPassword: [function ($elt, context){
            let user = $registryUser.val().trim();
            let pass = $elt.val();
            if (user && !pass) {
                return {msg: 'Must specify password if user set.'}
            }
        }],
        StopTimeout: [Validators.positiveIntegerValidator, Validators.versionValidator.bind(this, '1.25')],
        Entrypoint_IDX: [function ($elt) {
            if ($elt.closest("tr").index() === 0) {
                let value = Validators.autoTrim($elt);
                if (!value) {
                    return {msg: "The first entry point argument must point to an executable."};
                }
            }
        }],
        Volumes_IDX_PathInContainer: [Validators.requiredValidator],
        Ports_IDX_HostIp: [Validators.ipv4OrIpv6Validator],
        Ports_IDX_HostPort: [Validators.positiveIntegerValidator, Validators.portNumberValidator],
        Ports_IDX_ContainerPort: [Validators.requiredValidator, Validators.positiveIntegerValidator,
            Validators.portNumberValidator],
        Dns_IDX: [Validators.requiredValidator],
        DnsSearch_IDX: [Validators.requiredValidator],
        ExtraHosts_IDX_Name: [Validators.requiredValidator],
        ExtraHosts_IDX_Ip: [Validators.requiredValidator, Validators.ipv4OrIpv6Validator],
        NetworkCustom: [Validators.requiredValidator],
        NetworkContainer: [Validators.requiredValidator],
        Links_IDX_Container: [Validators.requiredValidator],
        Links_IDX_Alias: [Validators.requiredValidator],
        Ulimits_IDX_Name: [Validators.requiredValidator],
        Ulimits_IDX_Soft: [Validators.requiredValidator, Validators.positiveIntegerValidator],
        Ulimits_IDX_Hard: [Validators.requiredValidator, Validators.positiveIntegerValidator],
        LogConfig_IDX_Key: [Validators.requiredValidator],
        Devices_IDX_PathOnHost: [Validators.requiredValidator],
        Devices_IDX_PathInContainer: [Validators.requiredValidator],
        Env_IDX_Name: [Validators.requiredValidator],
        Labels_IDX_Key: [Validators.requiredValidator],
        SecurityOpt_IDX: [Validators.requiredValidator],
        StorageOpt_IDX_Key: [Validators.requiredValidator],
        Memory: [Validators.positiveIntegerValidator, function ($elt) {
            let value = $elt.val();
            if (!value) {
                return;
            }
            let number = parseInt(value);
            let multiplier = Validators.units_multiplier[$memoryUnit.val()];
            if ((number * multiplier) < 4194304) {
                return {msg: "Memory must be at least 4Mb."}
            }
        }],
        CPUs: [Validators.cpusValidator, Validators.versionValidator.bind(this, '1.25')],
        CpuQuota: [Validators.positiveIntegerValidator, function($elt) {
            let value = $elt.val();
            if (!value) {
                return;
            }
            let number = parseInt(value);
            if (number < 1000) {
                return {msg: "CPU Quota must be at least of 1000μs (1ms)."}
            }
        }],
        CpusetCpus: [Validators.noWindowsValidator, Validators.cpuSetValidator],
        CpusetMems: [Validators.noWindowsValidator, Validators.cpuSetValidator],
        CpuShares: [Validators.positiveIntegerValidator],
        CpuPeriod: [Validators.noWindowsValidator, Validators.positiveIntegerValidator, function($elt) {
            let value = $elt.val();
            if (!value) {
                return;
            }
            let number = parseInt(value);
            if (number < 1000 || number > 1000000) {
                return {msg: "CPU period must be between 1000μs (1ms) and 1000000μs (1s)"}
            }
        }],
        BlkioWeight: [Validators.noWindowsValidator, Validators.positiveIntegerValidator, function ($elt) {
            let value = $elt.val();
            if (!value) {
                return;
            }
            let number = parseInt(value);
            if (number < 10 || number > 1000) {
                return {msg: "IO weight must be between 10 and 1000"}
            }
        }],
        MemorySwap: [Validators.noWindowsValidator, Validators.positiveIntegerValidator,
            function ($elt) {
                let value = $elt.val();
                if (!value) {
                    return;
                }
                if ($swapUnlimited.is(":checked")) {
                    return;
                }
                let memoryVal = $memory.val();
                if (!memoryVal) {
                    return {msg: "Swap limitation can only be used in conjunction with the memory limit."};
                }
                let memory = parseInt(memoryVal);
                if (isNaN(memory)) {
                    return;
                }
                let swap = parseInt(value);
                let memoryUnitMultiplier = Validators.units_multiplier[$memoryUnit.val()];
                let swapUnitMultiplier = Validators.units_multiplier[$swapUnit.val()];
                if (swap * swapUnitMultiplier <= memory * memoryUnitMultiplier) {
                    return {msg: "Swap limit must be strictly greater than the memory limit."}
                }
            }
        ],
        OomKillDisable: [Validators.noWindowsValidator],
        CgroupParent: [Validators.noWindowsValidator]
    };

    const arrayTemplates = {
        imagesTableRow: '<tr class="imagesTableRow"><td class="image_data_Name highlight"></td>' +
        '<td class="maxInstance highlight"></td>' +
        '<td class="reusable highlight"></td>' +
        '<td class="edit highlight"><a href="#/" class="editImageLink">edit</a></td>\
<td class="remove"><a href="#/" class="removeImageLink">delete</a></td>' +
        '</tr>',
            Entrypoint: '<td><input type="text" id="dockerCloudImage_Entrypoint_IDX"/><span class="error" id="dockerCloudImage_Entrypoint_IDX_error"></span></td>',
            CapAdd: '<td><input type="text" id="dockerCloudImage_CapAdd_IDX"/><span class="error" id="dockerCloudImage_CapAdd_IDX_error"></span></td>',
            CapDrop: '<td><input type="text" id="dockerCloudImage_CapDrop_IDX"/><span class="error" id="dockerCloudImage_CapDrop_IDX_error"></span></td>',
            Cmd: '<td><input type="text" id="dockerCloudImage_Cmd_IDX"/></td>',
            Volumes: '<td><input type="text" id="dockerCloudImage_Volumes_IDX_PathOnHost" /></td>\
        <td><input type="text" id="dockerCloudImage_Volumes_IDX_PathInContainer" /><span class="error" id="dockerCloudImage_Volumes_IDX_PathInContainer_error"></span></td>\
        <td class="center"><input type="checkbox" id="dockerCloudImage_Volumes_IDX_ReadOnly" /></td>',
            Devices: '<td><input type="text" id="dockerCloudImage_Devices_IDX_PathOnHost" /></td>\
        <td><input type="text" id="dockerCloudImage_Devices_IDX_PathInContainer" /></td>\
        <td><input type="text" id="dockerCloudImage_Devices_IDX_CgroupPermissions" /></td>',
            Env: '<td><input type="text" id="dockerCloudImage_Env_IDX_Name" /><span class="error" id="dockerCloudImage_Env_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Env_IDX_Value" /></td>',
            Labels: '<td><input type="text" id="dockerCloudImage_Labels_IDX_Key" /><span class="error" id="dockerCloudImage_Labels_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Labels_IDX_Value" /></td>',
            Links: '<td><input type="text" id="dockerCloudImage_Links_IDX_Container" /><span class="error" id="dockerCloudImage_Links_IDX_Container_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Links_IDX_Alias" /><span class="error" id="dockerCloudImage_Links_IDX_Alias_error"></span></td>',
            LogConfig: '<td><input type="text" id="dockerCloudImage_LogConfig_IDX_Key" /><span class="error" id="dockerCloudImage_LogConfig_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_LogConfig_IDX_Value" /></td>',
            SecurityOpt: '<td><input type="text" id="dockerCloudImage_SecurityOpt_IDX"/><span' +
        ' class="error" id="dockerCloudImage_SecurityOpt_IDX_error"></span></td>',
            StorageOpt: '<td><input type="text" id="dockerCloudImage_StorageOpt_IDX_Key" /><span class="error" id="dockerCloudImage_StorageOpt_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_StorageOpt_IDX_Value" /></td>',
            Ulimits: ' <td><input type="text" id="dockerCloudImage_Ulimits_IDX_Name" /><span class="error" id="dockerCloudImage_Ulimits_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Ulimits_IDX_Soft" /><span class="error" id="dockerCloudImage_Ulimits_IDX_Soft_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Ulimits_IDX_Hard" /><span class="error" id="dockerCloudImage_Ulimits_IDX_Hard_error"></span></td>',
            Ports: '<td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_HostIp" />\
                <span class="error" id="dockerCloudImage_Ports_IDX_HostIp_error"></td>\
        <td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_HostPort" size="5"/><span class="error"\
         id="dockerCloudImage_Ports_IDX_HostPort_error"></td>\
        <td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_ContainerPort" size="5"/><span class="error" id="dockerCloudImage_Ports_IDX_ContainerPort_error"></span></td>\
        <td class="center"><select id="dockerCloudImage_Ports_IDX_Protocol">\
            <option value="tcp" selected="selected">tcp</option>\
            <option value="udp">udp</option>\
        </select></td>',
            Dns: '<td><input type="text" id="dockerCloudImage_Dns_IDX"/><span class="error" id="dockerCloudImage_Dns_IDX_error"></span></td>',
            DnsSearch: '<td><input type="text" id="dockerCloudImage_DnsSearch_IDX"/><span class="error" id="dockerCloudImage_DnsSearch_IDX_error"></span></td>',
            ExtraHosts: ' <td><input type="text" id="dockerCloudImage_ExtraHosts_IDX_Name" /><span class="error" id="dockerCloudImage_ExtraHosts_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_ExtraHosts_IDX_Ip" /><span class="error" id="dockerCloudImage_ExtraHosts_IDX_Ip_error"></span></td>'
    };

    this.validators = validators;
    this.arrayTemplates = arrayTemplates;

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

        let networkMode = Utils.getElt('NetworkMode');
        let customNetwork = Utils.getElt('NetworkCustom');
        let containerNetwork = Utils.getElt('NetworkContainer');

        networkMode.change(function () {
            let mode = networkMode.val();

            let container = mode === "container";
            containerNetwork.toggle(container);
            containerNetwork.prop('disabled', !container);
            let custom = mode === "custom";
            customNetwork.toggle(custom);
            customNetwork.prop('disabled', !custom)
            containerNetwork.blur();
            customNetwork.blur();
        }).change();

        let customKernelCap = Utils.getElt('kernel_custom_cap');
        $j('input[name="dockerCloudImage_Capabilities"]').change(function () {
            let radio = $j(this);
            customKernelCap.toggle(radio.is(':checked') && radio.val() === "custom");
        }).change();


        let $swap = Utils.getElt('MemorySwap');

        $memoryUnit.change(function () {
            $memory.blur();
            $swap.blur();
        });
        $memory.change(function () {
            $swap.blur();
        });
        $swapUnit.change(function () {
            $swap.blur();
        });

        $swapUnlimited.change(function() {
            let unlimited = $swapUnlimited.is(":checked");
            $swap.prop("disabled", unlimited);
            $swapUnit.prop("disabled", unlimited);
            if (unlimited) {
                $swap.val("")
            }
            $swap.blur();
        });
    }
}

module.exports = Schema;
