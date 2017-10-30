

const SwarmSchema = require('SwarmSchema');
const Utils = require('Utils');
const ValidationHandler = require('ValidationHandler');
const ValidatorTester = require('Validators.test');

let settingsConverterFixtures = [
    {
        name: 'should handle Profile', fixtures: [{
        settings: {Administration: {Profile: 'test'}}, viewModel: {Profile: 'test'}
    }]
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
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Hostname: 'localhost'}}}}, viewModel: {Hostname: 'localhost'}
    }]
    },
    {
        name: 'should handle User', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {User: 'test'}}}}, viewModel: {User: 'test'}
    }]
    },
    {
        name: 'should handle Dir', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Dir: '/root'}}}}, viewModel: {Dir: '/root'}
    }]
    },
    {
        name: 'should handle StopSignal', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {StopSignal: 'SIGKILL'}}}}, viewModel: {StopSignal: 'SIGKILL'}
    }]
    },
    {
        name: 'should handle StopGracePeriod', fixtures: [
        {
            settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {StopGracePeriod: 1}}}, Editor: {StopGracePeriodUnit: 'ns'}},
            viewModel: {StopGracePeriod: '1', StopGracePeriodUnit: 'ns'}
        },
        {
            settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {StopGracePeriod: 1000000000}}}, Editor: {StopGracePeriodUnit: 's'}},
            viewModel: {StopGracePeriod: '1', StopGracePeriodUnit: 's'}
        },
        {
            settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {StopGracePeriod: 36000000000000}}}, Editor: {StopGracePeriodUnit: 'h'}},
            viewModel: {StopGracePeriod: '10', StopGracePeriodUnit: 'h'}
        }
    ]
    },
    {
        name: 'should handle Env', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Env: ['var1=value1', 'var2=value2']}}}},
        viewModel: {Env: [{Name: 'var1', Value: 'value1'}, {Name: 'var2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle Command', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Command: ['/usr/bin/test', 'arg1']}}}},
        viewModel: {Command: ['/usr/bin/test', 'arg1']}
    }]
    },
    {
        name: 'should handle Image', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Image: 'jetbrains/teamcity-agent:9.99'}}}},
        viewModel: {Image: 'jetbrains/teamcity-agent:9.99'}
    }]
    },
    {
        name: 'should handle Labels', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Labels: {key1: 'value1', key2: 'value2'}}}}},
        viewModel: {Labels: [{Key: 'key1', Value: 'value1'}, {Key: 'key2', Value: 'value2'}]}
    }]
    },
    {
        name: 'should handle Volumes', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Mounts: [
            {Source: 'volume_name', Target: '/tmp/container_path1', ReadOnly: false, Type: 'volume'},
            {Target: '/tmp/container_path2', ReadOnly: true, Type: 'volume' }
            ]}}}},
        viewModel: {Volumes: [
            {Source: 'volume_name', Target: '/tmp/container_path1', ReadOnly: false},
            {Target: '/tmp/container_path2', ReadOnly: true}]}
    }]
    },
    {
        name: 'should handle Binds', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {Mounts: [
            {Source: '/tmp/host_path1', Target: '/tmp/container_path1', ReadOnly: false, Type: 'bind'},
            {Source: '/tmp/host_path2', Target: '/tmp/container_path2', ReadOnly: false, Type: 'bind'}
        ]}}}},
        viewModel: {Binds: [
            {Source: '/tmp/host_path1', Target: '/tmp/container_path1', ReadOnly: false},
            {Source: '/tmp/host_path2', Target: '/tmp/container_path2', ReadOnly: false}]}
    }]
    },
    {
        name: 'should handle MemoryBytesLimit', fixtures: [
        {
            settings: {AgentHolderSpec: {TaskTemplate: {Resources: { Limits: { MemoryBytes: 1}}}}, Editor: {MemoryBytesLimitUnit: 'bytes'}},
            viewModel: {MemoryBytesLimit: '1', MemoryBytesLimitUnit: 'bytes'}
        },
        {
            settings: {AgentHolderSpec: {TaskTemplate: {Resources: { Limits: {MemoryBytes: 1048576}}}}, Editor: {MemoryBytesLimitUnit: 'MiB'}},
            viewModel: {MemoryBytesLimit: '1', MemoryBytesLimitUnit: 'MiB'}
        },
        {
            settings: {AgentHolderSpec: {TaskTemplate: {Resources: { Limits: {MemoryBytes: 10737418240}}}}, Editor: {MemoryBytesLimitUnit: 'GiB'}},
            viewModel: {MemoryBytesLimit: '10', MemoryBytesLimitUnit: 'GiB'}
        }
    ]
    },
    {
        name: 'should handle NanoCPUs', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {Resources: { Limits: {NanoCPUs: 1500000000}}}}},
        viewModel: {CPUs: '1.5'}
    }]
    },
    {
        name: 'should handle Ports', fixtures: [{
        settings: {
            AgentHolderSpec: {
                EndpointSpec: {
                    Ports: [{
                       PublishedPort: 8080,
                        TargetPort: 80,
                        Protocol: 'tcp'
                    }]
                }
            }
        },
        viewModel: { Ports: [{
            PublishedPort: 8080,
            TargetPort: 80,
            Protocol: 'tcp'
        }]}
    }]},
    {
        name: 'should handle Dns', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {DNSConfig: { Nameservers: ['4.4.4.4', '8.8.8.8']}}}}},
        viewModel: {Dns: ['4.4.4.4', '8.8.8.8']}
    }]
    },
    {
        name: 'should handle DnsSearch', fixtures: [{
        settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {DNSConfig: { Search: ['domain1.com', 'domain2.com']}}}}},
        viewModel: {DnsSearch: ['domain1.com', 'domain2.com']}
    }]
    },
    {
        name: 'should handle Hosts', fixtures: [{
        settings: {
            AgentHolderSpec: {
                TaskTemplate: {
                    ContainerSpec: {Hosts: ['host1 1.1.1.1 alias1', 'host2 2.2.2.2 alias 2']}
                }
            }
        },
        viewModel: {
            Hosts: ['host1 1.1.1.1 alias1', 'host2 2.2.2.2 alias 2']
        }
    }]},
    {
        name: 'should handle LogDriver',
        fixtures: [
            {
                settings: {AgentHolderSpec: {TaskTemplate: {ContainerSpec: {LogDriver: {Name: 'none'}}}}},
                viewModel: {LogDriver: 'none'}
            },
            {
                settings: {
                    AgentHolderSpec: {
                        TaskTemplate: {ContainerSpec: {LogDriver: {
                                Name: 'json-file',
                                Options: {'max-size': '10m', 'max-file': '10'}
                            }
                        }
                    }}
                },
                viewModel: {
                    LogDriver: 'json-file', LogOptions: [
                        {Key: 'max-size', Value: '10m'},
                        {Key: 'max-file', Value: '10'}
                    ]
                }
            }
        ]
    }
];


describe('Converting view model to settings', function () {

    const schema = new SwarmSchema();

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

    const schema = new SwarmSchema();

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

    beforeEach(function() {
        let schema = new SwarmSchema();
        $j('body').html(schema.html);
        let validationHandler = new ValidationHandler(schema.validators);
        v = new ValidatorTester(validationHandler, schema.arrayTemplates);
    });

    it('should perform profile name validation (Swarm)', function() {
        v.verifyProfile(v.loadElt('Profile'));
    });

    it('should perform image name validation (Swarm)', function() {
        v.verifyImage(v.loadElt('Image'));
    });

    it('should perform official agent image checkbox validation (Swarm)', function() {
        v.verifyUseOfficialTCAgentImage(v.loadElt('UseOfficialTCAgentImage'));
    });

    it('should perform max instance count validation (Swarm)', function() {
        v.verifyMaxInstanceCount(v.loadElt('MaxInstanceCount'));
    });

    it('should perform registry user and password validation (Swarm)', function() {
        v.verifyRegistryUserAndPassword(v.loadElt('RegistryUser'),
            v.loadElt('RegistryPassword'))
    });

    it('should perform task stop grace period validation', function() {
        v.verifyInteger(v.loadElt('StopGracePeriod'));
    });

    it('should perform DNS validation (Swarm)', function() {
        let eltHandle = new v.Table('Dns').addRow().singleField();

        v.verifyMandatory(eltHandle);
    });

    it('should perform search DNS validation (Swarm)', function() {
        let eltHandle = new v.Table('DnsSearch').addRow().singleField();

        v.verifyMandatory(eltHandle);
    });

    it('should perform Hosts validation', function() {
        v.verifyMandatory(new v.Table('Hosts').addRow().singleField());
    });

    it('should perform logging configuration validation (Swarm)', function() {
        let tableRow = new v. Table('LogOptions').addRow();
        v.verifyMandatory(tableRow.field('Key'));
        v.verifyOk(tableRow.field('Value'), '');
    });

    it('should perform env variables validation (Swarm)', function() {
        let tableRow = new v.Table('Env').addRow();
        v.verifyMandatory(tableRow.field('Name'));
        v.verifyOk(tableRow.field('Value'));
    });

    it('should perform labels validation (Swarm)', function() {
        let tableRow = new v.Table('Labels').addRow();
        v.verifyMandatory(tableRow.field('Key'));
        v.verifyOk(tableRow.field('Value'));
    });

    it('should perform memory validation (Swarm)', function() {
        v.verifyMemory(v.loadElt('MemoryBytesLimit'), Utils.getElt('MemoryBytesLimitUnit'));
    });

    it('should perform port binding validation (Swarm)', function() {
        let tableRow = new v.Table('Ports').addRow();
        let containerPort = tableRow.field('TargetPort');
        v.verifyPortNumber(containerPort);
        v.verifyMandatory(containerPort);
        v.verifyPortNumber(tableRow.field('PublishedPort'));
    });


});


describe('initializeSettings', function() {

    let schema;

    beforeEach(function() {
        schema = new SwarmSchema();

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