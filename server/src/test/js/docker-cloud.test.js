

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

    it('init default PullOnCreate flag when image V2', function() {
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
