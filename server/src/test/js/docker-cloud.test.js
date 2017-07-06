var dockerCloud = BS.Clouds.Docker;

var $j = jQuery.noConflict();

describe('Version number comparator', function () {

    it('should order valid version numbers', function () {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(dockerCloud.compareVersionNumbers('1.0', '2.0')).toBeLessThan(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '1.0')).toBeGreaterThan(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0.1')).toBeLessThan(0);
    });

    it('should treat unparseable token digit as 0', function () {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(dockerCloud.compareVersionNumbers('0', 'hello world')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('1.0', '1.a')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('1.0', '1.a')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0.0', '2.0.a')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0.0', '2.0.-1')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0.0.0', '2.0.404a.0')).toEqual(0);
    });
});

describe('Migrating images data', function () {

    it('should migrate empty image to latest version', function () {
        var image = {
            Administration: {Version: 1}
        };
        dockerCloud._migrateImagesData(image);
        expect(image.Administration.Version).toEqual(dockerCloud.IMAGE_VERSION);
    });

    it('should ignore images with unknown version', function () {
        var image = {
            Administration: {Version: 999999}
        };
        dockerCloud._migrateImagesData(image);
        expect(image.Administration.Version).toEqual(999999);
    });

    it('perform volume binding conversion when image V1', function () {
        var hostConfig = {};
        var editor = {};
        var image = {
            Administration: {Version: 1},
            Container: {HostConfig: hostConfig},
            Editor: editor
        };

        hostConfig.Binds = ['/tmp/host_path:/tmp/container_path:rw', '/tmp/host_path2:volume:ro'];

        dockerCloud._migrateImagesData(image);

        expect(editor.Binds).toEqual([
            {PathOnHost: '/tmp/host_path', PathInContainer: '/tmp/container_path', ReadOnly: 'rw'},
            {PathOnHost: '/tmp/host_path2', PathInContainer: 'volume', ReadOnly: 'ro'}
        ]);

        expect(image.Administration.Version).toEqual(dockerCloud.IMAGE_VERSION);

        image = {
            Administration: {Version: 1},
            Container: {HostConfig: hostConfig},
            Editor: editor
        };

        hostConfig.Binds = ['C:\\host_path:C:\\container_path:rw', 'C:\\host_path:volume:ro'];

        dockerCloud._migrateImagesData(image);

        expect(editor.Binds).toEqual([
            {PathOnHost: 'C:\\host_path', PathInContainer: 'C:\\container_path', ReadOnly: 'rw'},
            {PathOnHost: 'C:\\host_path', PathInContainer: 'volume', ReadOnly: 'ro'}
        ]);
    });

    it('should init default PullOnCreate flag when image V2', function () {
        var image = {
            Administration: {Version: 2}
        };

        dockerCloud._migrateImagesData(image);

        expect(image.Administration.PullOnCreate).toEqual(true);
        expect(image.Administration.Version).toEqual(dockerCloud.IMAGE_VERSION);

        image.Administration.PullOnCreate = false;

        dockerCloud._migrateImagesData(image);

        expect(image.Administration.PullOnCreate).toEqual(false);
    });

    it('should fix memory unit when image V3', function () {

        var legacyUnitFactors = {
            GiB: 134217728,
            MiB: 131072,
            bytes: 1
        };

        var base = {Administration: {Version: 3}};
        var settings = function (settings) {
            return $j.extend(true, settings, base);
        };

        var imageData = settings({});

        dockerCloud._migrateImagesData(imageData);

        expect(imageData).toEqual({Administration: {Version: dockerCloud.IMAGE_VERSION}, Editor:{}});

        var memValues = [
            { value: 'MemorySwap', unit: 'MemorySwapUnit' },
            { value: 'Memory', unit: 'MemoryUnit'}];
        memValues.forEach(function (memValue) {
            Object.keys(legacyUnitFactors).forEach(function (unit) {
                var hostConfig;
                var editor;
                [1, 2, 3].forEach(function(value) {
                    var factor = legacyUnitFactors[unit];
                    hostConfig = {};
                    editor = {};
                    hostConfig[memValue.value] = value * factor;
                    editor[memValue.unit] = unit;
                    var imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                    dockerCloud._migrateImagesData(imageData);

                    expect(hostConfig[memValue.value]).toEqual(value * dockerCloud._units_multiplier[unit]);
                });

                hostConfig = {};
                editor = {};
                hostConfig[memValue.value] = -1;
                editor[memValue.unit] = unit;

                var imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                dockerCloud._migrateImagesData(imageData);

                expect(hostConfig[memValue.value]).toEqual(-1);

                hostConfig = {};
                editor = {};
                hostConfig[memValue.value] = -1;

                imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                dockerCloud._migrateImagesData(imageData);

                expect(hostConfig[memValue.value]).toEqual(-1);
            });
        });
    });
});

describe('Applying view model', function () {
    it('should handle text input', function () {

        $j(document.body).html('<input id="dockerCloudImage_test1" type="text"/>' +
            '<input id="dockerCloudImage_test2" type="text"/>' +
            '<input id="dockerCloudImage_test3" type="text"/>');

        dockerCloud._applyViewModel({test1: 'hello1', test2: 'hello2', test3: 'hello3'});

        expect($j('#dockerCloudImage_test1').val()).toEqual('hello1');
        expect($j('#dockerCloudImage_test2').val()).toEqual('hello2');
        expect($j('#dockerCloudImage_test3').val()).toEqual('hello3');
    });

    it('should handle password input', function () {

        $j(document.body).html('<input id="dockerCloudImage_test" type="password"/>');

        dockerCloud._applyViewModel({test: 'hello world'});

        expect($j('#dockerCloudImage_test').val()).toEqual('hello world');
    });

    it('should handle checkbox input', function () {

        $j(document.body).html('<input id="dockerCloudImage_testChecked" type="checkbox"/>' +
            '<input id="dockerCloudImage_testUnchecked" type="checkbox"/>');

        dockerCloud._applyViewModel({testChecked: true, testUnchecked: false});

        expect($j('#dockerCloudImage_testChecked').is(':checked')).toEqual(true);
        expect($j('#dockerCloudImage_testUnchecked').is(':checked')).toEqual(false);
    });

    it('should handle radio button', function () {
        $j(document.body).html('<input class="radioA" name="dockerCloudImage_testRadio" type="radio" value="A"/>' +
            '<input class="radioB" name="dockerCloudImage_testRadio" type="radio" value="B"/>');

        dockerCloud._applyViewModel({testRadio: 'B'});

        expect($j('.radioA').is(':checked')).toEqual(false);
        expect($j('.radioB').is(':checked')).toEqual(true);
    });

    it('should handle selects', function () {
        $j(document.body).html('<select id="dockerCloudImage_testSelect">' +
            '<option value="a">A</option>' +
            '<option value="b">B</option>' +
            '</select>');

        dockerCloud._applyViewModel({testSelect: 'b'});

        expect($j('#dockerCloudImage_testSelect').val()).toEqual('b');
    });

    it('should handle tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '</tbody>');

        dockerCloud.arrayTemplates['dockerCloudImage_testTable'] =
            '<td><input type="text" id="dockerCloudImage_testTable_IDX_testFieldA"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_IDX_testFieldB"/></td>';

        dockerCloud._applyViewModel({
            testTable: [
                {testFieldA: "A1", testFieldB: true},
                {testFieldA: "A2", testFieldB: false}
            ]
        });

        expect($j('#dockerCloudImage_testTable_0_testFieldA').val()).toEqual('A1');
        expect($j('#dockerCloudImage_testTable_0_testFieldB').is(':checked')).toEqual(true);
        expect($j('#dockerCloudImage_testTable_1_testFieldA').val()).toEqual('A2');
        expect($j('#dockerCloudImage_testTable_1_testFieldB').is(':checked')).toEqual(false);
    });

    it('should handle simple tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '</tbody>');

        dockerCloud.arrayTemplates['dockerCloudImage_testTable'] =
            '<td><input type="text" id="dockerCloudImage_testTable_IDX"/></td>';

        dockerCloud._applyViewModel({testTable: ['hello', 'world']});

        expect($j('#dockerCloudImage_testTable_0').val()).toEqual('hello');
        expect($j('#dockerCloudImage_testTable_1').val()).toEqual('world');
    });
});

describe('Restoring view model', function () {

    it('should handle text input', function () {
        $j(document.body).html('<input id="dockerCloudImage_test1" type="text" value="A"/>' +
            '<input id="dockerCloudImage_test2" type="text" value="B"/>' +
            '<input id="dockerCloudImage_test3" type="text" value="C"/>');

        expect(dockerCloud._restoreViewModel()).toEqual({test1: 'A', test2: 'B', test3: 'C'});
    });

    it('should handle password input', function () {
        $j(document.body).html('<input id="dockerCloudImage_test" type="password" value="pwd"/>');

        expect(dockerCloud._restoreViewModel()).toEqual({test: 'pwd'});
    });

    it('should handle checkbox input', function () {
        $j(document.body).html('<input id="dockerCloudImage_testChecked" type="checkbox" checked/>' +
            '<input id="dockerCloudImage_testUnchecked" type="checkbox"/>');

        expect(dockerCloud._restoreViewModel()).toEqual({testChecked: true, testUnchecked: false});
    });

    it('should handle radio button', function () {
        $j(document.body).html('<input class="radioA" name="dockerCloudImage_testRadio" type="radio" value="A"/>' +
            '<input class="radioB" name="dockerCloudImage_testRadio" type="radio" value="B" checked/>');

        expect(dockerCloud._restoreViewModel()).toEqual({testRadio: 'B'});
    });

    it('should handle selects', function () {
        $j(document.body).html('<select id="dockerCloudImage_testSelect">' +
            '<option value="a">A</option>' +
            '<option value="b" selected>B</option>' +
            '</select>');

        expect(dockerCloud._restoreViewModel()).toEqual({testSelect: 'b'});
    });

    it('should handle tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_0_testFieldA" value="A1"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_0_testFieldB" checked/></td></tr>' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_1_testFieldA" value="A2"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_1_testFieldB"/></td></tr>' +
            '</tbody>');

        expect(dockerCloud._restoreViewModel()).toEqual({
            testTable: [
                {testFieldA: 'A1', testFieldB: true}, {testFieldA: 'A2', testFieldB: false}
            ]
        });
    });

    it('should handle simple tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_0" value="hello"/></td></tr>' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_1" value="world"/></td></tr>' +
            '</tbody>');

        expect(dockerCloud._restoreViewModel()).toEqual({testTable: ['hello', 'world']});
    });
});

var settingsConverterFixtures = [
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


    var base = {Administration: {Version: dockerCloud.IMAGE_VERSION}};
    var settings = function (settings) {
        return $j.extend(true, settings, base);
    };

    it('should create base settings for empty view model', function () {
        expect(dockerCloud._convertViewModelToSettings({})).toEqual(base);
    });


    settingsConverterFixtures.forEach(function (test) {
        it(test.name, function () {
            test.fixtures.forEach(function (fixture) {
                expect(dockerCloud._convertViewModelToSettings(fixture.viewModel))
                    .toEqual(settings(fixture.settings));
            });
        });
    });
});

describe('Converting settings to view model', function () {

    var base = {Administration: {Version: dockerCloud.IMAGE_VERSION}};
    var settings = function (settings) {
        return $j.extend(true, settings, base);
    };

    it('should create empty view model for empty settings', function () {
        expect(dockerCloud._convertSettingsToViewModel({})).toEqual({});
    });

    settingsConverterFixtures.forEach(function (test) {
        it(test.name, function () {
            test.fixtures.forEach(function (fixture) {
                expect(dockerCloud._convertSettingsToViewModel(settings(fixture.settings)))
                    .toEqual(fixture.viewModel);
            });
        });
    });
});