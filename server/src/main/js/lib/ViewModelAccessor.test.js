
const ViewModelAccess = require('ViewModelAccessor');

describe('Applying view model', function () {


    const emptyTemplates = {
        deleteCell: '',
        settingsCell: '<span></span></a>',
        insertAddButton: function() {}
    };
    
    it('should handle text input', function () {
        const viewModelAccess = new ViewModelAccess();

        $j(document.body).html('<input id="dockerCloudImage_test1" type="text"/>' +
            '<input id="dockerCloudImage_test2" type="text"/>' +
            '<input id="dockerCloudImage_test3" type="text"/>');

        viewModelAccess.applyViewModel({test1: 'hello1', test2: 'hello2', test3: 'hello3'});

        expect($j('#dockerCloudImage_test1').val()).toEqual('hello1');
        expect($j('#dockerCloudImage_test2').val()).toEqual('hello2');
        expect($j('#dockerCloudImage_test3').val()).toEqual('hello3');
    });

    it('should handle password input', function () {
        const viewModelAccess = new ViewModelAccess();

        $j(document.body).html('<input id="dockerCloudImage_test" type="password"/>');

        viewModelAccess.applyViewModel({test: 'hello world'});

        expect($j('#dockerCloudImage_test').val()).toEqual('hello world');
    });

    it('should handle checkbox input', function () {
        const viewModelAccess = new ViewModelAccess();

        $j(document.body).html('<input id="dockerCloudImage_testChecked" type="checkbox"/>' +
            '<input id="dockerCloudImage_testUnchecked" type="checkbox"/>');

        viewModelAccess.applyViewModel({testChecked: true, testUnchecked: false});

        expect($j('#dockerCloudImage_testChecked').is(':checked')).toEqual(true);
        expect($j('#dockerCloudImage_testUnchecked').is(':checked')).toEqual(false);
    });

    it('should handle radio button', function () {
        const viewModelAccess = new ViewModelAccess();

        $j(document.body).html('<input class="radioA" name="dockerCloudImage_testRadio" type="radio" value="A"/>' +
            '<input class="radioB" name="dockerCloudImage_testRadio" type="radio" value="B"/>');

        viewModelAccess.applyViewModel({testRadio: 'B'});

        expect($j('.radioA').is(':checked')).toEqual(false);
        expect($j('.radioB').is(':checked')).toEqual(true);
    });

    it('should handle selects', function () {
        const viewModelAccess = new ViewModelAccess();

        $j(document.body).html('<select id="dockerCloudImage_testSelect">' +
            '<option value="a">A</option>' +
            '<option value="b">B</option>' +
            '</select>');

        viewModelAccess.applyViewModel({testSelect: 'b'});

        expect($j('#dockerCloudImage_testSelect').val()).toEqual('b');
    });

    it('should handle tabular value', function () {
        const arrayTemplates = { testTable:
        '<td><input type="text" id="dockerCloudImage_testTable_IDX_testFieldA"/></td>' +
        '<td><input type="checkbox" id="dockerCloudImage_testTable_IDX_testFieldB"/></td>' };
        const viewModelAccess = new ViewModelAccess(emptyTemplates, arrayTemplates);

        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '</tbody>');

        viewModelAccess.applyViewModel({
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
        const arrayTemplates = { testTable: '<td><input type="text" id="dockerCloudImage_testTable_IDX"/></td>' };
        const viewModelAccess = new ViewModelAccess(emptyTemplates, arrayTemplates);
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '</tbody>');

        viewModelAccess.applyViewModel({testTable: ['hello', 'world']});

        expect($j('#dockerCloudImage_testTable_0').val()).toEqual('hello');
        expect($j('#dockerCloudImage_testTable_1').val()).toEqual('world');
    });
});


describe('Restoring view model', function () {

    it('should handle text input', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<input id="dockerCloudImage_test1" type="text" value="A"/>' +
            '<input id="dockerCloudImage_test2" type="text" value="B"/>' +
            '<input id="dockerCloudImage_test3" type="text" value="C"/>');

        expect(viewModelAccess.restoreViewModel()).toEqual({test1: 'A', test2: 'B', test3: 'C'});
    });

    it('should handle password input', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<input id="dockerCloudImage_test" type="password" value="pwd"/>');

        expect(viewModelAccess.restoreViewModel()).toEqual({test: 'pwd'});
    });

    it('should handle checkbox input', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<input id="dockerCloudImage_testChecked" type="checkbox" checked/>' +
            '<input id="dockerCloudImage_testUnchecked" type="checkbox"/>');

        expect(viewModelAccess.restoreViewModel()).toEqual({testChecked: true, testUnchecked: false});
    });

    it('should handle radio button', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<input class="radioA" name="dockerCloudImage_testRadio" type="radio" value="A"/>' +
            '<input class="radioB" name="dockerCloudImage_testRadio" type="radio" value="B" checked/>');

        expect(viewModelAccess.restoreViewModel()).toEqual({testRadio: 'B'});
    });

    it('should handle selects', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<select id="dockerCloudImage_testSelect">' +
            '<option value="a">A</option>' +
            '<option value="b" selected>B</option>' +
            '</select>');

        expect(viewModelAccess.restoreViewModel()).toEqual({testSelect: 'b'});
    });

    it('should handle tabular value', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_0_testFieldA" value="A1"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_0_testFieldB" checked/></td></tr>' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_1_testFieldA" value="A2"/></td>' +
            '<td><input type="checkbox" id="dockerCloudImage_testTable_1_testFieldB"/></td></tr>' +
            '</tbody>');

        expect(viewModelAccess.restoreViewModel()).toEqual({
            testTable: [
                {testFieldA: 'A1', testFieldB: true}, {testFieldA: 'A2', testFieldB: false}
            ]
        });
    });

    it('should handle simple tabular value', function () {
        const viewModelAccess = new ViewModelAccess();
        
        $j(document.body).html('<table>' +
            '<tbody id="dockerCloudImage_testTable">' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_0" value="hello"/></td></tr>' +
            '<tr><td><input type="text" id="dockerCloudImage_testTable_1" value="world"/></td></tr>' +
            '</tbody>');

        expect(viewModelAccess.restoreViewModel()).toEqual({testTable: ['hello', 'world']});
    });
});