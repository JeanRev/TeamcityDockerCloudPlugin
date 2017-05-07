

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
});