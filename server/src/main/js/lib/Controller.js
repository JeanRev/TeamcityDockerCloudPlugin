
const I18n = require('I18n');
const Logger = require('Logger');
const TableHelper = require('TableHelper');
const Utils = require('Utils');
const ValidationHandler = require('ValidationHandler');
const ViewModelAccessor = require('ViewModelAccessor');

import Clipboard from 'clipboard';
import UAParser from 'ua-parser';

const IMAGE_VERSION = 5;

function Controller(bs, oo, tabbedPane, params, schema) {
    Logger.logInfo('Initializing Docker Cloud JS ' + schema.cloudType + ' support.');

    const BS = bs;
    const OO = oo;

    let useTlsParam = params.useTlsParam;

    const hasWebSocketSupport = ('WebSocket' in window) && params.webSocketEndpointsAvailable;
    if (hasWebSocketSupport) {
        Logger.logInfo('WebSocket support enabled.')
    }
    const hasXTermSupport = hasWebSocketSupport && _checkXtermBrowserSupport();
    if (hasXTermSupport) {
        Logger.logInfo('XTerm support enabled.')
    }

    $j('#DockerCloudImageDialogContent').append(schema.html);

    const defaultLocalInstanceURI = params.defaultLocalInstanceURI;
    const checkConnectivityCtrlURL = params.checkConnectivityCtrlURL;
    const testContainerCtrlURL = params.testContainerCtrlURL;
    const errorIconURL = params.errorIconURL;
    const warnIconURL = params.warnIconURL;
    const testStatusSocketPath = params.testStatusSocketPath;
    const streamSocketPath = params.streamSocketPath;
    const windowsHost = params.windowsHost;
    const daemonTargetVersion = params.daemonTargetVersion;
    const daemonMinVersion = params.daemonMinVersion;
    const $images = $j(_idSel(params.imagesParam));
    const $tcImagesDetails = $j(_idSel(params.tcImagesDetails));

    const $checkConnectionBtn = $j('#dockerCloudCheckConnectionBtn');
    const $checkConnectionResult = $j('#dockerCloudCheckConnectionResult');
    const $checkConnectionWarning = $j('#dockerCloudCheckConnectionWarning');
    const $newImageBtn = $j('#dockerShowDialogButton');
    const $imageDialogSubmitBtn = $j('#dockerAddImageButton');
    const $imageDialogCancelBtn = $j('#dockerCancelAddImageButton');
    const $imagesTable = $j('#dockerCloudImagesTable');

    const $useTls = $j(_idSel(useTlsParam));
    const $dockerAddress = $j('#dockerCloudDockerAddress');
    const $useLocalInstance = $j('#dockerCloudUseLocalInstance');
    const $useCustomInstance = $j('#dockerCloudUseCustomInstance');
    const $checkConnectionLoader = $j('#dockerCloudCheckConnectionLoader');

    const $imageDataOkBtn = $j('#dockerAddImageButton');
    const $imageDataDialogTitle = $j('#DockerImageDialogTitle');
    const $dialog = $j('#DockerCloudImageDialog');
    const $dialogTables = $j('table', $dialog);

    /* Test container */
    const $imageTestContainerBtn = $j('#dockerTestImageButton');
    const $testImageDialog = $j('#DockerTestContainerDialog');
    const $testContainerCreateBtn = $j('#dockerCreateImageTest');
    const $testContainerStartBtn = $j('#dockerStartImageTest');
    const $testContainerLoader = $j('#dockerCloudTestContainerLoader');
    const $testContainerLabel = $j('#dockerCloudTestContainerLabel');
    const $testExecInfo = $j('#dockerTestExecInfo');
    const $testContainerOutcome = $j('#dockerCloudTestContainerOutcome');
    const $testContainerShellBtn = $j('#dockerCloudTestContainerShellBtn');
    const $testContainerContainerLogsBtn = $j('#dockerCloudTestContainerContainerLogsBtn');
    const $testContainerDisposeBtn = $j('#dockerCloudTestContainerDisposeBtn');
    const $testContainerCancelBtn = $j('#dockerCloudTestContainerCancelBtn');
    const $testContainerCloseBtn = $j('#dockerCloudTestContainerCloseBtn');
    const $testContainerSuccessIcon = $j('#dockerCloudTestContainerSuccess');
    const $testContainerWarningIcon = $j('#dockerCloudTestContainerWarning');
    const $testContainerErrorIcon = $j('#dockerCloudTestContainerError');
    const $dockerTestContainerOutput = $j('#dockerTestContainerOutput');
    const $dockerTestContainerOutputTitle = $j('#dockerTestContainerOutputTitle');
    const $testedImage = $j(_idSel('run.var.teamcity.docker.cloud.tested_image'));

    /* Diagnostic dialog */
    const $diagnosticMsg = $j('#dockerCloudTestContainerErrorDetailsMsg');
    const $diagnosticLogs = $j('#dockerCloudTestContainerErrorDetailsStackTrace');
    const $diagnosticCopyBtn = $j('#dockerDiagnosticCopyBtn');
    const $diagnosticCloseBtn = $j('#dockerDiagnosticCloseBtn');

    const templates = {
        deleteCell: '<a class="btn dockerCloudCtrlBtn dockerCloudDeleteBtn" href="#/" title="Delete"><span></span></a>',
        settingsCell: '<a class="btn dockerCloudCtrlBtn dockerCloudSettingsBtn" href="#/" title="Settings"><span></span></a>',
        insertAddButton: function($tbody, colspan) {
            $tbody.append('<tr class="dockerCloudAddItem"><td colspan="' + colspan + '" class="dockerCloudCtrlCell"> <a class="btn dockerCloudAddBtn" href="#/"  title="Add item"><span class="dockerCloudAddBtn">Add</span></a></td></tr>');
        }
    };

    const i18n = new I18n(schema.translations);
    const tableHelper = new TableHelper(templates, schema.arrayTemplates);
    const viewModelAccessor = new ViewModelAccessor(templates, schema.arrayTemplates);
    const imagesData = _loadImageData();
    const validationHandler = new ValidationHandler(schema.validators);

    let effectiveApiVersion;
    let daemonOs;
    let clipboard;
    let checkConnectionTimeout;
    let tooltipHolder;
    let queryDaemonInfoReq;
    let tabs;
    let testCancelled;
    let testUuid;
    let testPhase;
    let testStatusSocket = null;
    let logStreamingSocket;

    _initDaemonInfo();
    _initTabs();
    _setupTooltips();
    _initDialogs();
    _bindHandlers();
    _renderImagesTable();

    /* Initialization */

    function _initDaemonInfo() {

        // The radio button selection between local and custom instance address is conditionally displayed
        // and its state persisted as plugin property. This test ensure that we always have a consistent
        // state with older versions of the plugin where the associated property may not have been set yet.
        if (!$useLocalInstance.is(':checked')) {
            $useCustomInstance.prop('checked', true);
        }

        // Simple heuristic to check if this is a new cloud profile. At least one image must be saved for
        // existing profiles.
        let existingProfile = Object.keys(imagesData).length;

        if (existingProfile) {
            // Check Docker connectivity for existing profiles at the time the configuration is loaded.
            // This ensure that we have fresh meta-data regarding the Daemon OS and supported API.
            setTimeout(_checkConnection, 0);
        }
    }

    function _initTabs() {
        Logger.logDebug('Initializing dialog tabs.');

        tabs = schema.tabs;

        $j.each(tabs, function(i, val) {
            tabbedPane.addTab(val.id, {
                caption: '<span>' + val.lbl + ' <span class="dockerCloudErrorIcon"></span></span>',
                onselect: _selectTab
            });
        });

        $j.each(tabbedPane.getTabs(), function (i, tab) {
            tab.errors = [];
            tab.warnings = [];

            tab.clearAllMessages = function() {
                tab.errors = [];
                tab.warnings = [];
                tab._updateTabIcon();
                _updateOkBtnState();
            };
            tab.clearMessages = function (id) {
                Utils.removeFromArray(tab.errors, id);
                Utils.removeFromArray(tab.warnings, id);
                tab._updateTabIcon();
                _updateOkBtnState();
            };
            tab.addMessage = function (id, warning) {
                Utils.addToArrayIfAbsent(warning ? tab.warnings : tab.errors, id);
                tab._updateTabIcon();
                _updateOkBtnState();
            };
            tab._updateTabIcon = function () {
                let caption = $j(tab.myOptions.caption);
                let span = caption.children('span').empty();
                if (tab.errors.length) {
                    span.append('<img src="' + errorIconURL + '" />');
                } else if (tab.warnings.length) {
                    span.append('<img src="' + warnIconURL + '" />');
                }
                tab.setCaption(caption[0].outerHTML);
            };
        });

        $imageDataOkBtn.click(_okBtnClicked);

        tabbedPane.showIn('dockerCloudImageTabContainer');
    }

    function _setupTooltips() {
        Logger.logDebug('Setup help tooltip.');

        // Our tooltip div holder. Dynamically added at the end of the body and absolutely positioned under
        // the tooltip icon. Having the tooltip div outside of containers with non-visible overflow (like
        // dialogs), prevent it from being cut-off.
        tooltipHolder = $j('<div id="tooltipHolder"></div>').appendTo($j('body')).hide();
        $j('span.tooltiptext').hide();
        $j('i.tooltip').mouseover(function() {
            let tooltipText = $j(this).siblings('span.tooltiptext');
            tooltipHolder.html(tooltipText.html());
            tooltipHolder.css('top', $j(this).offset()['top'] + 25);
            tooltipHolder.css('left', $j(this).offset()['left'] - (tooltipHolder.width() / 2) + 8);
            tooltipHolder.show();
        }).mouseleave(function() {
            tooltipHolder.hide();
        })
    }

    function _bindHandlers() {
        Logger.logDebug('Binding handlers.');

        $useLocalInstance.change(_instanceChange);
        $useCustomInstance.change(_instanceChange);
        $useTls.change(_scheduleConnectionCheck);

        let useLocalInstance = $useLocalInstance.is(':checked');
        $dockerAddress.prop('disabled', useLocalInstance);
        if (useLocalInstance) {
            $dockerAddress.val(defaultLocalInstanceURI);
        }

        $checkConnectionBtn.click(_checkConnectionClickHandler);
        $newImageBtn.click(_showImageDialogClickHandler);

        $dockerAddress.change(function() {
            // Normalize the Docker address and do some auto-correction regarding count of slashes after the
            // scheme.
            let address = $dockerAddress.val();
            address = Utils.sanitizeURI(address, windowsHost);
            $dockerAddress.val(address);

            _scheduleConnectionCheck();
        });

        $imageDialogSubmitBtn.click(function() {
            _triggerAllFields(true);

            if(!_updateOkBtnState()) {
                return false;
            }

            let settings = _restoreSettings();

            let oldProfile = $imageDialogSubmitBtn.data('profile');
            let newProfile = settings.Administration.Profile;

            Logger.logDebug('Saving profile: ' + newProfile + ' (was: ' + oldProfile + ')');

            delete imagesData[oldProfile];
            imagesData[newProfile] = settings;

            _saveImagesData(oldProfile, newProfile);

            BS.DockerImageDialog.close();

            _renderImagesTable();
        });

        $imageDialogCancelBtn.click(function() {
            BS.DockerImageDialog.close();
        });

        $dialog.on('blur', 'input:text', _validateHandler);
        $dialog.on('change', 'input:not(input:text):not(input:button), select', _validateHandler);

        $imagesTable.on('click', '.dockerCloudDeleteBtn', function() {
            if (!confirm('Do you really want to delete this image ?')) {
                return;
            }
            let profile = $j(this).closest('tr').data('profile');
            Logger.logDebug('Deleting image: ' + profile);
            delete imagesData[profile];
            _saveImagesData();
            _renderImagesTable();
        });

        $imagesTable.on('click', '.dockerCloudSettingsBtn', function(evt) {
            _showDialog($j(this).closest('tr').data('profile'));
            evt.preventDefault();
            return false;
        });

        $imagesTable.on('click', '.dockerCloudAddBtn', function() {
            _showDialog();
        });

        $dialog.on('click', '.dockerCloudDeleteBtn', function() {
            let $row = $j(this).closest('tr');
            let $table = $row.closest('table');
            let tab = _getElementTab($row);
            // Clear error and warning messages related to this table row.
            $j('input, select', $row).each(function (i, val) {
                tab.clearMessages(val.id);
            });
            $row.remove();
            tableHelper.updateTableMandoryStarsVisibility($table);
        });


        $dialog.on('click', '.dockerCloudAddBtn', function(e) {
            // TODO: improve event binding. This handler will sometimes get called twice here if we do not
            // stop propagation.
            e.stopPropagation();

            let $elt = $j(this);

            // Fetch closest table.
            let $tableBody = $elt.closest('tbody');

            tableHelper.addTableRow($tableBody, schema.arrayTemplates);
        });

        $testContainerCreateBtn.click(function() {
            _testDialogHideAllBtns();

            $testContainerCancelBtn.show();
            $testContainerSuccessIcon.hide();
            $testContainerWarningIcon.hide();
            $testContainerErrorIcon.hide();

            // Pack the current image settings into a hidden field to be submitted.
            let settings = _restoreSettings();

            $testedImage.val(JSON.stringify(settings));

            $testContainerLabel.text('Starting test...');
            testPhase = 'CREATE';

            let params = BS.Clouds.Admin.CreateProfileForm.serializeParameters();
            _invokeTestAction('create', params)
                .done(function (response) {
                    testUuid = JSON.parse(response.responseText).testUuid;
                    _queryTestStatus();
                });
        });

        $testContainerStartBtn.click(function() {
            _testDialogHideAllBtns();
            $testContainerCancelBtn.show();
            $testContainerSuccessIcon.hide();
            $testContainerWarningIcon.hide();
            $testContainerErrorIcon.hide();

            $testContainerLabel.text('Waiting on server...');
            testPhase = 'START';
            _invokeTestAction('start')
                .done(function () {
                    _queryTestStatus();
                });
        });

        $testContainerCancelBtn.click(function() {
            _closeStatusSocket();
            testCancelled = true;
            $testContainerLoader.hide();
            $testContainerErrorIcon.show();
            $testContainerLabel.text('Cancelled by user.');
            $testContainerCancelBtn.hide();
            $testContainerCloseBtn.show();
        });

        $testContainerCloseBtn.click(function() {
            _cancelTest();
        });

        $testContainerContainerLogsBtn.click(function() {
            _invokeTestAction('logs', null, true)
                .done(function(response) {
                    let logs = JSON.parse(response.responseText).logs;
                    prepareDiagnosticDialog(i18n.text('test.logs'), logs);
                    $testContainerLoader.hide();
                    BS.DockerDiagnosticDialog.showCentered();
                });
        });

        BS.DockerTestContainerDialog.afterClose(_cancelTest);

        $imageTestContainerBtn.click(function() {

            _triggerAllFields(true);

            if(!_updateOkBtnState()) {
                return false;
            }

            _initTestDialog();
            BS.DockerTestContainerDialog.showCentered();
        });

        $diagnosticCloseBtn.click(function () {
            BS.DockerDiagnosticDialog.close();
        });

        $diagnosticCopyBtn.hide();
    }

    function _initDialogs() {
        BS.DockerImageDialog =
            OO.extend(BS.AbstractModalDialog, {
                getContainer: function () {
                    return $('DockerCloudImageDialog');
                }
            });

        BS.DockerTestContainerDialog =
            OO.extend(BS.AbstractModalDialog, {
                getContainer: function () {
                    return $('DockerTestContainerDialog');
                }
            });

        BS.DockerDiagnosticDialog =
            OO.extend(BS.AbstractModalDialog, {
                getContainer: function () {
                    return $('DockerDiagnosticDialog');
                }
            });
    }

    /* Render cloud images table. */

    function _renderImagesTable() {
        Logger.logDebug('Rendering image table.');
        $imagesTable.empty();

        let sortedKeys = [];

        Utils.safeKeyValueEach(imagesData, function(key, value) {
            sortedKeys.push(key);
        });

        sortedKeys.sort();

        $j.each(sortedKeys, function(i, val) {
            _renderImageRow(imagesData[val]);
        });
        templates.insertAddButton($imagesTable, 4);
    }

    function _renderImageRow(image) {
        let imageLabel = image.Administration.UseOfficialTCAgentImage ? 'Official TeamCity agent image' :
            schema.getImage(image.AgentHolderSpec);
        return $imagesTable.append($j('<tr><td>' + image.Administration.Profile + '</td>' +
            '<td>' + imageLabel + '</td>' +
            '<td class="center">' + (image.Administration.MaxInstanceCount ? image.Administration.MaxInstanceCount : 'unlimited') + '</td>' +
            '<td class="center">' + (image.Administration.RmOnExit ? 'Yes' : 'No') + '</td>' +
            '<td class="dockerCloudCtrlCell">' + templates.settingsCell + templates.deleteCell + '</td>' +
            '</tr>').data('profile', image.Administration.Profile));
    }

    /* Check connection with the server. */

    function _scheduleConnectionCheck(timeout) {
        if (checkConnectionTimeout) {
            clearTimeout(checkConnectionTimeout);
        }
        checkConnectionTimeout = setTimeout(_checkConnection, timeout);
    }

    function _checkConnection() {
        $checkConnectionResult.hide().removeClass('infoMessage errorMessage').empty();
        $checkConnectionWarning.hide().empty();

        if (!$dockerAddress.val()) {
            return;
        }

        _toggleCheckConnectionBtn();
        $checkConnectionLoader.show();

        let deferred = _queryDaemonInfo();

        deferred.fail(function (msg, failureCause) {
            Logger.logInfo('Checking connection failed');
            let $container = $checkConnectionResult.addClass('errorMessage').append($j('<div>').append('<span>'));
            $container.text(msg);
            if (failureCause) {
                _prepareDiagnosticDialogWithLink($container, 'Checking for connectivity failed.', failureCause);
            }
            $checkConnectionResult.append($container).show();
        }).done(function (daemonInfo) {

            effectiveApiVersion = daemonInfo.meta.effectiveApiVersion;
            daemonOs = daemonInfo.info.Os;

            $checkConnectionResult.addClass('infoMessage');
            $checkConnectionResult.text('Connection successful to Docker v' + daemonInfo.info.Version
                + ' (API v ' + effectiveApiVersion + ') on '
                + daemonInfo.info.Os + '/' + daemonInfo.info.Arch).show();

            if (Utils.compareVersionNumbers(effectiveApiVersion, daemonMinVersion) < 0 ||
                Utils.compareVersionNumbers(effectiveApiVersion, daemonTargetVersion) > 0) {
                $checkConnectionWarning
                    .append('Warning: daemon API version is outside of supported version range (v'
                        + daemonMinVersion + ' - v' + daemonTargetVersion + ').').show();

                // Prevent further version check.
                effectiveApiVersion = null;
            }
        }).always(function () {
            $checkConnectionLoader.hide();
            _toggleCheckConnectionBtn(true);
        });

        return false; // to prevent link with href='#' to scroll to the top of the page

    }

    function _toggleCheckConnectionBtn(enable) {
        $checkConnectionBtn.attr('disabled', !enable);
    }

    function _queryDaemonInfo() {
        let deferred = $j.Deferred();

        if (queryDaemonInfoReq) {
            Logger.logDebug('Aborting previous connection check.');
            queryDaemonInfoReq.abort();
        }

        let data = BS.Clouds.Admin.CreateProfileForm.serializeParameters();
        queryDaemonInfoReq = $j.ajax({
            url: checkConnectivityCtrlURL,
            method: 'POST',
            data: data,
            error: function(response) {
                let msg = response.statusText;
                switch (msg) {
                    case 'abort':
                        return;
                    case 'timeout':
                        msg = 'Server did not reply in time.'
                }

                deferred.reject(msg);
            },
            success: function(responseMap) {
                let error = responseMap.error;
                if (error) {
                    deferred.reject(error, responseMap.failureCause);
                } else {
                    deferred.resolve(responseMap);
                }
            },
            complete: function() {
                queryDaemonInfoReq = null;
            },
            timeout: 15000
        });
        return deferred;
    }

    /* Model handling. */

    function _loadImageData() {
        Logger.logDebug('Processing images data.');

        let json = $images.val();

        let images = json ? JSON.parse(json) : [];
        let imagesData = {};
        Logger.logDebug(images.length + ' images to be loaded.');
        $j.each(images, function(i, image) {
            schema.migrateSettings(image);
            image.Administration.Version = IMAGE_VERSION;
            imagesData[image.Administration.Profile] = image;
        });

        return imagesData;
    }

    function _saveImagesData(oldProfile, newProfile) {
        let tmp = [];
        Utils.safeKeyValueEach(imagesData, function (key, value) {
            tmp.push(value);
        });
        $images.val(JSON.stringify(tmp));

        _updateTCImageDetails(oldProfile, newProfile);
    }

    function _updateTCImageDetails(oldSourceId, newSourceId) {
        Logger.logDebug('Updating cloud image details (oldSourceId=' + oldSourceId + ', newSourceId=' +
            newSourceId + ').');
        let newTCImagesDetails = [];
        let json = $tcImagesDetails.val();
        let oldTCImagesDetails = [];
        if (json) {
            try { oldTCImagesDetails = JSON.parse(json) } catch (e) {
                Logger.logError('Failed to parse image details: ' + json);
            }
        }

        Utils.safeKeyValueEach(imagesData, function(name) {
            // If the profile name changed, then the source-id parameter in the image details must be
            // translated as well.
            let sourceImageName = name === newSourceId ? oldSourceId : name;
            let oldImageDetails = $j.grep(oldTCImagesDetails, function (imageDetails) {
                return imageDetails['source-id'] === sourceImageName;
            });
            let newImageDetails = oldImageDetails.length ? oldImageDetails[0] : {};
            newImageDetails['source-id'] = name;
            newTCImagesDetails.push(newImageDetails);
        });

        json = JSON.stringify(newTCImagesDetails);

        Logger.logDebug('Updated cloud image details: ' + json);

        $tcImagesDetails.val(json);
    }

    function _instanceChange(){
        let useLocalInstance = $useLocalInstance.is(':checked');
        $dockerAddress.val(useLocalInstance ? defaultLocalInstanceURI : "");
        $dockerAddress.prop('disabled', useLocalInstance);
        _scheduleConnectionCheck(500);
    }

    function _showDialog(profileName) {

        let existingImage = !!profileName;

        $imageDataDialogTitle.text((existingImage ? 'Edit' : 'Add') + ' Image');

        // Clear all errors.
        $j.each(tabbedPane.getTabs(), function (i, tab) {
            tab.clearAllMessages();
        });
        $j('span[id$="_error"], span[id$="_warning"]', $dialog).empty();

        let viewModel = schema.convertSettingsToViewModel(imagesData[profileName] || schema.initializeSettings({
            daemonOs: daemonOs
        }));
        viewModelAccessor.applyViewModel(viewModel);
        tableHelper.updateAllTablesMandoryStarsVisibility($dialogTables);

        _selectTabWithId('dockerCloudImageTab_general');
        tabbedPane.setActiveCaption('dockerCloudImageTab_general');

        if (existingImage) {
            $imageDialogSubmitBtn.val('Save')
                .data('image-id', profileName)
                .data('profile', profileName);
        } else {
            $imageDialogSubmitBtn.val('Add')
                .removeData('image-id')
                .removeData('profile');
        }

        // Update validation status and all other kinds of handler.
        _triggerAllFields(existingImage);

        BS.DockerImageDialog.showCentered();
    }

    function _triggerAllFields(blurTextFields) {
        if (blurTextFields) {
            // Blur all text fields to trigger 'required' validations.
            // Do not cache this selector to prevent problem with tables.
            $j('#DockerCloudImageDialog input:text').blur();
        }

        // Change all fields to trigger validation and the other handlers.
        $j('#DockerCloudImageDialog input:not(input:text):not(input:button), #DockerCloudImageDialog select').change();
    }

    function _restoreSettings() {
        let viewModel = viewModelAccessor.restoreViewModel();
        let settings = schema.convertViewModelToSettings(viewModel);

        settings.Administration.Version = IMAGE_VERSION;

        return settings;
    }

    function _validateHandler() {

        let $elt = $j(this);
        let eltId = _getEltId($elt);

        let validation;

        // Only validate fields that are not disabled.
        // Note: fields that are not visible must always be validated in order to perform cross-tabs
        // validation.
        if (!$elt.is(':disabled')) {
            let currentProfile = $imageDialogSubmitBtn.data('profile');
            let context = {
                profile: currentProfile,
                daemonOs: daemonOs,
                effectiveApiVersion: effectiveApiVersion,
                getImagesData: function() {
                    return imagesData;
                }
            };

            validation = validationHandler.validate($elt, _canonicalId(eltId), context);
        }

        let tab = _getElementTab($elt);

        let errorMsg = $j('#' + eltId + '_error').empty();
        let warningMsg = $j('#' + eltId + '_warning').empty();

        tab.clearMessages(eltId);

        if (validation) {
            if (validation.warnings.length) {
                $j.each(validation.warnings, function (i, warning) {
                    warningMsg.append('<p>' + warning + '</p>');
                });
                tab.addMessage(eltId, true)
            }
            if (validation.error) {
                errorMsg.append(validation.error);
                tab.addMessage(eltId, false)
            }
        }
    }

    /* Container tests management. */

    function _queryTestStatus() {
        if (testCancelled) {
            return;
        }
        if (!testUuid) {
            Logger.logError('Test UUID not resolved.');
            return;
        }

        if (hasWebSocketSupport) {
            if (!testStatusSocket) {
                Logger.logInfo('Opening test status listener socket.');
                let socketURL = _resolveWebSocketURL(testStatusSocketPath + '?testUuid=' + testUuid);
                testStatusSocket = new WebSocket(socketURL);
                testStatusSocket.onmessage = function (event) {
                    _processTestStatusResponse(_parseTestStatusResponse(event.data));
                }
            }
        } else {
            _invokeTestAction('query', BS.Clouds.Admin.CreateProfileForm.serializeParameters())
                .done(function(response){
                    let responseMap = _parseTestStatusResponse(response.responseText);
                    if (!responseMap || responseMap.status === 'PENDING') {
                        Logger.logDebug('Scheduling status retrieval.');
                        setTimeout(_queryTestStatus, 5000);
                    }
                    if (responseMap) {
                        _processTestStatusResponse(responseMap);
                    }
                });
        }
    }

    function _testDialogHideAllBtns() {
        $testContainerCreateBtn.hide();
        $testContainerStartBtn.hide();
        $testContainerContainerLogsBtn.hide();
        $testContainerDisposeBtn.hide();
        $testContainerShellBtn.hide();
    }

    function _initTestDialog() {
        _testDialogHideAllBtns();
        $dockerTestContainerOutputTitle.hide();
        $dockerTestContainerOutput.hide();
        $dockerTestContainerOutput.empty();
        $testContainerCreateBtn.show();
        $testContainerOutcome.text();
        $testContainerCancelBtn.hide();
        $testContainerCloseBtn.val('Close');
        $testContainerLoader.hide();
        $testContainerSuccessIcon.hide();
        $testContainerWarningIcon.hide();
        $testContainerErrorIcon.hide();
        $testContainerLabel.empty();
        $testExecInfo.empty();
        $testExecInfo.hide();
        $testContainerLabel.removeClass('containerTestError');
        $testContainerCancelBtn.attr('disabled', false);
        testCancelled = false;
        testPhase = null;
    }

    function _processTestStatusResponse(responseMap) {

        Logger.logDebug('Phase: ' + responseMap.phase + ' Status: ' + responseMap.status + ' Msg: ' +
            responseMap.msg + ' Container ID: ' + responseMap.containerId + ' Uuid: ' + responseMap.testUuid +
            ' Warnings: ' + responseMap.warnings.length);

        if (testPhase !== responseMap.phase) {
            Logger.logDebug('Ignoring spurious status message.');
            return;
        }
        $testContainerLabel.text(Utils.shortenString(responseMap.msg, 300));
        let agentStarted = responseMap.containerStartTime !== null &&
            responseMap.containerStartTime !== undefined;

        if (responseMap.status === 'PENDING') {
            if (agentStarted) {
                // Note: streaming log on Windows Docker daemons is currently not functional:
                // See: https://github.com/moby/moby/issues/30046
                if (hasXTermSupport && !logStreamingSocket && daemonOs !== 'windows') {
                    Logger.logInfo('Opening live logs socket now.');
                    let url = _resolveWebSocketURL(streamSocketPath + '?testUuid=' + testUuid);
                    logStreamingSocket = new WebSocket(url);
                    Promise.all([$dockerTestContainerOutputTitle.fadeIn(400).promise(),
                        // Compensate the appearance of the terminal with a upward shift of the dialog window.
                        // Should be roughly half of the terminal height include the title.
                        $testImageDialog.animate(
                            {top: '-=150px', left: '-=75px', width: '+=150px'}).promise()]).then(_ => {
                        try {
                            import(/* webpackChunkName: "XtermBundle" */ 'XtermBundle').then(xterm => {
                                $dockerTestContainerOutput.slideDown(400).promise().then(function () {
                                    try {
                                        let logTerm = new xterm();
                                        Logger.logInfo('Open and attach terminal now.');
                                        logTerm.open($dockerTestContainerOutput[0], false);
                                        logTerm.fit();
                                        logTerm.attach(logStreamingSocket);
                                        logTerm.convertEol = true;
                                    } catch (e) {
                                        Logger.logError(e);
                                    }
                                });
                            });
                        } catch (e) {
                            Logger.logError(e);
                        }
                    });
                }
            }
        } else {

            _testDialogHideAllBtns();
            _closeStatusSocket();

            $testContainerCancelBtn.hide();
            $testContainerLoader.hide();
            $testContainerCloseBtn.show();

            if (agentStarted) {
                $testContainerContainerLogsBtn.show();
                $testExecInfo.append('Note: you can access the running container by using the <code>exec</code> ' +
                    'command on the the Docker daemon host. For example: ' +
                    '<p class="mono">docker exec -t -i ' + responseMap.containerId + ' ' +
                    (daemonOs === 'windows' ? 'powershell' : '/bin/bash') +'</p>');
                $testExecInfo.slideDown();
            }

            if (responseMap.status === 'FAILURE') {
                if (responseMap.phase === 'CREATE') {
                    $testContainerCloseBtn.val('Close');
                } else {
                    $testContainerCloseBtn.val('Dispose container');
                }

                $testContainerLabel.addClass('containerTestError');
                $testContainerErrorIcon.show();

                if (responseMap.failureCause) {
                    _prepareDiagnosticDialogWithLink($testContainerLabel, responseMap.msg,
                        responseMap.failureCause);
                }

            } else if (responseMap.status === 'SUCCESS') {
                $testContainerCloseBtn.val('Dispose container');

                let hasWarning = !!responseMap.warnings.length;

                if (hasWarning) {
                    $testContainerWarningIcon.show();
                } else {
                    $testContainerSuccessIcon.show();
                }

                if (responseMap.phase === 'CREATE') {
                    $testContainerStartBtn.show();
                    if (hasWarning) {
                        $testContainerLabel.text(i18n.text('test.create.warning', responseMap.containerId));
                    } else {
                        $testContainerLabel.text(i18n.text('test.create.success', responseMap.containerId));
                    }
                } else if (responseMap.phase === 'START') {

                    if (hasWarning) {
                        $testContainerLabel.text(i18n.text('test.wait.warning', responseMap.containerId));
                    } else {
                        $testContainerLabel.text(i18n.text('test.wait.success', responseMap.containerId));
                    }
                }

                if (hasWarning) {
                    let $list = $j('<ul>');
                    Utils.safeEach(responseMap.warnings, function(warning) {
                        $list.append('<li>' + warning + '</li>');
                    });
                    $testContainerWarningIcon.show();
                    $testContainerLabel.append($list);
                }
            } else {
                Logger.logError('Unrecognized status: ' + responseMap.status);
            }
        }
    }

    function _cancelTest() {
        Logger.logDebug('Cancelling test: ' + testUuid);
        _closeStatusSocket();

        if (testUuid) {
            _invokeTestAction('cancel', null, true);
            testUuid = null;
        }

        BS.DockerTestContainerDialog.close();
    }

    function _closeStatusSocket() {
        if (testStatusSocket) {
            Logger.logInfo('Closing status socket.');
            try { testStatusSocket.close() } catch (e) {}
            testStatusSocket = null;
        }
        if (logStreamingSocket) {
            try { logStreamingSocket.close() } catch (e) {}
            logStreamingSocket = null;
        }
    }

    function _parseTestStatusResponse(json) {
        return JSON.parse(json).statusMsg;
    }

    function _invokeTestAction(action, parameters, immediate) {

        Logger.logDebug('Will invoke action ' + action + ' for test UUID ' + testUuid);

        let deferred = $j.Deferred();

        // Invoke test action.
        let url = testContainerCtrlURL + '?action=' + action;
        if (testUuid) {
            url += '&testUuid=' + testUuid;
        }

        if (!immediate) {
            $testContainerLoader.show();
            $testContainerCancelBtn.show();
            $testContainerCloseBtn.hide();
        }

        BS.ajaxRequest(url, {
            parameters: parameters,
            onSuccess: function (response) {
                deferred.resolve(response);
            },
            onFailure: function (response) {
                let txt;
                if (response.responseText.length > 150 || response.responseText.indexOf('<html>') !== -1) {
                    txt = response.statusText;
                } else {
                    txt = response.responseText;
                }
                deferred.reject(txt);
            }
        });

        if (!immediate) {
            deferred.fail(function(errorMsg) {
                _testDialogHideAllBtns();
                $testContainerCloseBtn.show();
                $testContainerLabel.text(errorMsg).addClass('containerTestError');
                $testContainerErrorIcon.show();
                $testContainerLoader.hide();
            });
        }

        return deferred;
    }

    /* Utility methods */

    function _idSel(id) {
        if (!id) {
            return id;
        }
        return '#' + id.toString().replace(/\./g,'\\.');
    }

    function _checkXtermBrowserSupport() {
        try {
            let parser = new UAParser.UAParser();
            parser.setUA(navigator.userAgent);

            const browser = parser.getBrowser();
            const name = browser.name;
            const versionToken = browser.version.split('.')[0];
            if (!versionToken.match(/[0-9]+/)) {
                return false;
            }
            const version = parseInt(versionToken);

            Logger.logDebug('Detected browser name: ' + name + ' -- version: ' + version);
            if ((name === 'Chrome' || name === 'Chromium') && version >= 48) {
                return true;
            }
            if (name === 'Firefox' && version >= 44) {
                return true;
            }
            if (name === 'IE' && version >= 11) {
                return true;
            }
            if (name === 'Edge' && version >= 13) {
                return true;
            }
            if (name === 'Opera' && version >= 8) {
                return true;
            }
            if (name === 'Safari' && version >= 35) {
                return true;
            }
        } catch (e) {
            Logger.logError('Failed to determine browser support: ' + e);
        }
    }

    function _getEltId($elt) {
        return $elt.attr('id') || $elt.attr('name');
    }

    function _canonicalId(id) {
        return Utils.trimIdPrefix(id.replace(/[0-9]+/, 'IDX'));
    }

    function _prepareDiagnosticDialogWithLink($container, msg, details) {
        let viewDetailsLink = $j('<a href="#/">view details</a>)').click(function () {
            prepareDiagnosticDialog(msg, details);
            BS.DockerDiagnosticDialog.showCentered();
        });
        $container.append(' (').append(viewDetailsLink).append(')');
    }

    function prepareDiagnosticDialog(msg, details) {
        $diagnosticMsg.text(Utils.shortenString(msg, 300));
        $diagnosticLogs.text(details);

        if (!clipboard) {
            Logger.logInfo('Clipboard operations enabled.');
            clipboard = new Clipboard('#dockerDiagnosticCopyBtn');
            clipboard.on('success', function(e) {
                e.clearSelection();
            });
            $diagnosticCopyBtn.show();
        }
    }

    function _resolveWebSocketURL(path) {
        let protocol = (location.protocol === 'https:') ? 'wss://' : 'ws://';
        let port =  ((location.port) ? (':' + location.port) : '');
        return protocol + location.hostname + port + path;
    }

    function _checkConnectionClickHandler() {
        if ($checkConnectionBtn.attr('disabled') !== 'true') { // it may be undefined
            _checkConnection();
        }

        return false; // to prevent link with href='#' to scroll to the top of the page
    }

    function _okBtnClicked(evt) {
        // Blur all text fields to trigger "required" validations.
        // Do not cache this selector to prevent problem with tables.
        $j('#DockerCloudImageDialog input:text').blur();
        if (!$j(this).is(':enabled')) {
            evt.preventDefault();
        }
    }

    function _updateOkBtnState() {
        let hasError = false;

        $j.each(tabbedPane.getTabs(), function (i, tab) {
            if (tab.errors.length) {
                hasError = true;
                return false;
            }
        });
        $imageDataOkBtn.prop('disabled', hasError);
        $imageTestContainerBtn.prop('disabled', hasError);
        return !hasError;
    }

    function _showImageDialogClickHandler() {
        if (!$newImageBtn.attr('disabled')) {
            _showDialog();
        }
        return false;
    }

    function _getElementTab(elt) {
        return tabbedPane.getTab(elt.closest('[id^="dockerCloudImageTab"]').attr('id'));
    }

    function _selectTab(tab) {
        _selectTabWithId(tab.getId());
    }

    function _selectTabWithId(id) {
        $j.each(tabs, function (i, val) {
            $j('#' + val.id).toggle(val.id === id);
        });
    }
}

module.exports = Controller;