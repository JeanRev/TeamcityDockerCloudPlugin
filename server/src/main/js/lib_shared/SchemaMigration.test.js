

const migrateSettings = require('SchemaMigration.js');
const Validators = require('Validators');

describe('Migrating images settings', function () {

    it('should migrate empty image to latest version', function () {
        let image = {
            Administration: {Version: 1}
        };
        migrateSettings(image);
    });

    it('should ignore images with unknown version', function () {
        let image = {
            Administration: {Version: 999999}
        };
        migrateSettings(image);
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

        migrateSettings(image);

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

        migrateSettings(image);

        expect(editor.Binds).toEqual([
            {PathOnHost: 'C:\\host_path', PathInContainer: 'C:\\container_path', ReadOnly: 'rw'},
            {PathOnHost: 'C:\\host_path', PathInContainer: 'volume', ReadOnly: 'ro'}
        ]);
    });

    it('should init default PullOnCreate flag when image V2', function () {
        let image = {
            Administration: { Version: 2 }
        };

        migrateSettings(image);

        expect(image.Administration.PullOnCreate).toEqual(true);

        image.Administration.PullOnCreate = false;

        image = {
            Administration: { Version: 3, PullOnCreate: false }
        };

        migrateSettings(image);

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

        migrateSettings(imageData);

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
                    migrateSettings(imageData);

                    expect(hostConfig[memValue.value]).toEqual(value * Validators.units_multiplier[unit]);
                });

                hostConfig = {};
                editor = {};
                hostConfig[memValue.value] = -1;
                editor[memValue.unit] = unit;

                let imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                migrateSettings(imageData);

                expect(hostConfig[memValue.value]).toEqual(-1);

                hostConfig = {};
                editor = {};
                hostConfig[memValue.value] = -1;

                imageData = settings({Editor: editor, Container: { HostConfig: hostConfig }});
                migrateSettings(imageData);

                expect(hostConfig[memValue.value]).toEqual(-1);
            });
        });
    });

    it('should rename Container object when image V4', function () {
        let imageData = {
            Administration: { Version: 4 }
        };

        migrateSettings(imageData);

        expect(imageData).toEqual({Administration: {Version: 4}});

        imageData = {
            Administration: { Version: 4 },
            Container: { foo: 'bar' }
        };

        migrateSettings(imageData);

        expect(imageData).toEqual({Administration: {Version: 4}, AgentHolderSpec: { foo: 'bar' }});
    });
});