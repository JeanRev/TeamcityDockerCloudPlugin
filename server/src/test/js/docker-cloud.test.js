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

describe('Sanitizing Daemon URI', function() {

    it('should preserve the given scheme if any', function () {
        expect(dockerCloud.sanitizeURI('tpc://hostname', false)).toEqual('tpc://hostname');
        expect(dockerCloud.sanitizeURI('unix:///some_socket', true)).toEqual('unix:///some_socket');
        expect(dockerCloud.sanitizeURI('blah://this is completely wrong', true)).toEqual('blah://this is completely wrong');

    });

    it('should auto-detect IPv4 address', function() {
        expect(dockerCloud.sanitizeURI('127.0.0.1', false)).toEqual('tcp://127.0.0.1');
        expect(dockerCloud.sanitizeURI('127.0.0.1', true)).toEqual('tcp://127.0.0.1');
    });

    it('should auto-detect IPv6 address', function() {
        expect(dockerCloud.sanitizeURI('0000:0000:0000:0000:0000:0000:0000:0001', false)).toEqual('tcp://0000:0000:0000:0000:0000:0000:0000:0001');
        expect(dockerCloud.sanitizeURI('::1', true)).toEqual('tcp://::1');
    });

    it('should auto detect unix scheme when not on windows host', function() {
        expect(dockerCloud.sanitizeURI('/some/path', false)).toEqual('unix:///some/path');
    });

    it('should auto correct the count of leading slashes in the scheme specific part for unix sockets', function() {
        expect(dockerCloud.sanitizeURI('unix:/some/path', false)).toEqual('unix:///some/path');
        expect(dockerCloud.sanitizeURI('unix://some/path', false)).toEqual('unix:///some/path');
        expect(dockerCloud.sanitizeURI('unix://////some/path', false)).toEqual('unix:///some/path');

        expect(dockerCloud.sanitizeURI('//some/path', false)).toEqual('unix:///some/path');
        expect(dockerCloud.sanitizeURI('//////some/path', false)).toEqual('unix:///some/path');
    });

    it('should auto detect npipe scheme when on windows host', function() {
        expect(dockerCloud.sanitizeURI('//server/pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
    });

    it('should auto correct the count of leading slashes in the scheme specific part for named pipes', function() {
        expect(dockerCloud.sanitizeURI('/server/pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(dockerCloud.sanitizeURI('//////server/pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(dockerCloud.sanitizeURI('npipe:/server/pipe/pipename', false)).toEqual('npipe:////server/pipe/pipename');
        expect(dockerCloud.sanitizeURI('npipe://////server/pipe/pipename', false)).toEqual('npipe:////server/pipe/pipename');
    });

    it('should replace all backlashes with slashes for named pipes', function() {
        expect(dockerCloud.sanitizeURI('\\server\\pipe\\pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(dockerCloud.sanitizeURI('npipe:\\\\server\\pipe\\pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(dockerCloud.sanitizeURI('npipe:\\/server\\pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(dockerCloud.sanitizeURI('/some\\path', false)).toEqual('unix:///some\\path');
    });

    it('should trim paths', function() {
        expect(dockerCloud.sanitizeURI(null, true)).toEqual(null);
        expect(dockerCloud.sanitizeURI('', false)).toEqual('');
        expect(dockerCloud.sanitizeURI('  ', false)).toEqual('');
        expect(dockerCloud.sanitizeURI('  tcp://127.0.0.1', false)).toEqual('tcp://127.0.0.1');
        expect(dockerCloud.sanitizeURI('tcp://127.0.0.1  ', false)).toEqual('tcp://127.0.0.1');
        expect(dockerCloud.sanitizeURI('  tcp://127.0.0.1  ', false)).toEqual('tcp://127.0.0.1');
    });

    it('should default to tcp', function() {
        expect(dockerCloud.sanitizeURI('somehostname:2375', false)).toEqual('tcp://somehostname:2375');
    });
});

describe('Field validation', function() {

    var Validator = function(result) {
        var self = this;
        self.invoked = false;
        self.run = function() {
            self.invoked = true;
            return result;
        }
    };

    var emptyValidationResult = { warnings: [], error: null };

    it('should work with no validators defined', function() {
        expect(dockerCloud.validate($j('<input/>', null))).toEqual(emptyValidationResult);
        expect(dockerCloud.validate($j('<input/>', []))).toEqual(emptyValidationResult);
    });

    it('should return empty result for successful validation', function() {
        var validator = new Validator();
        expect(dockerCloud.validate($j('<input/>'), [ validator.run ])).toEqual(emptyValidationResult);
        expect(validator.invoked).toEqual(true);
    });

    it('should handle validation error', function() {
        var okValidator1 = new Validator();
        var errorValidator1 = new Validator({ msg: 'error1' });
        var errorValidator2 = new Validator({ msg: 'error2' });
        var okValidator2 = new Validator();

        expect(dockerCloud.validate($j('<input/>'), [ okValidator1.run, errorValidator1.run, errorValidator2.run,
            okValidator2.run ])).toEqual({ warnings: [], error: 'error1' });
        expect(okValidator1.invoked).toEqual(true);
        expect(errorValidator1.invoked).toEqual(true);
        expect(errorValidator2.invoked).toEqual(true);
        expect(okValidator2.invoked).toEqual(true);
    });

    it('should handle validation warnings', function() {
        var errorValidator1 = new Validator({ msg: 'error1' });
        var warnValidator1 = new Validator({ msg: 'warn1', warning: true });
        var errorValidator2 = new Validator({ msg: 'error1' });
        var warnValidator2 = new Validator({ msg: 'warn2', warning: true });

        expect(dockerCloud.validate($j('<input/>'), [ errorValidator1.run, warnValidator1.run, errorValidator2.run,
            warnValidator2.run ])).toEqual({ warnings: [ 'warn1', 'warn2' ], error: 'error1' });
        expect(errorValidator1.invoked).toEqual(true);
        expect(warnValidator1.invoked).toEqual(true);
        expect(errorValidator2.invoked).toEqual(true);
        expect(warnValidator2.invoked).toEqual(true);
    });

    it('should ignore disabled fields', function() {
        var warnValidator = new Validator({ msg: 'warn1', warning: true });
        var errorValidator = new Validator({ msg: 'error1' });

        expect(dockerCloud.validate($j('<input/>').prop('disabled', true),
            [ warnValidator.run, errorValidator.run ])).toEqual(emptyValidationResult);
        expect(warnValidator.invoked).toEqual(false);
        expect(errorValidator.invoked).toEqual(false);
    });
});

describe('Validators', function() {

    beforeEach(function() {
        jasmine.getFixtures().fixturesPath = 'base/src/main/resources/buildServerResources';
        jasmine.getFixtures().load('image-settings.html');

        dockerCloud.$imageDialogSubmitBtn = $j('<input/>');
        dockerCloud._initValidators();
    });

    var Table = function(id) {
        this.addRow = function() {
            var tbodyId = 'dockerCloudImage_' + id;
            var $tbody = $j('#' + tbodyId);
            dockerCloud._addTableRow($tbody);
            return new Row($j.data($tbody.get(0), "index"), tbodyId);
        }
    };

    var Row = function(index, tbodyId) {
        this.singleField = function() {
            return { $elt: $j('#' + tbodyId + '_' + index), validators: dockerCloud.validators[tbodyId + '_IDX'] };
        };
        this.field = function(name){
            return { $elt: $j('#' + tbodyId + '_' + index + '_' + name),
                validators: dockerCloud.validators[tbodyId + '_IDX_' + name] };
        };
    };

    var loadElt = function(id) {
        var effectiveId = 'dockerCloudImage_' + id;
        var $elt = $j('#' + effectiveId);
        expect($elt.length).toEqual(1);
        var validators = dockerCloud.validators[effectiveId];
        return { $elt: $elt, validators: validators }
    };

    var verifyOk = function(eltHandle, value) {
        eltHandle.$elt.val(value);
        var result = dockerCloud.validate(eltHandle.$elt, eltHandle.validators);
        expect(result).toEqual({ error: null, warnings: [] });
    };

    var verifySingleWarn = function(eltHandle, value) {
        eltHandle.$elt.val(value);
        var result = dockerCloud.validate(eltHandle.$elt, eltHandle.validators);
        expect(result.error).toEqual(null);
        expect(result.warnings.length).toEqual(1);
    };

    var verifyFailure = function(eltHandle, value) {
        eltHandle.$elt.val(value);
        var result = dockerCloud.validate(eltHandle.$elt, eltHandle.validators);
        expect(result.error).not.toEqual(null);
    };

    var verifyMandatory = function(eltHandle) {
        verifyAutoTrim(eltHandle);
        verifyFailure(eltHandle, '');
        verifyFailure(eltHandle, '  ');
        verifyFailure(eltHandle, '\t');
    };

    var verifyMinApi1_25 = function(eltHandle, value) {
        verifyOk(eltHandle, '');

        verifyOk(eltHandle, value);

        dockerCloud.effectiveApiVersion = '1.25';
        verifyOk(eltHandle, value);

        dockerCloud.effectiveApiVersion = '1.26';
        verifyOk(eltHandle, value);

        dockerCloud.effectiveApiVersion = '1.24';
        verifySingleWarn(eltHandle, value);

        dockerCloud.effectiveApiVersion = undefined;
    };

    var verifyNoWindows = function(eltHandle, value) {
        var checkbox = eltHandle.$elt.is(':checkbox');
        if (checkbox) {
            eltHandle.$elt.prop('checked', false);
            verifyOk(eltHandle);
        } else {
            verifyOk(eltHandle, '');
        }

        if (checkbox) {
            eltHandle.$elt.prop('checked', true);
            verifyOk(eltHandle);
        } else {
            verifyOk(eltHandle, value);
        }

        dockerCloud.daemonOs = 'windows';
        if (checkbox) {
            eltHandle.$elt.prop('checked', true);
            verifySingleWarn(eltHandle);
        } else {
            verifySingleWarn(eltHandle, value);
        }

        dockerCloud.daemonOs = undefined;
    };

    var verifyAutoTrim = function(eltHandle) {
        dockerCloud.validate(eltHandle.$elt.val('  '), eltHandle.validators);
        expect(eltHandle.$elt.val()).toEqual('');
        dockerCloud.validate(eltHandle.$elt.val('\t'), eltHandle.validators);
        expect(eltHandle.$elt.val()).toEqual('');
        dockerCloud.validate(eltHandle.$elt.val(' abc\t'), eltHandle.validators);
        expect(eltHandle.$elt.val()).toEqual('abc');
    };

    var verifyPortNumber = function(eltHandle) {
        verifyInteger(eltHandle, 1, 65535);
    };

    var verifyInteger = function(eltHandle, lowerBound, upperBound) {
        if (typeof lowerBound === 'undefined') {
            lowerBound = 0;
        }
        var upperBoundTestValue;
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
        dockerCloud.validate(eltHandle.$elt.val(0), eltHandle.validators);
        expect(eltHandle.$elt.val()).toEqual('0');
        dockerCloud.validate(eltHandle.$elt.val('00'), eltHandle.validators);
        expect(eltHandle.$elt.val()).toEqual('0');
        dockerCloud.validate(eltHandle.$elt.val('0042'), eltHandle.validators);
        expect(eltHandle.$elt.val()).toEqual('42');
    };

    var verifyIPAddress = function(eltHandle) {
        verifyAutoTrim(eltHandle);
        verifyOk(eltHandle, '127.0.0.1');
        verifyOk(eltHandle, '0000:0000:0000:0000:0000:0000:0000:0001');
        verifyOk(eltHandle, 'aced::a11:7e57');
        verifyOk(eltHandle, '::1');
        verifyFailure(eltHandle, 'hostname');
    };

    var verifyCpuSet = function(eltHandle) {
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
        dockerCloud.imagesData = {};

        var eltHandle = loadElt('Profile');
        verifyOk(eltHandle, 'profile_name');

        verifyMandatory(eltHandle);

        // Invalid chars.
        verifyFailure(eltHandle, 'profile name');
        verifyFailure(eltHandle, 'profile%name');
        verifyFailure(eltHandle, 'profilé_name');

        // Existing profile.
        dockerCloud.$imageDialogSubmitBtn.data('profile', 'profile_name');
        dockerCloud.imagesData['profile_name'] = {};

        verifyOk(eltHandle, 'profile_name');

        // Duplicated profile name.
        dockerCloud.$imageDialogSubmitBtn.data('profile', 'profile_name2');
        dockerCloud.imagesData['profile_name2'] = {};

        verifyFailure(eltHandle, 'profile_name');
    });

    it('should perform official agent image checkbox validation', function() {
        verifyNoWindows(loadElt('UseOfficialTCAgentImage'));
    });

    it('should perform image name validation', function() {
        var eltHandle = loadElt('Image');

        verifyOk(eltHandle, 'image:1.0');
        verifyMandatory(eltHandle);
    });

    it('should perform max instance count validation', function() {
        var eltHandle = loadElt('MaxInstanceCount');

        verifyInteger(eltHandle, 1);
    });

    it('should perform registry user and password validation', function() {
        var userEltHandle = loadElt('RegistryUser');
        dockerCloud.$registryUser = userEltHandle.$elt;
        var pwdEltHandle = loadElt('RegistryPassword');
        dockerCloud.$registryPassword = pwdEltHandle.$elt;

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
        var eltHandle = loadElt('StopTimeout');

        verifyInteger(eltHandle);
        verifyMinApi1_25(eltHandle, '2');
    });

    it('should perform entrypoint validation', function() {
        var table = new Table('Entrypoint');
        var eltHandle = table.addRow().singleField();
        // First entry is mandatory.
        verifyMandatory(eltHandle);
        verifyOk(eltHandle, '/usr/bin');
        eltHandle = table.addRow().singleField();
        // Subsequent entries are optional.
        verifyOk(eltHandle, '');
    });

    it('should perform volumes validation', function() {
        var tableRow = new Table('Volumes').addRow();
        verifyMandatory(tableRow.field('PathInContainer'));
        verifyOk(tableRow.field('PathOnHost'), '');
    });

    it('should perform port binding validation', function() {
        var tableRow = new Table('Ports').addRow();
        var containerPort = tableRow.field('ContainerPort');
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
        var tableRow = new Table('ExtraHosts').addRow();
        verifyMandatory(tableRow.field('Name'));
        var ip = tableRow.field('Ip');
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
        var tableRow = new Table('Links').addRow();
        verifyMandatory(tableRow.field('Container'));
        verifyMandatory(tableRow.field('Alias'));
    });

    it('should perform ulimits validation', function() {
        var tableRow = new Table('Ulimits').addRow();
        verifyMandatory(tableRow.field('Name'));
        var softLimit = tableRow.field('Soft');
        verifyMandatory(softLimit);
        verifyInteger(softLimit);
        var hardLimit = tableRow.field('Hard');
        verifyMandatory(hardLimit);
        verifyInteger(hardLimit);
    });

    it('should perform logging configuration validation', function() {
        var tableRow = new Table('LogConfig').addRow();
        verifyMandatory(tableRow.field('Key'));
        verifyOk(tableRow.field('Value'), '');
    });

    it('should perform security options validation', function() {
        verifyMandatory(new Table('SecurityOpt').addRow().singleField());
    });

    it('should perform storage options validation', function() {
        var tableRow = new Table('StorageOpt').addRow();
        verifyMandatory(tableRow.field('Key'));
        verifyOk(tableRow.field('Value'), '');
    });

    it('should perform devices validation', function() {
        var tableRow = new Table('Devices').addRow();
        verifyMandatory(tableRow.field('PathOnHost'));
        verifyMandatory(tableRow.field('PathInContainer'));
        verifyOk(tableRow.field('CgroupPermissions'));
    });

    it('should perform env variables validation', function() {
        var tableRow = new Table('Env').addRow();
        verifyMandatory(tableRow.field('Name'));
        verifyOk(tableRow.field('Value'));
    });

    it('should perform labels validation', function() {
        var tableRow = new Table('Labels').addRow();
        verifyMandatory(tableRow.field('Key'));
        verifyOk(tableRow.field('Value'));
    });

    it('should perform memory validation', function() {
        var eltHandle = loadElt('Memory');
        // Test 4MiB limit
        dockerCloud.$memoryUnit = $j('<input/>').val('bytes');
        verifyInteger(eltHandle, 4194304);
        dockerCloud.$memoryUnit = $j('<input/>').val('KiB');
        verifyInteger(eltHandle, 4096);
        dockerCloud.$memoryUnit = $j('<input/>').val('MiB');
        verifyInteger(eltHandle, 4);
    });

    it('should perform cpus count validation', function() {
        var eltHandle = loadElt('CPUs');
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
        var eltHandle = loadElt('CpuQuota');
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
        var eltHandle = loadElt('CpuPeriod');
        verifyInteger(eltHandle, 1000, 1000000);
        verifyNoWindows(eltHandle, 1000);
    });

    it('should perform Blkio-weight validation', function() {
        var eltHandle = loadElt('BlkioWeight');
        verifyInteger(eltHandle, 10, 1000);
        verifyNoWindows(eltHandle, 10);
    });

    it('should perform swap validation', function() {
        dockerCloud.$swapUnlimited = $j('<input type="checkbox"/>');
        dockerCloud.$memory = $j('<input/>');
        dockerCloud.$swapUnit = $j('<input/>').val('bytes');
        dockerCloud.$memoryUnit = $j('<input/>').val('bytes');
        var eltHandle = loadElt('MemorySwap');

        // Swap without memory is illegal.
        verifyOk(eltHandle, '');
        verifyFailure(eltHandle, 1);

        // Swap must be greater or equal than memory.
        dockerCloud.$memory.val(100);
        verifyFailure(eltHandle, 100);
        verifyOk(eltHandle, 101);

        dockerCloud.$swapUnlimited.prop('checked', true);
        // Skip validation if swap unlimited.
        verifyOk(eltHandle, 100);
        dockerCloud.$swapUnlimited.prop('checked', false);

        // Cross units comparison
        dockerCloud.$memoryUnit.val('MiB');
        dockerCloud.$memory.val(1);
        dockerCloud.$swapUnit.val('KiB');
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

describe('shortenString', function() {

    it('should shorten string to the desired length', function() {
        expect(dockerCloud.shortenString('hello world', 10)).toEqual('hello worl…')
    });

    it('should not affect strings shorter or of maximal length', function() {
        expect(dockerCloud.shortenString('', 100)).toEqual('');
        expect(dockerCloud.shortenString('hello world', 100)).toEqual('hello world');
        expect(dockerCloud.shortenString('hello world', 11)).toEqual('hello world')
    });

    it('should trim whitespaces when shortening strings', function() {
        expect(dockerCloud.shortenString('hello world ', 100)).toEqual('hello world ');
        expect(dockerCloud.shortenString('hello world ', 6)).toEqual('hello…');
        expect(dockerCloud.shortenString('hello world ', 5)).toEqual('hello…');
    });

    it('should be resilient to null or undefined values', function() {
        expect(dockerCloud.shortenString(null, 100)).toEqual('');
        expect(dockerCloud.shortenString(undefined, 100)).toEqual('');
    });
});

describe('validateHandler', function() {

    var emptyValidation;

    beforeEach(function() {
        emptyValidation = {
            error: null,
            warnings: []
        };
    });

    var createFixture = function(tabId, fieldId, error, warnings) {
        var errorStr = error ? error : '';
        var warningsStr = warnings && warnings.length ? '<p>' + warnings.join('</p><p>') + '</p>' : '';

        jasmine.getFixtures().set('<div id="dockerCloudImageTab_anotherTab1"></div>' +
            '<div id="dockerCloudImageTab_' + tabId + '">' +
            '<input id="' + fieldId + '"/>' +
            '<span id="' + fieldId + '_error">' + errorStr + '</span>"' +
            '<div id="' + fieldId + '_warning">' + warningsStr + '</div>' +
            '</div>' +
            '<div id="dockerCloudImageTab_anotherTab2"></div>');
        return document.getElementById(fieldId);
    };

    var TestTabbedPane = function() {
        this._tabs = {};
        this.addTab = function(id) {
            return this._tabs[id] = new TestTab();
        };
        this.getTab = function (id) {
            return this._tabs[id];
        };
    };

    var TestTab = function() {
        this.warningMessages = [];
        this.errorMessages = [];

        this.addMessage = function(msg, warning) {
            if (warning) {
                dockerCloud.addToArrayIfAbsent(this.warningMessages, msg);
            } else {
                dockerCloud.addToArrayIfAbsent(this.errorMessages, msg)
            }
        };
        this.clearMessages = function(id) {
            dockerCloud.removeFromArray(this.warningMessages, id);
            dockerCloud.removeFromArray(this.errorMessages, id);
        }
    };

    it('should invoke validate with corresponding validators', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');

        var dummyValidator = [];

        dockerCloud.validators['myField'] = dummyValidator;

        spyOn(dockerCloud, 'validate').and.returnValue(emptyValidation);

        dockerCloud.validateHandler.bind(field)();

        expect(dockerCloud.validate).toHaveBeenCalledWith($j(field), dummyValidator);
    });

    it('should clear previous warnings and error messages', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        var tab = dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');
        tab.warningMessages.push('some_field_id_a');
        tab.warningMessages.push('myField');
        tab.errorMessages.push('some_field_id_b');
        tab.errorMessages.push('myField');

        spyOn(dockerCloud, 'validate').and.returnValue(emptyValidation);

        dockerCloud.validateHandler.bind(field)();

        expect(tab.warningMessages).toEqual(['some_field_id_a']);
        expect(tab.errorMessages).toEqual(['some_field_id_b']);
    });

    it('should clear previous warnings and error messages on tab', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        var tab = dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');
        tab.warningMessages.push('some_field_id_a');
        tab.warningMessages.push('myField');
        tab.errorMessages.push('some_field_id_b');
        tab.errorMessages.push('myField');

        spyOn(dockerCloud, 'validate').and.returnValue(emptyValidation);

        dockerCloud.validateHandler.bind(field)();

        expect(tab.warningMessages).toEqual(['some_field_id_a']);
        expect(tab.errorMessages).toEqual(['some_field_id_b']);
    });

    it('should clear previous warnings and error messages on field', function() {
        var field = createFixture('myTab', 'myField', 'some_error', ['some_warning_1', 'some_warning_2']);

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');

        spyOn(dockerCloud, 'validate').and.returnValue(emptyValidation);

        dockerCloud.validateHandler.bind(field)();

        expect($j('#myField_error').is(':empty')).toEqual(true);
        expect($j('#myField_warning').is(':empty')).toEqual(true);
    });

    it('should set notify warnings messages on tab', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        var tab = dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');

        spyOn(dockerCloud, 'validate').and.returnValue({ warnings: ['some_warning_1', 'some_warning_2']});

        dockerCloud.validateHandler.bind(field)();

        expect(tab.warningMessages).toEqual(['myField']);
        expect(tab.errorMessages).toEqual([]);
    });

    it('should set notify error messages on tab', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        var tab = dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');

        spyOn(dockerCloud, 'validate').and.returnValue({ error: 'some_error', warnings: []});

        dockerCloud.validateHandler.bind(field)();

        expect(tab.warningMessages).toEqual([]);
        expect(tab.errorMessages).toEqual(['myField']);
    });

    it('should set warning messages on field', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        var tab = dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');

        spyOn(dockerCloud, 'validate').and.returnValue({ warnings: ['some_warning_1', 'some_warning_2']});

        dockerCloud.validateHandler.bind(field)();

        expect($j('#myField_warning').get(0).innerHTML).toEqual('<p>some_warning_1</p><p>some_warning_2</p>');
    });

    it('should set error message on field', function() {
        var field = createFixture('myTab', 'myField');

        dockerCloud.imageDataTabbedPane = new TestTabbedPane();
        var tab = dockerCloud.imageDataTabbedPane.addTab('dockerCloudImageTab_myTab');

        spyOn(dockerCloud, 'validate').and.returnValue({ error: 'some_error', warnings: [] });

        dockerCloud.validateHandler.bind(field)();

        expect($j('#myField_error').get(0).innerHTML).toEqual('some_error');
    });
});