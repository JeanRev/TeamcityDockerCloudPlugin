

const Schema = require('Schema');
const Utils = require('Utils');
const ValidationHandler = require('ValidationHandler');
const Validators = require('Validators');
const ValidatorTester = require('Validators.test');

describe('Migrating images settings', function () {

    let schema;

    beforeEach(function() {
        schema = new Schema();

        $j('body').html(schema.html);
    });

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

    it('should rename Container object when image V4', function () {
        let imageData = {
            Administration: { Version: 4 }
        };

        schema.migrateSettings(imageData);

        expect(imageData).toEqual({Administration: {Version: 4}});

        imageData = {
            Administration: { Version: 4 },
            Container: { foo: 'bar' }
        };

        schema.migrateSettings(imageData);

        expect(imageData).toEqual({Administration: {Version: 4}, AgentHolderSpec: { foo: 'bar' }});
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
        settings: {AgentHolderSpec: {Hostname: 'localhost'}}, viewModel: {Hostname: 'localhost'}
    }]
    },
    {
        name: 'should handle Domainname', fixtures: [{
        settings: {AgentHolderSpec: {Domainname: 'test.com'}},
        viewModel: {Domainname: 'test.com'}
    }]
    },
    {
        name: 'should handle User', fixtures: [{
        settings: {AgentHolderSpec: {User: 'test'}}, viewModel: {User: 'test'}
    }]
    },
    {
        name: 'should handle WorkingDir', fixtures: [{
        settings: {AgentHolderSpec: {WorkingDir: '/root'}}, viewModel: {WorkingDir: '/root'}
    }]
    },
    {
        name: 'should handle StopTimeout', fixtures: [{
        settings: {AgentHolderSpec: {StopTimeout: 42}}, viewModel: {StopTimeout: 42}
    }]
    },
    {
        name: 'should handle StopSignal', fixtures: [{
        settings: {AgentHolderSpec: {StopSignal: 'SIGKILL'}}, viewModel: {StopSignal: 'SIGKILL'}
    }]
    },
    {
        name: 'should handle Env', fixtures: [{
        settings: {AgentHolderSpec: {Env: ['var1=value1', 'var2=value2']}},
        viewModel: {Env: [{Name: 'var1', Value: 'value1'}, {Name: 'var2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle Cmd', fixtures: [{
        settings: {AgentHolderSpec: {Cmd: ['/usr/bin/test', 'arg1']}},
        viewModel: {Cmd: ['/usr/bin/test', 'arg1']}
    }]
    },
    {
        name: 'should handle Entrypoint', fixtures: [{
        settings: {AgentHolderSpec: {Entrypoint: ['/usr/bin/test', 'arg1']}},
        viewModel: {Entrypoint: ['/usr/bin/test', 'arg1']}
    }]
    },
    {
        name: 'should handle Image', fixtures: [{
        settings: {AgentHolderSpec: {Image: 'jetbrains/teamcity-agent:9.99'}},
        viewModel: {Image: 'jetbrains/teamcity-agent:9.99'}
    }]
    },
    {
        name: 'should handle Labels', fixtures: [{
        settings: {AgentHolderSpec: {Labels: {key1: 'value1', key2: 'value2'}}},
        viewModel: {Labels: [{Key: 'key1', Value: 'value1'}, {Key: 'key2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle Volumes', fixtures: [{
        settings: {AgentHolderSpec: {Volumes: {'/tmp/container_path1': {}, '/tmp/container_path2': {}}}},
        viewModel: {Volumes: [{PathInContainer: '/tmp/container_path1'}, {PathInContainer: '/tmp/container_path2'}]}
    }]
    },
    {
        name: 'should handle Binds', fixtures: [{
        settings: {
            AgentHolderSpec: {
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
            AgentHolderSpec: {
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
            settings: {AgentHolderSpec: {HostConfig: {Memory: 1}}, Editor: {MemoryUnit: 'bytes'}},
            viewModel: {Memory: '1', MemoryUnit: 'bytes'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {Memory: 1048576}}, Editor: {MemoryUnit: 'MiB'}},
            viewModel: {Memory: '1', MemoryUnit: 'MiB'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {Memory: 10737418240}}, Editor: {MemoryUnit: 'GiB'}},
            viewModel: {Memory: '10', MemoryUnit: 'GiB'}
        }
    ]
    },
    {
        name: 'should handle MemorySwap', fixtures: [
        {
            settings: {AgentHolderSpec: {HostConfig: {MemorySwap: 1}}, Editor: {MemorySwapUnit: 'bytes'}},
            viewModel: {MemorySwap: '1', MemorySwapUnit: 'bytes'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {MemorySwap: 1048576}}, Editor: {MemorySwapUnit: 'MiB'}},
            viewModel: {MemorySwap: '1', MemorySwapUnit: 'MiB'}
        }
    ]
    },
    {
        name: 'should handle NanoCPUs', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {NanoCPUs: 1500000000}}},
        viewModel: {CPUs: '1.5'}
    }]
    },
    {
        name: 'should handle CpuQuota', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {CpuQuota: 42}}},
        viewModel: {CpuQuota: '42'}
    }]
    },
    {
        name: 'should handle CpuShares', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {CpuShares: 42}}},
        viewModel: {CpuShares: '42'}
    }]
    },
    {
        name: 'should handle CpuPeriod', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {CpuPeriod: 42}}},
        viewModel: {CpuPeriod: '42'}
    }]
    },
    {
        name: 'should handle CpusetCpus', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {CpusetCpus: '1-2,3-4'}}},
        viewModel: {CpusetCpus: '1-2,3-4'}
    }]
    },
    {
        name: 'should handle CpusetMems', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {CpusetMems: '1-2,3-4'}}},
        viewModel: {CpusetMems: '1-2,3-4'}
    }]
    },
    {
        name: 'should handle OomKillDisable', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {OomKillDisable: true}}},
        viewModel: {OomKillDisable: true}
    },
        {
            settings: {AgentHolderSpec: {HostConfig: {OomKillDisable: false}}},
            viewModel: {OomKillDisable: false}
        }]
    },
    {
        name: 'should handle Ports', fixtures: [{
        settings: {
            AgentHolderSpec: {
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
            settings: {AgentHolderSpec: {HostConfig: {PublishAllPorts: true}}},
            viewModel: {PublishAllPorts: true}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {PublishAllPorts: false}}},
            viewModel: {PublishAllPorts: false}
        }]
    },
    {
        name: 'should handle Privileged flag', fixtures: [
        {
            settings: {AgentHolderSpec: {HostConfig: {Privileged: true}}},
            viewModel: {Privileged: true}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {Privileged: false}}},
            viewModel: {Privileged: false}
        }]
    },
    {
        name: 'should handle Dns', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {Dns: ['4.4.4.4', '8.8.8.8']}}},
        viewModel: {Dns: ['4.4.4.4', '8.8.8.8']}
    }]
    },
    {
        name: 'should handle DnsSearch', fixtures: [{
        settings: {AgentHolderSpec: {HostConfig: {DnsSearch: ['domain1.com', 'domain2.com']}}},
        viewModel: {DnsSearch: ['domain1.com', 'domain2.com']}
    }]
    },
    {
        name: 'should handle ExtraHosts', fixtures: [{
        settings: {
            AgentHolderSpec: {
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
            AgentHolderSpec: {
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
            AgentHolderSpec: {
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
            settings: {AgentHolderSpec: {HostConfig: {NetworkMode: 'bridge'}}},
            viewModel: {NetworkMode: 'bridge'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {NetworkMode: 'host'}}},
            viewModel: {NetworkMode: 'host'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {NetworkMode: 'none'}}},
            viewModel: {NetworkMode: 'none'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {NetworkMode: 'container:container_name'}}},
            viewModel: {NetworkMode: 'container', NetworkContainer: 'container_name'}
        },
        {
            settings: {AgentHolderSpec: {HostConfig: {NetworkMode: 'my_custom_net'}}},
            viewModel: {NetworkMode: 'custom', NetworkCustom: 'my_custom_net'}
        }]
    },
    {
        name: 'should handle Devices', fixtures: [{
        settings: {
            AgentHolderSpec: {
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
            AgentHolderSpec: {
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
                settings: {AgentHolderSpec: {HostConfig: {LogConfig: {Type: 'none'}}}},
                viewModel: {LogType: 'none'}
            },
            {
                settings: {
                    AgentHolderSpec: {
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
            AgentHolderSpec: {
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
            AgentHolderSpec: {
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
        settings: {AgentHolderSpec: {HostConfig: {CgroupParent: '/docker'}}},
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
    let v;
    let schema;
    let validationHandler;

    beforeEach(function() {
        schema = new Schema();

        $j('body').html(schema.html);

        validationHandler = new ValidationHandler(schema.validators);
        v = new ValidatorTester(validationHandler, schema.arrayTemplates);
    });

    it('should perform profile name validation', function() {
        v.verifyProfile(v.loadElt('Profile'));
    });

    it('should perform official agent image checkbox validation', function() {
        v.verifyUseOfficialTCAgentImage(v.loadElt('UseOfficialTCAgentImage'));
    });

    it('should perform image name validation', function() {
        v.verifyImage(v.loadElt('Image'));
    });

    it('should perform max instance count validation', function() {
        v.verifyMaxInstanceCount(v.loadElt('MaxInstanceCount'));
    });

    it('should perform registry user and password validation', function() {
        v.verifyRegistryUserAndPassword(v.loadElt('RegistryUser'),
            v.loadElt('RegistryPassword'))
    });

    it('should perform stop timeout validation', function() {
        let eltHandle = v.loadElt('StopTimeout');

        v.verifyInteger(eltHandle);
        v.verifyMinApi1_25(eltHandle, '2');
    });

    it('should perform entrypoint validation', function() {
        let table = new v.Table('Entrypoint');
        let eltHandle = table.addRow().singleField();
        // First entry is mandatory.
        v.verifyMandatory(eltHandle);
        v.verifyOk(eltHandle, '/usr/bin');
        eltHandle = table.addRow().singleField();
        // Subsequent entries are optional.
        v.verifyOk(eltHandle, '');
    });

    it('should perform volumes validation', function() {
        let tableRow = new v.Table('Volumes').addRow();
        v.verifyMandatory(tableRow.field('PathInContainer'));
        v.verifyOk(tableRow.field('PathOnHost'), '');
    });

    it('should perform port binding validation', function() {
        let tableRow = new v.Table('Ports').addRow();
        let containerPort = tableRow.field('ContainerPort');
        v.verifyPortNumber(containerPort);
        v.verifyMandatory(containerPort);
        v.verifyPortNumber(tableRow.field('HostPort'));
        v.verifyIPAddress(tableRow.field('HostIp'));
    });

    it('should perform DNS validation', function() {
        v.verifyMandatory(new v.Table('Dns').addRow().singleField());
    });

    it('should perform search DNS validation', function() {
        v.verifyMandatory(new v.Table('DnsSearch').addRow().singleField());
    });

    it('should perform extra hosts validation', function() {
        let tableRow = new v.Table('ExtraHosts').addRow();
        v.verifyMandatory(tableRow.field('Name'));
        let ip = tableRow.field('Ip');
        v.verifyMandatory(ip);
        v.verifyIPAddress(ip);
    });

    it('should perform custom network validation', function() {
        v.verifyMandatory(v.loadElt('NetworkCustom'));
    });

    it('should perform container network validation', function() {
        v.verifyMandatory(v.loadElt('NetworkContainer'));
    });

    it('should perform network links validation', function() {
        let tableRow = new v.Table('Links').addRow();
        v.verifyMandatory(tableRow.field('Container'));
        v.verifyMandatory(tableRow.field('Alias'));
    });

    it('should perform ulimits validation', function() {
        let tableRow = new v.Table('Ulimits').addRow();
        v.verifyMandatory(tableRow.field('Name'));
        let softLimit = tableRow.field('Soft');
        v.verifyMandatory(softLimit);
        v.verifyInteger(softLimit);
        let hardLimit = tableRow.field('Hard');
        v.verifyMandatory(hardLimit);
        v.verifyInteger(hardLimit);
    });

    it('should perform logging configuration validation', function() {
        let tableRow = new v.Table('LogConfig').addRow();
        v.verifyMandatory(tableRow.field('Key'));
        v.verifyOk(tableRow.field('Value'), '');
    });

    it('should perform security options validation', function() {
        v.verifyMandatory(new v.Table('SecurityOpt').addRow().singleField());
    });

    it('should perform storage options validation', function() {
        let tableRow = new v.Table('StorageOpt').addRow();
        v.verifyMandatory(tableRow.field('Key'));
        v.verifyOk(tableRow.field('Value'), '');
    });

    it('should perform devices validation', function() {
        let tableRow = new v.Table('Devices').addRow();
        v.verifyMandatory(tableRow.field('PathOnHost'));
        v.verifyMandatory(tableRow.field('PathInContainer'));
        v.verifyOk(tableRow.field('CgroupPermissions'));
    });

    it('should perform env variables validation', function() {
        let tableRow = new v.Table('Env').addRow();
        v.verifyMandatory(tableRow.field('Name'));
        v.verifyOk(tableRow.field('Value'));
    });

    it('should perform labels validation', function() {
        let tableRow = new v.Table('Labels').addRow();
        v.verifyMandatory(tableRow.field('Key'));
        v.verifyOk(tableRow.field('Value'));
    });

    it('should perform memory validation', function() {
        v.verifyMemory(v.loadElt('Memory'), Utils.getElt('MemoryUnit'))
    });

    it('should perform cpus count validation', function() {
        let eltHandle = v.loadElt('CPUs');
        v.verifyOk(eltHandle, '');
        v.verifyFailure(eltHandle, -1);
        v.verifyOk(eltHandle, 0);
        expect(eltHandle.$elt.val()).toEqual('0');
        v.verifyOk(eltHandle, '0.0');
        expect(eltHandle.$elt.val()).toEqual('0.0');
        v.verifyOk(eltHandle, '00.0');
        expect(eltHandle.$elt.val()).toEqual('0.0');
        v.verifyOk(eltHandle, 1.1234);
        expect(eltHandle.$elt.val()).toEqual('1.1234');
        v.verifyOk(eltHandle, '0001.1234');
        expect(eltHandle.$elt.val()).toEqual('1.1234');

        // Upper bound
        v.verifyOk(eltHandle, 9000000000);
        v.verifyFailure(eltHandle, 9100000000);
        // Max value precision.
        v.verifyOk(eltHandle, 1.900000001);
        v.verifyFailure(eltHandle, 1.9000000001);
        v.verifyMinApi1_25(eltHandle, 0);
    });

    it('should perform CPU quota validation', function() {
        let eltHandle = v.loadElt('CpuQuota');
        v.verifyInteger(eltHandle, 1000);
        v.verifyOk(eltHandle, '');
    });

    it ('should perform cpuset-cpus validation', function () {
        v.verifyCpuSet(v.loadElt('CpusetCpus'));
    });

    it('should perform cpuset-mems validation', function() {
        v.verifyCpuSet(v.loadElt('CpusetMems'));
    });

    it('should perform CPU shares validation', function() {
        v.verifyInteger(v.loadElt('CpuShares'));
    });

    it('should perform CPU period validation', function() {
        let eltHandle = v.loadElt('CpuPeriod');
        v.verifyInteger(eltHandle, 1000, 1000000);
        v.verifyNoWindows(eltHandle, 1000);
    });

    it('should perform Blkio-weight validation', function() {
        let eltHandle = v.loadElt('BlkioWeight');
        v.verifyInteger(eltHandle, 10, 1000);
        v.verifyNoWindows(eltHandle, 10);
    });

    it('should perform swap validation', function() {

        const $swapUnit = Utils.getElt('MemorySwapUnit').val('bytes');
        const $swapUnlimited = Utils.getElt('MemorySwapUnlimited');
        const $memoryUnit = Utils.getElt('MemoryUnit').val('bytes');
        const $memory = Utils.getElt('Memory');

        let eltHandle = v.loadElt('MemorySwap');

        // Swap without memory is illegal.
        v.verifyOk(eltHandle, '');
        v.verifyFailure(eltHandle, 1);

        // Swap must be greater or equal than memory.
        $memory.val(100);
        v.verifyFailure(eltHandle, 100);
        v.verifyOk(eltHandle, 101);

        $swapUnlimited.prop('checked', true);
        // Skip validation if swap unlimited.
        v.verifyOk(eltHandle, 100);
        $swapUnlimited.prop('checked', false);

        // Cross units comparison
        $memoryUnit.val('MiB');
        $memory.val(1);
        $swapUnit.val('KiB');
        v.verifyFailure(eltHandle, 1024);
        v.verifyOk(eltHandle, 1025);

        v.verifyNoWindows(eltHandle, 99999999);
    });

    it('should perform CgroupParent validation', function() {
        v.verifyNoWindows(v.loadElt('CgroupParent'), 'system.slice');
    });

    it('should perform disabled OOM-killer validation', function() {
        v.verifyNoWindows(v.loadElt('OomKillDisable'));
    });
});

describe('initializeSettings', function() {

    let schema;

    beforeEach(function() {
        schema = new Schema();

        $j('body').html(schema.html);
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