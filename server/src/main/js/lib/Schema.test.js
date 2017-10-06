

const Schema = require('Schema');
const TableHelper = require('TableHelper');
const Utils = require('Utils');
const ValidationHandler = require('ValidationHandler');
const Validators = require('Validators');


const $j = jQuery.noConflict();

describe('Migrating images settings', function () {

    $j('body').load('base/static/image-settings.html');

    const schema = new Schema();

    it('should migrate empty image to latest version', function () {
        let image = {
            Administration: {Version: 1}
        };
        schema.migrateSettings(image);
    });

    it('should ignore images with unknown version', function () {
        let image = {
            Administration: {Version: 999999}
        };
        schema.migrateSettings(image);
        expect(image.Administration.Version).toEqual(999999);
    });

    it('perform volume binding conversion when image V1', function () {
        let hostConfig = {};
        let editor = {};
        let image = {
            Administration: {Version: 1},
            Container: {HostConfig: hostConfig},
            Editor: editor
        };

        hostConfig.Binds = ['/tmp/host_path:/tmp/container_path:rw', '/tmp/host_path2:volume:ro'];

        schema.migrateSettings(image);

        expect(editor.Binds).toEqual([
            {PathOnHost: '/tmp/host_path', PathInContainer: '/tmp/container_path', ReadOnly: 'rw'},
            {PathOnHost: '/tmp/host_path2', PathInContainer: 'volume', ReadOnly: 'ro'}
        ]);

        image = {
            Administration: {Version: 1},
            Container: {HostConfig: hostConfig},
            Editor: editor
        };

        hostConfig.Binds = ['C:\\host_path:C:\\container_path:rw', 'C:\\host_path:volume:ro'];

        schema.migrateSettings(image);

        expect(editor.Binds).toEqual([
            {PathOnHost: 'C:\\host_path', PathInContainer: 'C:\\container_path', ReadOnly: 'rw'},
            {PathOnHost: 'C:\\host_path', PathInContainer: 'volume', ReadOnly: 'ro'}
        ]);
    });

    it('should init default PullOnCreate flag when image V2', function () {
        let image = {
            Administration: { Version: 2 }
        };

        schema.migrateSettings(image);

        expect(image.Administration.PullOnCreate).toEqual(true);

        image.Administration.PullOnCreate = false;

        image = {
            Administration: { Version: 3, PullOnCreate: false }
        };

        schema.migrateSettings(image);

        expect(image.Administration.PullOnCreate).toEqual(false);
    });

    it('should fix memory unit when image V3', function () {

        let legacyUnitFactors = {
            GiB: 134217728,
            MiB: 131072,
            bytes: 1
        };

        let base = {Administration: {Version: 3}};
        let settings = function (settings) {
            return $j.extend(true, settings, base);
        };

        let imageData = settings({});

        schema.migrateSettings(imageData);

        expect(imageData).toEqual({Administration: {Version: 3}, Editor:{}});

        let memValues = [
            { value: 'MemorySwap', unit: 'MemorySwapUnit' },
            { value: 'Memory', unit: 'MemoryUnit'}];
        memValues.forEach(function (memValue) {
            Object.keys(legacyUnitFactors).forEach(function (unit) {
                let hostConfig;
                let editor;
                [1, 2, 3].forEach(function(value) {
                    let factor = legacyUnitFactors[unit];
                    hostConfig = {};
                    editor = {};
                    hostConfig[memValue.value] = value * factor;
                    editor[memValue.unit] = unit;
                    let imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                    schema.migrateSettings(imageData);

                    expect(hostConfig[memValue.value]).toEqual(value * Validators.units_multiplier[unit]);
                });

                hostConfig = {};
                editor = {};
                hostConfig[memValue.value] = -1;
                editor[memValue.unit] = unit;

                let imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                schema.migrateSettings(imageData);

                expect(hostConfig[memValue.value]).toEqual(-1);

                hostConfig = {};
                editor = {};
                hostConfig[memValue.value] = -1;

                imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                schema.migrateSettings(imageData);

                expect(hostConfig[memValue.value]).toEqual(-1);
            });
        });
    });
});

let settingsConverterFixtures = [
    {
        name: 'should handle Profile', fixtures: [{
        settings: {Administration: {Profile: 'test'}}, viewModel: {Profile: 'test'}
    }]
    },
    {
        name: 'should handle RmOnExit flag', fixtures: [
        {settings: {Administration: {RmOnExit: true}}, viewModel: {RmOnExit: true}},
        {settings: {Administration: {RmOnExit: false}}, viewModel: {RmOnExit: false}}]
    },
    {
        name: 'should handle PullOnCreate flag', fixtures: [
        {settings: {Administration: {PullOnCreate: true}}, viewModel: {PullOnCreate: true}},
        {settings: {Administration: {PullOnCreate: false}}, viewModel: {PullOnCreate: false}}
    ]
    },
    {
        name: 'should handle MaxInstanceCount', fixtures: [{
        settings: {Administration: {MaxInstanceCount: 42}}, viewModel: {MaxInstanceCount: 42}
    }]
    },
    {
        name: 'should handle UseOfficialTCAgentImage flag', fixtures: [{
        settings: {Administration: {UseOfficialTCAgentImage: true}}, viewModel: {UseOfficialTCAgentImage: true}
    }]
    },
    {
        name: 'should handle RegistryUser', fixtures: [{
        settings: {Administration: {RegistryUser: 'test'}}, viewModel: {RegistryUser: 'test'}
    }]
    },
    {
        name: 'should handle RegistryPassword using UTF-16BE encoded in base64', fixtures: [{
        settings: {Administration: {RegistryPassword: 'AHQAZQBzAHQAXwDpAODYAdw3'}},
        viewModel: {RegistryPassword: 'test_éà𐐷'}
    }]
    },
    {
        name: 'should handle Hostname', fixtures: [{
        settings: {Container: {Hostname: 'localhost'}}, viewModel: {Hostname: 'localhost'}
    }]
    },
    {
        name: 'should handle Domainname', fixtures: [{
        settings: {Container: {Domainname: 'test.com'}},
        viewModel: {Domainname: 'test.com'}
    }]
    },
    {
        name: 'should handle User', fixtures: [{
        settings: {Container: {User: 'test'}}, viewModel: {User: 'test'}
    }]
    },
    {
        name: 'should handle WorkingDir', fixtures: [{
        settings: {Container: {WorkingDir: '/root'}}, viewModel: {WorkingDir: '/root'}
    }]
    },
    {
        name: 'should handle StopTimeout', fixtures: [{
        settings: {Container: {StopTimeout: 42}}, viewModel: {StopTimeout: 42}
    }]
    },
    {
        name: 'should handle StopSignal', fixtures: [{
        settings: {Container: {StopSignal: 'SIGKILL'}}, viewModel: {StopSignal: 'SIGKILL'}
    }]
    },
    {
        name: 'should handle Env', fixtures: [{
        settings: {Container: {Env: ['var1=value1', 'var2=value2']}},
        viewModel: {Env: [{Name: 'var1', Value: 'value1'}, {Name: 'var2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle Cmd', fixtures: [{
        settings: {Container: {Cmd: ['/usr/bin/test', 'arg1']}},
        viewModel: {Cmd: ['/usr/bin/test', 'arg1']}
    }]
    },
    {
        name: 'should handle Entrypoint', fixtures: [{
        settings: {Container: {Entrypoint: ['/usr/bin/test', 'arg1']}},
        viewModel: {Entrypoint: ['/usr/bin/test', 'arg1']}
    }]
    },
    {
        name: 'should handle Image', fixtures: [{
        settings: {Container: {Image: 'jetbrains/teamcity-agent:9.99'}},
        viewModel: {Image: 'jetbrains/teamcity-agent:9.99'}
    }]
    },
    {
        name: 'should handle Labels', fixtures: [{
        settings: {Container: {Labels: {key1: 'value1', key2: 'value2'}}},
        viewModel: {Labels: [{Key: 'key1', Value: 'value1'}, {Key: 'key2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle Volumes', fixtures: [{
        settings: {Container: {Volumes: {'/tmp/container_path1': {}, '/tmp/container_path2': {}}}},
        viewModel: {Volumes: [{PathInContainer: '/tmp/container_path1'}, {PathInContainer: '/tmp/container_path2'}]}
    }]
    },
    {
        name: 'should handle Binds', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    Binds: ['/tmp/host_path1:/tmp/container_path:ro', 'C:\\host_path2:volume:rw']
                }
            },
            Editor: {
                Binds: [
                    {PathOnHost: '/tmp/host_path1', PathInContainer: '/tmp/container_path', ReadOnly: 'ro'},
                    {PathOnHost: 'C:\\host_path2', PathInContainer: 'volume', ReadOnly: 'rw'}
                ]
            }
        },
        viewModel: {
            Volumes: [{PathOnHost: '/tmp/host_path1', PathInContainer: '/tmp/container_path', ReadOnly: true},
                {PathOnHost: 'C:\\host_path2', PathInContainer: 'volume', ReadOnly: false}
            ]
        }
    }]
    },
    {
        name: 'should handle Links', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    Links: ['container_name1:alias1', 'container_name2:alias2']
                }
            }
        }, viewModel: {
            Links: [{Container: 'container_name1', Alias: 'alias1'},
                {Container: 'container_name2', Alias: 'alias2'}]
        }
    }]
    },
    {
        name: 'should handle Memory', fixtures: [
        {
            settings: {Container: {HostConfig: {Memory: 1}}, Editor: {MemoryUnit: 'bytes'}},
            viewModel: {Memory: '1', MemoryUnit: 'bytes'}
        },
        {
            settings: {Container: {HostConfig: {Memory: 1048576}}, Editor: {MemoryUnit: 'MiB'}},
            viewModel: {Memory: '1', MemoryUnit: 'MiB'}
        },
        {
            settings: {Container: {HostConfig: {Memory: 10737418240}}, Editor: {MemoryUnit: 'GiB'}},
            viewModel: {Memory: '10', MemoryUnit: 'GiB'}
        }
    ]
    },
    {
        name: 'should handle MemorySwap', fixtures: [
        {
            settings: {Container: {HostConfig: {MemorySwap: 1}}, Editor: {MemorySwapUnit: 'bytes'}},
            viewModel: {MemorySwap: '1', MemorySwapUnit: 'bytes'}
        },
        {
            settings: {Container: {HostConfig: {MemorySwap: 1048576}}, Editor: {MemorySwapUnit: 'MiB'}},
            viewModel: {MemorySwap: '1', MemorySwapUnit: 'MiB'}
        }
    ]
    },
    {
        name: 'should handle NanoCPUs', fixtures: [{
        settings: {Container: {HostConfig: {NanoCPUs: 1500000000}}},
        viewModel: {CPUs: '1.5'}
    }]
    },
    {
        name: 'should handle CpuQuota', fixtures: [{
        settings: {Container: {HostConfig: {CpuQuota: 42}}},
        viewModel: {CpuQuota: '42'}
    }]
    },
    {
        name: 'should handle CpuShares', fixtures: [{
        settings: {Container: {HostConfig: {CpuShares: 42}}},
        viewModel: {CpuShares: '42'}
    }]
    },
    {
        name: 'should handle CpuPeriod', fixtures: [{
        settings: {Container: {HostConfig: {CpuPeriod: 42}}},
        viewModel: {CpuPeriod: '42'}
    }]
    },
    {
        name: 'should handle CpusetCpus', fixtures: [{
        settings: {Container: {HostConfig: {CpusetCpus: '1-2,3-4'}}},
        viewModel: {CpusetCpus: '1-2,3-4'}
    }]
    },
    {
        name: 'should handle CpusetMems', fixtures: [{
        settings: {Container: {HostConfig: {CpusetMems: '1-2,3-4'}}},
        viewModel: {CpusetMems: '1-2,3-4'}
    }]
    },
    {
        name: 'should handle OomKillDisable', fixtures: [{
        settings: {Container: {HostConfig: {OomKillDisable: true}}},
        viewModel: {OomKillDisable: true}
    },
        {
            settings: {Container: {HostConfig: {OomKillDisable: false}}},
            viewModel: {OomKillDisable: false}
        }]
    },
    {
        name: 'should handle Ports', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    PortBindings: {
                        '8080/tcp': [{HostIp: '127.0.0.1', HostPort: '80'}],
                        '2525/udp': [{HostIp: '192.168.100.1', HostPort: '25'},
                            {HostIp: '192.168.100.2', HostPort: '26'}]
                    }
                }
            }
        }, viewModel: {
            Ports: [
                {
                    HostIp: '127.0.0.1',
                    HostPort: '80',
                    ContainerPort: '8080',
                    Protocol: 'tcp'
                },
                {
                    HostIp: '192.168.100.1',
                    HostPort: '25',
                    ContainerPort: '2525',
                    Protocol: 'udp'
                },
                {
                    HostIp: '192.168.100.2',
                    HostPort: '26',
                    ContainerPort: '2525',
                    Protocol: 'udp'
                }
            ]
        }
    }]
    },
    {
        name: 'should handle PublishAllPorts', fixtures: [
        {
            settings: {Container: {HostConfig: {PublishAllPorts: true}}},
            viewModel: {PublishAllPorts: true}
        },
        {
            settings: {Container: {HostConfig: {PublishAllPorts: false}}},
            viewModel: {PublishAllPorts: false}
        }]
    },
    {
        name: 'should handle Privileged flag', fixtures: [
        {
            settings: {Container: {HostConfig: {Privileged: true}}},
            viewModel: {Privileged: true}
        },
        {
            settings: {Container: {HostConfig: {Privileged: false}}},
            viewModel: {Privileged: false}
        }]
    },
    {
        name: 'should handle Dns', fixtures: [{
        settings: {Container: {HostConfig: {Dns: ['4.4.4.4', '8.8.8.8']}}},
        viewModel: {Dns: ['4.4.4.4', '8.8.8.8']}
    }]
    },
    {
        name: 'should handle DnsSearch', fixtures: [{
        settings: {Container: {HostConfig: {DnsSearch: ['domain1.com', 'domain2.com']}}},
        viewModel: {DnsSearch: ['domain1.com', 'domain2.com']}
    }]
    },
    {
        name: 'should handle ExtraHosts', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    ExtraHosts: ['host1:1.1.1.1', 'host2:2.2.2.2']
                }
            }
        }, viewModel: {
            ExtraHosts: [{Name: 'host1', Ip: '1.1.1.1'}, {Name: 'host2', Ip: '2.2.2.2'}]
        }
    }]
    },
    {
        name: 'should handle CapAdd', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    CapAdd: ['SETPCAP', 'MKNOD']
                }
            }
        }, viewModel: {
            CapAdd: ['SETPCAP', 'MKNOD']
        }
    }]
    },
    {
        name: 'should handle CapDrop', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    CapDrop: ['SETPCAP', 'MKNOD']
                }
            }
        }, viewModel: {
            CapDrop: ['SETPCAP', 'MKNOD']
        }
    }]
    },
    {
        name: 'should handle NetworkMode', fixtures: [
        {
            settings: {Container: {HostConfig: {NetworkMode: 'bridge'}}},
            viewModel: {NetworkMode: 'bridge'}
        },
        {
            settings: {Container: {HostConfig: {NetworkMode: 'host'}}},
            viewModel: {NetworkMode: 'host'}
        },
        {
            settings: {Container: {HostConfig: {NetworkMode: 'none'}}},
            viewModel: {NetworkMode: 'none'}
        },
        {
            settings: {Container: {HostConfig: {NetworkMode: 'container:container_name'}}},
            viewModel: {NetworkMode: 'container', NetworkContainer: 'container_name'}
        },
        {
            settings: {Container: {HostConfig: {NetworkMode: 'my_custom_net'}}},
            viewModel: {NetworkMode: 'custom', NetworkCustom: 'my_custom_net'}
        }]
    },
    {
        name: 'should handle Devices', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    Devices: [
                        {PathOnHost: '/dev/sda1', PathInContainer: '/dev/sdb1', CgroupPermissions: 'rwm'},
                        {PathOnHost: '/dev/sda2', PathInContainer: '/dev/sdb2', CgroupPermissions: 'r'}]
                }
            }
        },
        viewModel: {
            Devices: [
                {PathOnHost: '/dev/sda1', PathInContainer: '/dev/sdb1', CgroupPermissions: 'rwm'},
                {PathOnHost: '/dev/sda2', PathInContainer: '/dev/sdb2', CgroupPermissions: 'r'}
            ]
        }
    }]
    },
    {
        name: 'should handle Ulimits', fixtures: [{
        settings: {
            Container: {
                HostConfig: {
                    Devices: [
                        {Name: 'nofile', Soft: '123', Hard: '456'},
                        {Name: 'memlock', Soft: '789', Hard: '012'}]
                }
            }
        },
        viewModel: {
            Devices: [
                {Name: 'nofile', Soft: '123', Hard: '456'},
                {Name: 'memlock', Soft: '789', Hard: '012'}
            ]
        }
    }]
    },
    {
        name: 'should handle LogType',
        fixtures: [
            {
                settings: {Container: {HostConfig: {LogConfig: {Type: 'none'}}}},
                viewModel: {LogType: 'none'}
            },
            {
                settings: {
                    Container: {
                        HostConfig: {
                            LogConfig: {
                                Type: 'json-file',
                                Config: {'max-size': '10m', 'max-file': '10'}
                            }
                        }
                    }
                },
                viewModel: {
                    LogType: 'json-file', LogConfig: [
                        {Key: 'max-size', Value: '10m'},
                        {Key: 'max-file', Value: '10'}
                    ]
                }
            }
        ]
    },
    {
        name: 'should handle SecurityOpt', fixtures: [{
        settings: {
            Container: {
                HostConfig:{
                    SecurityOpt: ['label 1', 'label 2', 'label 3']
                }
            }
        },
        viewModel: {SecurityOpt: ['label 1', 'label 2', 'label 3']}
    }]
    },
    {
        name: 'should handle StorageOpt', fixtures: [{
        settings: {
            Container: {
                HostConfig:{
                    StorageOpt: {key1: 'value1', key2: 'value2'}
                }
            }
        },
        viewModel: {StorageOpt: [{Key: 'key1', Value: 'value1'}, {Key: 'key2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle CgroupParent', fixtures: [{
        settings: {Container: {HostConfig: {CgroupParent: '/docker'}}},
        viewModel: {CgroupParent: '/docker'}
    }]
    }
];

describe('Converting view model to settings', function () {

    const schema = new Schema();

    const base = { };
    let settings = function (settings) {
        return $j.extend(true, settings, base);
    };

    it('should create base settings for empty view model', function () {
        expect(schema.convertViewModelToSettings({})).toEqual(base);
    });


    settingsConverterFixtures.forEach(function (test) {
        it(test.name, function () {
            test.fixtures.forEach(function (fixture) {
                expect(schema.convertViewModelToSettings(fixture.viewModel))
                    .toEqual(settings(fixture.settings));
            });
        });
    });
});

describe('Converting settings to view model', function () {

    const schema = new Schema();

    const base = {Administration: {Version: 99}};
    const settings = function (settings) {
        return $j.extend(true, settings, base);
    };

    it('should create empty view model for empty settings', function () {
        expect(schema.convertSettingsToViewModel({})).toEqual({});
    });

    settingsConverterFixtures.forEach(function (test) {
        it(test.name, function () {
            test.fixtures.forEach(function (fixture) {
                expect(schema.convertSettingsToViewModel(settings(fixture.settings)))
                    .toEqual(fixture.viewModel);
            });
        });
    });
});

describe('Validators', function() {

    let schema;
    let validationHandler;
    let tableHelper;

    const emptyTemplates = {
        deleteCell: '',
        settingsCell: '<span></span></a>',
        insertAddButton: function() {}
    };

    beforeEach(function() {
        $j('body').load('base/static/image-settings.html');

        schema = new Schema();
        validationHandler = new ValidationHandler(schema.validators);
        tableHelper = new TableHelper(emptyTemplates, schema.arrayTemplates);
    });

    let Table = function(id) {
        this.addRow = function() {
            let $tbody = $j('#dockerCloudImage_' + id);
            tableHelper.addTableRow($tbody);
            return new Row($j.data($tbody.get(0), "index"), id);
        }
    };

    let Row = function(index, eltId) {
        this.singleField = function() {
            return { $elt: $j('#dockerCloudImage_' + eltId + '_' + index), eltId: eltId + '_IDX' };
        };
        this.field = function(name){
            return { $elt: $j('#dockerCloudImage_' + eltId + '_' + index + '_' + name),
                eltId: eltId + '_IDX_' + name };
        };
    };

    let loadElt = function(id) {
        let effectiveId = 'dockerCloudImage_' + id;
        let $elt = $j('#' + effectiveId);
        expect($elt.length).toEqual(1);
        return { $elt: $elt, eltId: id }
    };

    let verifyOk = function(eltHandle, value, context) {
        context = context || {};
        eltHandle.$elt.val(value);
        let result = validationHandler.validate(eltHandle.$elt, eltHandle.eltId, context);
        expect(result).toEqual({ error: null, warnings: [] });
    };

    let verifySingleWarn = function(eltHandle, value, context) {
        eltHandle.$elt.val(value);
        let result = validationHandler.validate(eltHandle.$elt, eltHandle.eltId, context);
        expect(result.error).toEqual(null);
        expect(result.warnings.length).toEqual(1);
    };

    let verifyFailure = function(eltHandle, value, context) {
        context = context || {};
        eltHandle.$elt.val(value);
        let result = validationHandler.validate(eltHandle.$elt, eltHandle.eltId, context);
        expect(result.error).not.toEqual(null);
    };

    let verifyMandatory = function(eltHandle, context) {
        verifyAutoTrim(eltHandle, context);
        verifyFailure(eltHandle, '', context);
        verifyFailure(eltHandle, '  ', context);
        verifyFailure(eltHandle, '\t', context);
    };

    let verifyMinApi1_25 = function(eltHandle, value) {
        verifyOk(eltHandle, '');

        verifyOk(eltHandle, value);

        let context = {};

        context.effectiveApiVersion = '1.25';
        verifyOk(eltHandle, value, context);

        context.effectiveApiVersion = '1.26';
        verifyOk(eltHandle, value, context);

        context.effectiveApiVersion = '1.24';
        verifySingleWarn(eltHandle, value, context);
    };

    let verifyNoWindows = function(eltHandle, value) {

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
    };

    let verifyAutoTrim = function(eltHandle, context) {
        context = context || {};
        validationHandler.validate(eltHandle.$elt.val('  '), eltHandle.eltId, context);
        expect(eltHandle.$elt.val()).toEqual('');
        validationHandler.validate(eltHandle.$elt.val('\t'), eltHandle.eltId, context);
        expect(eltHandle.$elt.val()).toEqual('');
        validationHandler.validate(eltHandle.$elt.val(' abc\t'), eltHandle.eltId, context);
        expect(eltHandle.$elt.val()).toEqual('abc');
    };

    let verifyPortNumber = function(eltHandle) {
        verifyInteger(eltHandle, 1, 65535);
    };

    let verifyInteger = function(eltHandle, lowerBound, upperBound) {
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
    };

    let verifyIPAddress = function(eltHandle) {
        verifyAutoTrim(eltHandle);
        verifyOk(eltHandle, '127.0.0.1');
        verifyOk(eltHandle, '0000:0000:0000:0000:0000:0000:0000:0001');
        verifyOk(eltHandle, 'aced::a11:7e57');
        verifyOk(eltHandle, '::1');
        verifyFailure(eltHandle, 'hostname');
    };

    let verifyCpuSet = function(eltHandle) {
        verifyAutoTrim(eltHandle);
        verifyOk(eltHandle, '');
        verifyOk(eltHandle, '0,1,2');
        verifyOk(eltHandle, '0-1');
        verifyOk(eltHandle, '0-1,2-3,4-5');
        verifyFailure(eltHandle, '0-1, 2-3,4-5');
        verifyFailure(eltHandle, '-1');
        verifyFailure(eltHandle, '0-a');
        verifyNoWindows(eltHandle, 0);
    };

    it('should perform profile name validation', function() {
        // New profile

        let imagesData = {};
        let context = {
          getImagesData: function() {
              return imagesData;
          }
        };

        let eltHandle = loadElt('Profile');
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
    });

    it('should perform official agent image checkbox validation', function() {
        verifyNoWindows(loadElt('UseOfficialTCAgentImage'));
    });

    it('should perform image name validation', function() {
        let eltHandle = loadElt('Image');

        verifyOk(eltHandle, 'image:1.0');
        verifyMandatory(eltHandle);
    });

    it('should perform max instance count validation', function() {
        let eltHandle = loadElt('MaxInstanceCount');

        verifyInteger(eltHandle, 1);
    });

    it('should perform registry user and password validation', function() {
        let userEltHandle = loadElt('RegistryUser');
        let pwdEltHandle = loadElt('RegistryPassword');

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
    });

    it('should perform stop timeout validation', function() {
        let eltHandle = loadElt('StopTimeout');

        verifyInteger(eltHandle);
        verifyMinApi1_25(eltHandle, '2');
    });

    it('should perform entrypoint validation', function() {
        let table = new Table('Entrypoint');
        let eltHandle = table.addRow().singleField();
        // First entry is mandatory.
        verifyMandatory(eltHandle);
        verifyOk(eltHandle, '/usr/bin');
        eltHandle = table.addRow().singleField();
        // Subsequent entries are optional.
        verifyOk(eltHandle, '');
    });

    it('should perform volumes validation', function() {
        let tableRow = new Table('Volumes').addRow();
        verifyMandatory(tableRow.field('PathInContainer'));
        verifyOk(tableRow.field('PathOnHost'), '');
    });

    it('should perform port binding validation', function() {
        let tableRow = new Table('Ports').addRow();
        let containerPort = tableRow.field('ContainerPort');
        verifyPortNumber(containerPort);
        verifyMandatory(containerPort);
        verifyPortNumber(tableRow.field('HostPort'));
        verifyIPAddress(tableRow.field('HostIp'));
    });

    it('should perform DNS validation', function() {
        verifyMandatory(new Table('Dns').addRow().singleField());
    });

    it('should perform search DNS validation', function() {
        verifyMandatory(new Table('DnsSearch').addRow().singleField());
    });

    it('should perform extra hosts validation', function() {
        let tableRow = new Table('ExtraHosts').addRow();
        verifyMandatory(tableRow.field('Name'));
        let ip = tableRow.field('Ip');
        verifyMandatory(ip);
        verifyIPAddress(ip);
    });

    it('should perform custom network validation', function() {
        verifyMandatory(loadElt('NetworkCustom'));
    });

    it('should perform container network validation', function() {
        verifyMandatory(loadElt('NetworkContainer'));
    });

    it('should perform network links validation', function() {
        let tableRow = new Table('Links').addRow();
        verifyMandatory(tableRow.field('Container'));
        verifyMandatory(tableRow.field('Alias'));
    });

    it('should perform ulimits validation', function() {
        let tableRow = new Table('Ulimits').addRow();
        verifyMandatory(tableRow.field('Name'));
        let softLimit = tableRow.field('Soft');
        verifyMandatory(softLimit);
        verifyInteger(softLimit);
        let hardLimit = tableRow.field('Hard');
        verifyMandatory(hardLimit);
        verifyInteger(hardLimit);
    });

    it('should perform logging configuration validation', function() {
        let tableRow = new Table('LogConfig').addRow();
        verifyMandatory(tableRow.field('Key'));
        verifyOk(tableRow.field('Value'), '');
    });

    it('should perform security options validation', function() {
        verifyMandatory(new Table('SecurityOpt').addRow().singleField());
    });

    it('should perform storage options validation', function() {
        let tableRow = new Table('StorageOpt').addRow();
        verifyMandatory(tableRow.field('Key'));
        verifyOk(tableRow.field('Value'), '');
    });

    it('should perform devices validation', function() {
        let tableRow = new Table('Devices').addRow();
        verifyMandatory(tableRow.field('PathOnHost'));
        verifyMandatory(tableRow.field('PathInContainer'));
        verifyOk(tableRow.field('CgroupPermissions'));
    });

    it('should perform env variables validation', function() {
        let tableRow = new Table('Env').addRow();
        verifyMandatory(tableRow.field('Name'));
        verifyOk(tableRow.field('Value'));
    });

    it('should perform labels validation', function() {
        let tableRow = new Table('Labels').addRow();
        verifyMandatory(tableRow.field('Key'));
        verifyOk(tableRow.field('Value'));
    });

    it('should perform memory validation', function() {
        let eltHandle = loadElt('Memory');
        // Test 4MiB limit
        const $memoryUnit = Utils.getElt('MemoryUnit').val('bytes');
        verifyInteger(eltHandle, 4194304);
        $memoryUnit.val('KiB');
        verifyInteger(eltHandle, 4096);
        $memoryUnit.val('MiB');
        verifyInteger(eltHandle, 4);
    });

    it('should perform cpus count validation', function() {
        let eltHandle = loadElt('CPUs');
        verifyOk(eltHandle, '');
        verifyFailure(eltHandle, -1);
        verifyOk(eltHandle, 0);
        expect(eltHandle.$elt.val()).toEqual('0');
        verifyOk(eltHandle, '0.0');
        expect(eltHandle.$elt.val()).toEqual('0.0');
        verifyOk(eltHandle, '00.0');
        expect(eltHandle.$elt.val()).toEqual('0.0');
        verifyOk(eltHandle, 1.1234);
        expect(eltHandle.$elt.val()).toEqual('1.1234');
        verifyOk(eltHandle, '0001.1234');
        expect(eltHandle.$elt.val()).toEqual('1.1234');

        // Upper bound
        verifyOk(eltHandle, 9000000000);
        verifyFailure(eltHandle, 9100000000);
        // Max value precision.
        verifyOk(eltHandle, 1.900000001);
        verifyFailure(eltHandle, 1.9000000001);
        verifyMinApi1_25(eltHandle, 0);
    });

    it('should perform CPU quota validation', function() {
        let eltHandle = loadElt('CpuQuota');
        verifyInteger(eltHandle, 1000);
        verifyOk(eltHandle, '');
    });

    it ('should perform cpuset-cpus validation', function () {
        verifyCpuSet(loadElt('CpusetCpus'));
    });

    it('should perform cpuset-mems validation', function() {
        verifyCpuSet(loadElt('CpusetMems'));
    });

    it('should perform CPU shares validation', function() {
        verifyInteger(loadElt('CpuShares'));
    });

    it('should perform CPU period validation', function() {
        let eltHandle = loadElt('CpuPeriod');
        verifyInteger(eltHandle, 1000, 1000000);
        verifyNoWindows(eltHandle, 1000);
    });

    it('should perform Blkio-weight validation', function() {
        let eltHandle = loadElt('BlkioWeight');
        verifyInteger(eltHandle, 10, 1000);
        verifyNoWindows(eltHandle, 10);
    });

    it('should perform swap validation', function() {

        const $swapUnit = Utils.getElt('MemorySwapUnit').val('bytes');
        const $swapUnlimited = Utils.getElt('MemorySwapUnlimited');
        const $memoryUnit = Utils.getElt('MemoryUnit').val('bytes');
        const $memory = Utils.getElt('Memory');

        let eltHandle = loadElt('MemorySwap');

        // Swap without memory is illegal.
        verifyOk(eltHandle, '');
        verifyFailure(eltHandle, 1);

        // Swap must be greater or equal than memory.
        $memory.val(100);
        verifyFailure(eltHandle, 100);
        verifyOk(eltHandle, 101);

        $swapUnlimited.prop('checked', true);
        // Skip validation if swap unlimited.
        verifyOk(eltHandle, 100);
        $swapUnlimited.prop('checked', false);

        // Cross units comparison
        $memoryUnit.val('MiB');
        $memory.val(1);
        $swapUnit.val('KiB');
        verifyFailure(eltHandle, 1024);
        verifyOk(eltHandle, 1025);

        verifyNoWindows(eltHandle, 99999999);
    });

    it('should perform CgroupParent validation', function() {
        verifyNoWindows(loadElt('CgroupParent'), 'system.slice');
    });

    it('should perform disabled OOM-killer validation', function() {
        verifyNoWindows(loadElt('OomKillDisable'));
    });
});

describe('initializeSettings', function() {

    let schema;

    beforeEach(function() {
        $j('body').load('base/static/image-settings.html');

        schema = new Schema();
    });

    it('should convert to view model and backward', function() {
        let settings = {};
        schema.initializeSettings(settings);
        let viewModel = schema.convertSettingsToViewModel(settings);
        expect(schema.convertViewModelToSettings(viewModel)).toEqual(settings);
    });

    it('should set official image flag according to daemon OS', function() {
        let context = {};

        context.daemonOs = undefined;
        expect(schema.initializeSettings(context).Administration.UseOfficialTCAgentImage).toEqual(true);

        context.daemonOs = 'linux';
        expect(schema.initializeSettings(context).Administration.UseOfficialTCAgentImage).toEqual(true);

        context.daemonOs = 'windows';
        expect(schema.initializeSettings(context).Administration.UseOfficialTCAgentImage).toEqual(false);
    });
});