
const Controller = require('Controller');
const Utils = require('Utils');

let $j = jQuery.noConflict();

const NOOP = () => {};

describe('validateHandler', function() {

    let validators;
    let tabbedPane;
    let controller;
    let emptyValidation;

    function createFixture(tabId, fieldId, error, warnings) {
        let errorStr = error ? error : '';
        let warningsStr = warnings && warnings.length ? '<p>' + warnings.join('</p><p>') + '</p>' : '';

        $j('body').html('<div id="DockerCloudImageDialog"><div' +
            ' id="dockerCloudImageTab_anotherTab1"></div>' +
            '<div id="dockerCloudImageTab_' + tabId + '">' +
            '<input id="dockerCloudImage_' + fieldId + '"/>' +
            '<span id="dockerCloudImage_' + fieldId + '_error">' + errorStr + '</span>"' +
            '<div id="dockerCloudImage_' + fieldId + '_warning">' + warningsStr + '</div>' +
            '</div>' +
            '<div id="dockerCloudImageTab_anotherTab2"></div></div>');

        validators = {};
        tabbedPane = new TestTabbedPane();

        const BS = { AbstractModalDialog: {}};
        const OO = { extend: _ => { return { afterClose: NOOP } } };

        controller = new Controller(BS, OO, tabbedPane, {}, {
            validators: validators
        });
        emptyValidation = {
            error: null,
            warnings: []
        };

        return $j('#dockerCloudImage_' + fieldId);
    }

    function TestTabbedPane() {
        this._tabs = {};
        this.addTab = function(id) {
            return this._tabs[id] = new TestTab();
        };
        this.getTab = function (id) {
            return this._tabs[id];
        };
        this.getTabs = function() {
            return this._tabs;
        };
        this.showIn = NOOP;
    }

    function TestTab() {
        this.warningMessages = [];
        this.errorMessages = [];

        this.addMessage = function(msg, warning) {
            if (warning) {
                Utils.addToArrayIfAbsent(this.warningMessages, msg);
            } else {
                Utils.addToArrayIfAbsent(this.errorMessages, msg)
            }
        };
        this.clearMessages = function(id) {
            Utils.removeFromArray(this.warningMessages, id);
            Utils.removeFromArray(this.errorMessages, id);
        }
    }


    it('should clear previous warnings and error messages on tab', function() {
        let field = createFixture('myTab', 'myField');

        let tab = tabbedPane.addTab('dockerCloudImageTab_myTab');
        tab.warningMessages.push('some_field_id_a');
        tab.warningMessages.push('dockerCloudImage_myField');
        tab.errorMessages.push('some_field_id_b');
        tab.errorMessages.push('dockerCloudImage_myField');

        field.blur();

        expect(tab.warningMessages).toEqual(['some_field_id_a']);
        expect(tab.errorMessages).toEqual(['some_field_id_b']);
    });

    it('should clear previous warnings and error messages on field', function() {
        let field = createFixture('myTab', 'myField', 'some_error', ['some_warning_1', 'some_warning_2']);

        tabbedPane.addTab('dockerCloudImageTab_myTab');

        field.blur();

        expect($j('#dockerCloudImage_myField_error').is(':empty')).toEqual(true);
        expect($j('#dockerCloudImage_myField_warning').is(':empty')).toEqual(true);
    });

    it('should set warnings messages on tab', function() {
        let field = createFixture('myTab', 'myField');

        let tab = tabbedPane.addTab('dockerCloudImageTab_myTab');

        validators['myField'] = [
            () => { return { warning: true, msg: 'some_warning_1'}},
            () => { return { warning: true, msg: 'some_warning_2'}}];

        field.blur();

        expect(tab.warningMessages).toEqual(['dockerCloudImage_myField']);
        expect(tab.errorMessages).toEqual([]);
    });

    it('should set notify error messages on tab', function() {
        let field = createFixture('myTab', 'myField');
        let tab = tabbedPane.addTab('dockerCloudImageTab_myTab');

        validators['myField'] = [
            () => { return { warning: false, msg: 'some_error'}}];

        field.blur();

        expect(tab.warningMessages).toEqual([]);
        expect(tab.errorMessages).toEqual(['dockerCloudImage_myField']);
    });

    it('should set warning messages on field', function() {
        let field = createFixture('myTab', 'myField');

        tabbedPane.addTab('dockerCloudImageTab_myTab');

        validators['myField'] = [
            () => { return { warning: true, msg: 'some_warning_1'}},
            () => { return { warning: true, msg: 'some_warning_2'}}];

        field.blur();

        expect($j('#dockerCloudImage_myField_warning').get(0).innerHTML).toEqual('<p>some_warning_1</p><p>some_warning_2</p>');
    });

    it('should set error message on field', function() {
        let field = createFixture('myTab', 'myField');

        tabbedPane.addTab('dockerCloudImageTab_myTab');

        validators['myField'] = [
            () => { return { warning: false, msg: 'some_error'}}];

        field.blur();

        expect($j('#dockerCloudImage_myField_error').get(0).innerHTML).toEqual('some_error');
    });
});