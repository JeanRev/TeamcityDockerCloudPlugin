

var dockerCloud = BS.Clouds.Docker;

var $j = jQuery.noConflict();

describe('Version number comparator', function() {

    it('should order valid version numbers', function() {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(dockerCloud.compareVersionNumbers('1.0', '2.0')).toBeLessThan(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '1.0')).toBeGreaterThan(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0.1')).toBeLessThan(0);
    });

    it('should treat unparseable token digit as 0', function() {
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

describe('Migrating images data', function() {

    it('should migrate empty image to latest version', function() {
       var image = {
           Administration: { Version: 1 }
       };
       dockerCloud._migrateImagesData(image);
        expect(image.Administration.Version).toEqual(dockerCloud.IMAGE_VERSION);
    });

    it('should ignore images with unknown version', function() {
        var image = {
            Administration: { Version: 999999 }
        };
        dockerCloud._migrateImagesData(image);
        expect(image.Administration.Version).toEqual(999999);
    });

    it('perform volume binding conversion when image V1', function() {
        var hostConfig = {};
        var editor = {};
        var image = {
            Administration: { Version: 1 },
            Container: { HostConfig: hostConfig },
            Editor: editor
        };

        hostConfig.Binds = ['/tmp/host_path:/tmp/container_path:rw', '/tmp/host_path2:volume:ro'];

        dockerCloud._migrateImagesData(image);

        expect(editor.Binds).toEqual([
            { PathOnHost: '/tmp/host_path', PathInContainer: '/tmp/container_path', ReadOnly: 'rw' },
            { PathOnHost: '/tmp/host_path2', PathInContainer: 'volume', ReadOnly: 'ro' }
        ]);

        expect(image.Administration.Version).toEqual(dockerCloud.IMAGE_VERSION);

        image = {
            Administration: { Version: 1 },
            Container: { HostConfig: hostConfig },
            Editor: editor
        };

        hostConfig.Binds = ['C:\\host_path:C:\\container_path:rw', 'C:\\host_path:volume:ro'];

        dockerCloud._migrateImagesData(image);

        expect(editor.Binds).toEqual([
            { PathOnHost: 'C:\\host_path', PathInContainer: 'C:\\container_path', ReadOnly: 'rw' },
            { PathOnHost: 'C:\\host_path', PathInContainer: 'volume', ReadOnly: 'ro' }
        ]);
    });

    it('should init default PullOnCreate flag when image V2', function() {
        var image = {
            Administration: { Version: 2 }
        };

        dockerCloud._migrateImagesData(image);

        expect(image.Administration.PullOnCreate).toEqual(true);
        expect(image.Administration.Version).toEqual(dockerCloud.IMAGE_VERSION);

        image.Administration.PullOnCreate = false;

        dockerCloud._migrateImagesData(image);

        expect(image.Administration.PullOnCreate).toEqual(false);
    });
});

describe('Applying view model', function() {
    it('should handle text input', function() {

        $j(document.body).html('<input id="dockerCloudImage_test1" type="text"/>' +
            '<input id="dockerCloudImage_test2" type="text"/>' +
            '<input id="dockerCloudImage_test3" type="text"/>');

        dockerCloud._applyViewModel( { test1: 'hello1', test2: 'hello2', test3: 'hello3' });

        expect($j('#dockerCloudImage_test1').val()).toEqual('hello1');
        expect($j('#dockerCloudImage_test2').val()).toEqual('hello2');
        expect($j('#dockerCloudImage_test3').val()).toEqual('hello3');
    });

    it('should handle password input', function() {

        $j(document.body).html('<input id="dockerCloudImage_test" type="password"/>');

        dockerCloud._applyViewModel( { test: 'hello world' });

        expect($j('#dockerCloudImage_test').val()).toEqual('hello world');
    });

    it('should handle checkbox input', function() {

        $j(document.body).html('<input id="dockerCloudImage_testChecked" type="checkbox"/>' +
            '<input id="dockerCloudImage_testUnchecked" type="checkbox"/>');

        dockerCloud._applyViewModel( { testChecked: true, testUnchecked: false });

        expect($j('#dockerCloudImage_testChecked').is(':checked')).toEqual(true);
        expect($j('#dockerCloudImage_testUnchecked').is(':checked')).toEqual(false);
    });

    it('should handle radio button', function() {
        $j(document.body).html('<input class="radioA" name="dockerCloudImage_testRadio" type="radio" value="A"/>' +
            '<input class="radioB" name="dockerCloudImage_testRadio" type="radio" value="B"/>');

        dockerCloud._applyViewModel( { testRadio: 'B' });

        expect($j('.radioA').is(':checked')).toEqual(false);
        expect($j('.radioB').is(':checked')).toEqual(true);
    });

    it('should handle selects', function () {
        $j(document.body).html('<select id="dockerCloudImage_testSelect">' +
            '<option value="a">A</option>' +
            '<option value="b">B</option>' +
            '</select>');

        dockerCloud._applyViewModel( { testSelect: 'b' });

        expect($j('#dockerCloudImage_testSelect').val()).toEqual('b');
    });

    it('should handle tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '</tbody>');

        dockerCloud.arrayTemplates['dockerCloudImage_testTable'] =
            '<td><input type="text" id="dockerCloudImage_testTable_IDX_testFieldA"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_IDX_testFieldB"/></td>';

        dockerCloud._applyViewModel( { testTable: [
            { testFieldA: "A1", testFieldB: true },
            { testFieldA: "A2", testFieldB: false }
            ] });

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

        dockerCloud._applyViewModel( { testTable: [ 'hello', 'world' ] });

        expect($j('#dockerCloudImage_testTable_0').val()).toEqual('hello');
        expect($j('#dockerCloudImage_testTable_1').val()).toEqual('world');
    });
});

describe('Restoring view model', function() {

    it('should handle text input', function() {
        $j(document.body).html('<input id="dockerCloudImage_test1" type="text" value="A"/>' +
            '<input id="dockerCloudImage_test2" type="text" value="B"/>' +
            '<input id="dockerCloudImage_test3" type="text" value="C"/>');

        expect(dockerCloud._restoreViewModel()).toEqual({ test1: 'A', test2: 'B', test3: 'C' });
    });

    it('should handle password input', function() {
        $j(document.body).html('<input id="dockerCloudImage_test" type="password" value="pwd"/>');

        expect(dockerCloud._restoreViewModel()).toEqual({ test: 'pwd' });
    });

    it('should handle checkbox input', function() {
        $j(document.body).html('<input id="dockerCloudImage_testChecked" type="checkbox" checked/>' +
            '<input id="dockerCloudImage_testUnchecked" type="checkbox"/>');

        expect(dockerCloud._restoreViewModel()).toEqual({ testChecked: true, testUnchecked: false });
    });

    it('should handle radio button', function() {
        $j(document.body).html('<input class="radioA" name="dockerCloudImage_testRadio" type="radio" value="A"/>' +
            '<input class="radioB" name="dockerCloudImage_testRadio" type="radio" value="B" checked/>');

        expect(dockerCloud._restoreViewModel()).toEqual({ testRadio: 'B' });
    });

    it('should handle selects', function () {
        $j(document.body).html('<select id="dockerCloudImage_testSelect">' +
            '<option value="a">A</option>' +
            '<option value="b" selected>B</option>' +
            '</select>');

        expect(dockerCloud._restoreViewModel()).toEqual({ testSelect: 'b' });
    });

    it('should handle tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_0_testFieldA" value="A1"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_0_testFieldB" checked/></td></tr>' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_1_testFieldA" value="A2"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_1_testFieldB"/></td></tr>' +
            '</tbody>');

        expect(dockerCloud._restoreViewModel()).toEqual({ testTable: [
            { testFieldA: 'A1', testFieldB: true }, { testFieldA: 'A2', testFieldB: false }
        ] });
    });

    it('should handle simple tabular value', function () {
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_0" value="hello"/></td></tr>' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_1" value="world"/></td></tr>' +
            '</tbody>');

        expect(dockerCloud._restoreViewModel()).toEqual({ testTable: [ 'hello', 'world' ] });
    });

    describe('Converting view model to settings', function() {

        var base = { Administration: { Version: dockerCloud.IMAGE_VERSION } };
        var settings = function(settings) {
          return $j.extend(true, settings, base);
        };

        it('should create base settings for empty view model', function() {
            expect(dockerCloud._convertViewModelToSettings({})).toEqual(base);
        });

        it('should handle Profile', function () {
            expect(dockerCloud._convertViewModelToSettings({ Profile: 'test' }))
                .toEqual(settings( { Administration: { Profile: 'test' } }));
        });

        it('should handle RmOnExit flag', function () {
            expect(dockerCloud._convertViewModelToSettings({ RmOnExit: false }))
                .toEqual(settings( { Administration: { RmOnExit: false } }));

            expect(dockerCloud._convertViewModelToSettings({ RmOnExit: true }))
                .toEqual(settings( { Administration: { RmOnExit: true } }));
        });

        it('should handle PullOnCreate flag', function () {
            expect(dockerCloud._convertViewModelToSettings({ PullOnCreate: false }))
                .toEqual(settings( { Administration: { PullOnCreate: false } }));

            expect(dockerCloud._convertViewModelToSettings({ PullOnCreate: true }))
                .toEqual(settings( { Administration: { PullOnCreate: true } }));
        });

        it('should handle MaxInstanceCount', function () {
            expect(dockerCloud._convertViewModelToSettings({ MaxInstanceCount: '42' }))
                .toEqual(settings( { Administration: { MaxInstanceCount: 42 } }));
        });

        it('should handle UseOfficialTCAgentImage flag', function () {
            expect(dockerCloud._convertViewModelToSettings({ UseOfficialTCAgentImage: true }))
                .toEqual(settings( { Administration: { UseOfficialTCAgentImage: true } }));

            expect(dockerCloud._convertViewModelToSettings({ UseOfficialTCAgentImage: false }))
                .toEqual(settings( { Administration: { UseOfficialTCAgentImage: false } }));
        });

        it('should handle RegistryUser', function () {
            expect(dockerCloud._convertViewModelToSettings({ RegistryUser: 'test' }))
                .toEqual(settings( { Administration: { RegistryUser: 'test' } }));
        });

        it('should handle RegistryPassword using UTF-16BE encoded in base64', function () {
            expect(dockerCloud._convertViewModelToSettings({ RegistryPassword: 'test_éà𐐷' }))
                .toEqual(settings( { Administration: { RegistryPassword: 'AHQAZQBzAHQAXwDpAODYAdw3' } }));
        });

        it('should handle Hostname', function () {
            expect(dockerCloud._convertViewModelToSettings({ Hostname: 'localhost' }))
                .toEqual(settings( { Container: { Hostname: 'localhost' } }));
        });

        it('should handle Domainname', function () {
            expect(dockerCloud._convertViewModelToSettings({ Domainname: 'test.com' }))
                .toEqual(settings( { Container: { Domainname: 'test.com' } }));
        });

        it('should handle User', function () {
            expect(dockerCloud._convertViewModelToSettings({ User: 'test' }))
                .toEqual(settings( { Container: { User: 'test' } }));
        });

        it('should handle WorkingDir', function () {
            expect(dockerCloud._convertViewModelToSettings({ WorkingDir: '/root' }))
                .toEqual(settings( { Container: { WorkingDir: '/root' } }));
        });

        it('should handle StopTimeout', function () {
            expect(dockerCloud._convertViewModelToSettings({ StopTimeout: '42' }))
                .toEqual(settings( { Container: { StopTimeout: 42 } }));
        });

        it('should handle Env', function () {
            expect(dockerCloud._convertViewModelToSettings(
                { Env: [ { Name: 'var1', Value: 'value1' },  { Name: 'var2', Value: 'value2' }]})
            ).toEqual(settings( { Container: { Env: [ 'var1=value1', 'var2=value2' ] } }));
        });

        it('should handle Cmd', function () {
            expect(dockerCloud._convertViewModelToSettings({ Cmd: [ '/usr/bin/test', 'arg1' ] })
            ).toEqual(settings( { Container: { Cmd: [ '/usr/bin/test', 'arg1' ] } }));
        });

        it('should handle Entrypoint', function () {
            expect(dockerCloud._convertViewModelToSettings({ Entrypoint: [ '/usr/bin/test', 'arg1' ] })
            ).toEqual(settings( { Container: { Entrypoint: [ '/usr/bin/test', 'arg1' ] } }));
        });

        it('should handle Image', function () {
            expect(dockerCloud._convertViewModelToSettings({ Image: 'jetbrains/teamcity-agent:9.99' })
            ).toEqual(settings( { Container: { Image: 'jetbrains/teamcity-agent:9.99' } }));
        });

        it('should handle Labels', function () {
            expect(dockerCloud._convertViewModelToSettings({ Labels:
                [ { Key: 'key1', Value: 'value1' }, { Key: 'key2', Value: 'value2' } ] })
            ).toEqual(settings( { Container: { Labels: { key1: 'value1', key2: 'value2' }} }));
        });

        it('should handle Volumes', function () {
            expect(dockerCloud._convertViewModelToSettings(
                { Volumes: [ { PathInContainer: '/tmp/container_path1' }, { PathInContainer: '/tmp/container_path2' } ] }
                )).toEqual(settings( { Container: { Volumes: { '/tmp/container_path1': {}, '/tmp/container_path2': {} } } }));
        });

        it('should handle Binds', function () {

            var newSettings = dockerCloud._convertViewModelToSettings(
                { Volumes: [ {  PathOnHost: '/tmp/host_path1', PathInContainer: '/tmp/container_path', ReadOnly: true},
                    { PathOnHost: 'C:\\host_path2', PathInContainer: 'volume', ReadOnly: false}
                ]}
            );

            expect(newSettings)
                .toEqual(settings( {
                    Container: {
                        HostConfig: {
                            Binds: [ '/tmp/host_path1:/tmp/container_path:ro', 'C:\\host_path2:volume:rw' ]
                        }
                    },
                    Editor : {
                        Binds: [
                            { PathOnHost: '/tmp/host_path1', PathInContainer: '/tmp/container_path', ReadOnly: 'ro' },
                            { PathOnHost: 'C:\\host_path2', PathInContainer: 'volume', ReadOnly: 'rw' }
                        ]
                    }
                }));
        });

        it('should handle Links', function () {
            expect(dockerCloud._convertViewModelToSettings({ Links:
                [ { Container: 'container_name1', Alias: 'alias1' },
                    { Container: 'container_name2', Alias: 'alias2' } ] })).
            toEqual(settings({ Container: { HostConfig: {
                Links: ['container_name1:alias1', 'container_name2:alias2']
            }}}));
        });

        it('should handle Memory', function () {
            expect(dockerCloud._convertViewModelToSettings({ Memory: '1', MemoryUnit: 'bytes'})).
            toEqual(settings({ Container:{ HostConfig: {
                Memory: 1
            }}, Editor: { MemoryUnit: 'bytes' }}));
            expect(dockerCloud._convertViewModelToSettings({ Memory: '1', MemoryUnit: 'MiB'})).
            toEqual(settings({ Container:{ HostConfig: {
                Memory: 131072
            }}, Editor: { MemoryUnit: 'MiB' }}));
            expect(dockerCloud._convertViewModelToSettings({ Memory: '10', MemoryUnit: 'GiB'})).
            toEqual(settings({ Container:{ HostConfig: {
                Memory: 1342177280
            }}, Editor: { MemoryUnit: 'GiB' }}));
        });

        it('should handle MemorySwap', function () {
            expect(dockerCloud._convertViewModelToSettings({ MemorySwap: '1', MemorySwapUnit: 'bytes'})).
            toEqual(settings({ Container:{ HostConfig: {
                MemorySwap: 1
            }}, Editor: { MemorySwapUnit: 'bytes' }}));
            expect(dockerCloud._convertViewModelToSettings({ MemorySwap: '1', MemorySwapUnit: 'MiB'})).
            toEqual(settings({ Container:{ HostConfig: {
                MemorySwap: 131072
            }}, Editor: { MemorySwapUnit: 'MiB' }}));
            expect(dockerCloud._convertViewModelToSettings({ MemorySwap: '10', MemorySwapUnit: 'GiB'})).
            toEqual(settings({ Container:{ HostConfig: {
                MemorySwap: 1342177280
            }}, Editor: { MemorySwapUnit: 'GiB' }}));
            expect(dockerCloud._convertViewModelToSettings({ MemorySwapUnlimited: true })).
            toEqual(settings({ Container:{ HostConfig: { MemorySwap: -1 }}}));
        });

        it('should handle NanoCPUs', function () {
            expect(dockerCloud._convertViewModelToSettings({ CPUs: '1.5' })).
            toEqual(settings({ Container:{ HostConfig: {
                NanoCPUs: 1500000000
            }}}));
        });

        it('should handle CpuQuota', function () {
            expect(dockerCloud._convertViewModelToSettings({ CpuQuota: '42' })).
            toEqual(settings({ Container:{ HostConfig: {
                CpuQuota: 42
            }}}));
        });

        it('should handle CpuShares', function () {
            expect(dockerCloud._convertViewModelToSettings({ CpuShares: '42' })).
            toEqual(settings({ Container:{ HostConfig: {
                CpuShares: 42
            }}}));
        });

        it('should handle CpuPeriod', function () {
            expect(dockerCloud._convertViewModelToSettings({ CpuPeriod: '42' })).
            toEqual(settings({ Container:{ HostConfig: {
                CpuPeriod: 42
            }}}));
        });

        it('should handle CpusetCpus', function () {
            expect(dockerCloud._convertViewModelToSettings({ CpusetCpus: '1-2,3-4' })).
            toEqual(settings({ Container:{ HostConfig: {
                CpusetCpus: '1-2,3-4'
            }}}));
        });

        it('should handle CpusetMems', function () {
            expect(dockerCloud._convertViewModelToSettings({ CpusetMems: '1-2,3-4' })).
            toEqual(settings({ Container:{ HostConfig: {
                CpusetMems: '1-2,3-4'
            }}}));
        });

        it('should handle OomKillDisable', function () {
            expect(dockerCloud._convertViewModelToSettings({ OomKillDisable: true })).
            toEqual(settings({ Container:{ HostConfig: {
                OomKillDisable: true
            }}}));
            expect(dockerCloud._convertViewModelToSettings({ OomKillDisable: false })).
            toEqual(settings({ Container:{ HostConfig: {
                OomKillDisable: false
            }}}));
        });

        it('should handle OomKillDisable', function () {
            expect(dockerCloud._convertViewModelToSettings({ OomKillDisable: true })).
            toEqual(settings({ Container:{ HostConfig: {
                OomKillDisable: true
            }}}));
            expect(dockerCloud._convertViewModelToSettings({ OomKillDisable: false })).
            toEqual(settings({ Container:{ HostConfig: {
                OomKillDisable: false
            }}}));
        });

        it('should handle Ports', function () {
            expect(dockerCloud._convertViewModelToSettings(
                { Ports: [
                    {
                        HostIp: '127.0.0.1',
                        HostPort: '80',
                        ContainerPort: 8080,
                        Protocol: 'tcp'
                    },
                    {
                        HostIp: '192.168.100.1',
                        HostPort: '25',
                        ContainerPort: 2525,
                        Protocol: 'udp'
                    },
                    {
                        HostIp: '192.168.100.2',
                        HostPort: '26',
                        ContainerPort: 2525,
                        Protocol: 'udp'
                    }
                ] }
                )).toEqual(settings({ Container:{ HostConfig: {
                PortBindings: {
                    '8080/tcp': [ { HostIp: '127.0.0.1', HostPort: '80' } ],
                    '2525/udp': [ { HostIp: '192.168.100.1', HostPort: '25' },
                        { HostIp: '192.168.100.2', HostPort: '26' } ]
                }
            }}}));
        });

        it('should handle PublishAllPorts', function () {
            expect(dockerCloud._convertViewModelToSettings({ PublishAllPorts: true })).
            toEqual(settings({ Container:{ HostConfig: {
                PublishAllPorts: true
            }}}));
            expect(dockerCloud._convertViewModelToSettings({ PublishAllPorts: false })).
            toEqual(settings({ Container:{ HostConfig: {
                PublishAllPorts: false
            }}}));
        });

        it('should handle Privileged flag', function () {
            expect(dockerCloud._convertViewModelToSettings({ Privileged: true })).
            toEqual(settings({ Container:{ HostConfig: {
                Privileged: true
            }}}));
            expect(dockerCloud._convertViewModelToSettings({ Privileged: false })).
            toEqual(settings({ Container:{ HostConfig: {
                Privileged: false
            }}}));
        });

        it('should handle Dns', function () {
            expect(dockerCloud._convertViewModelToSettings({ Dns: [ '4.4.4.4', '8.8.8.8' ] })).
            toEqual(settings({ Container:{ HostConfig: {
                Dns: [ '4.4.4.4', '8.8.8.8' ]
            }}}));
        });

        it('should handle DnsSearch', function () {
            expect(dockerCloud._convertViewModelToSettings({ DnsSearch: [ 'domain1.com', 'domain2.com' ] })).
            toEqual(settings({ Container:{ HostConfig: {
                DnsSearch: [ 'domain1.com', 'domain2.com' ]
            }}}));
        });

        it('should handle ExtraHosts', function () {
            expect(dockerCloud._convertViewModelToSettings({
                ExtraHosts: [ { Name: 'host1', Ip: '1.1.1.1' }, {Name: 'host2', Ip: '2.2.2.2' } ]
            })).
            toEqual(settings({ Container:{ HostConfig: {
                ExtraHosts: [ 'host1:1.1.1.1', 'host2:2.2.2.2' ]
            }}}));
        });

        it('should handle CapAdd', function () {
            expect(dockerCloud._convertViewModelToSettings({
                CapAdd: [ 'SETPCAP', 'MKNOD' ]
            })).
            toEqual(settings({ Container:{ HostConfig: {
                CapAdd: [ 'SETPCAP', 'MKNOD' ]
            }}}));
        });

        it('should handle CapDrop', function () {
            expect(dockerCloud._convertViewModelToSettings({
                CapDrop: [ 'SETPCAP', 'MKNOD' ]
            })).
            toEqual(settings({ Container:{ HostConfig: {
                CapDrop: [ 'SETPCAP', 'MKNOD' ]
            }}}));
        });

        it('should handle NetworkMode', function () {
            expect(dockerCloud._convertViewModelToSettings({ NetworkMode: 'bridge' })).
            toEqual(settings({ Container:{ HostConfig: { NetworkMode: 'bridge' }}}));
            expect(dockerCloud._convertViewModelToSettings({ NetworkMode: 'host' })).
            toEqual(settings({ Container:{ HostConfig: { NetworkMode: 'host' }}}));
            expect(dockerCloud._convertViewModelToSettings({ NetworkMode: 'none' })).
            toEqual(settings({ Container:{ HostConfig: { NetworkMode: 'none' }}}));
            expect(dockerCloud._convertViewModelToSettings({ NetworkMode: 'container',
                NetworkContainer: 'container_name' })).
            toEqual(settings({ Container:{ HostConfig: { NetworkMode: 'container:container_name' }}}));
            expect(dockerCloud._convertViewModelToSettings({ NetworkMode: 'default' })).
            toEqual(settings());
            expect(dockerCloud._convertViewModelToSettings({ NetworkMode: 'custom', NetworkCustom: 'my_custom_net' })).
            toEqual(settings({ Container:{ HostConfig: { NetworkMode: 'my_custom_net' }}}));
        });

        it('should handle Devices', function () {
            expect(dockerCloud._convertViewModelToSettings({ Devices: [
                { PathOnHost: '/dev/sda1', PathInContainer: '/dev/sdb1', CgroupPermissions: 'rwm' },
                { PathOnHost: '/dev/sda2', PathInContainer: '/dev/sdb2', CgroupPermissions: 'r' }
                ]})).
            toEqual(settings({ Container:{ HostConfig: { Devices: [
                { PathOnHost: '/dev/sda1', PathInContainer: '/dev/sdb1', CgroupPermissions: 'rwm' },
                { PathOnHost: '/dev/sda2', PathInContainer: '/dev/sdb2', CgroupPermissions: 'r' } ]
            }}}));
        });

        it('should handle Ulimits', function () {
            expect(dockerCloud._convertViewModelToSettings({ Devices: [
                { Name: 'nofile', Soft: '123', Hard: '456' },
                { Name: 'memlock', Soft: '789', Hard: '012' }
            ]})).
            toEqual(settings({ Container:{ HostConfig: { Devices: [
                { Name: 'nofile', Soft: '123', Hard: '456' },
                { Name: 'memlock', Soft: '789', Hard: '012' } ]
            }}}));
        });

        it('should handle LogType', function () {
            expect(dockerCloud._convertViewModelToSettings({ LogType: 'none' })).
            toEqual(settings({ Container:{ HostConfig: { LogConfig: { Type: 'none' }}}}));
            expect(dockerCloud._convertViewModelToSettings({ LogType: 'json-file', LogConfig: [
                { Key: 'max-size', Value: '10m' },
                { Key: 'max-file', Value: '10' }
            ] })).
            toEqual(settings({ Container:{ HostConfig: { LogConfig: { Type: 'json-file',
                Config: { 'max-size': '10m', 'max-file': '10' }}}}}));
        });


        it('should handle CgroupParent', function () {
            expect(dockerCloud._convertViewModelToSettings({ CgroupParent: '/docker' })).
            toEqual(settings({ Container:{ HostConfig: { CgroupParent: '/docker' }}}));
        });
    });
});