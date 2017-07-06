
var BS = BS || {};
BS.Clouds = BS.Clouds || {};
BS.Clouds.Docker = BS.Clouds.Docker || (function () {

        //noinspection JSUnresolvedVariable
        var self = {
            IMAGE_VERSION: 4,
            selectors: {
                editImageLink: '.editImageLink',
                imagesTableRow: '.imagesTableRow'
            },
            init: function (params) {
                self.logInfo('Initializing Docker Cloud JS support.');

                self.defaultLocalSocketURI = params.defaultLocalSocketURI;
                self.checkConnectivityCtrlURL = params.checkConnectivityCtrlURL;
                self.testContainerCtrlURL = params.testContainerCtrlURL;
                self.testStatusSocketPath = params.testStatusSocketPath;
                self.streamSocketPath = params.streamSocketPath;
                self.errorIconURL = params.errorIconURL;
                self.warnIconURL = params.warnIconURL;
                self.debugEnabled = params.debugEnabled;
                self.daemonTargetVersion = params.daemonTargetVersion;
                self.daemonMinVersion = params.daemonMinVersion;

                var useTlsParam = params.useTlsParam;
                var imagesParam = params.imagesParam;
                var tcImagesDetailsParam = params.tcImagesDetails;

                self.hasWebSocketSupport = 'WebSocket' in window;
                self.hasXTermSupport = self.hasWebSocketSupport && self.checkXtermBrowserSupport();
                if (self.hasXTermSupport) {
                    self.logInfo("XTerm support enabled.")
                }

                self.$image = $j("#dockerCloudImage_Image");
                self.$registryUser = $j("#dockerCloudImage_RegistryUser");
                self.$registryPassword = $j("#dockerCloudImage_RegistryPassword");
                self.$checkConnectionBtn = $j("#dockerCloudCheckConnectionBtn");
                self.$checkConnectionResult = $j('#dockerCloudCheckConnectionResult');
                self.$checkConnectionWarning = $j('#dockerCloudCheckConnectionWarning');
                self.$checkConnectionInfo = $j('#dockerCloudCheckConnectionInfo');
                self.$newImageBtn = $j('#dockerShowDialogButton');
                self.$imageDialogSubmitBtn = $j('#dockerAddImageButton');
                self.$imageDialogCancelBtn = $j('#dockerCancelAddImageButton');
                self.$imagesTable = $j('#dockerCloudImagesTable');
                self.$useTls = $j(BS.Util.escapeId(useTlsParam));
                self.$images = $j(BS.Util.escapeId(imagesParam));
                self.$tcImagesDetails = $j(BS.Util.escapeId(tcImagesDetailsParam));
                self.$dockerAddress = $j("#dockerCloudDockerAddress");
                self.$useLocalInstance = $j("#dockerCloudUseLocalInstance");
                self.$useCustomInstance = $j("#dockerCloudUseCustomInstance");
                self.$checkConnectionLoader = $j('#dockerCloudCheckConnectionLoader');

                self.$imageDataOkBtn = $j("#dockerAddImageButton");
                self.$imageDataDialogTitle = $j("#DockerImageDialogTitle");
                self.$warnAutoBind = $j("#dockerCloudWarnAutoBind");
                self.$swapUnit = $j("#dockerCloudImage_MemorySwapUnit");
                self.$swapUnlimited = $j("#dockerCloudImage_MemorySwapUnlimited");
                self.$memoryUnit = $j("#dockerCloudImage_MemoryUnit");
                self.$memory = $j("#dockerCloudImage_Memory");
                self.$rmOnExit = $j("#dockerCloudImage_RmOnExit");
                self.$dialog = $j("#DockerCloudImageDialog");
                self.$dialogTables = $j('table', self.$dialog);
                self.$useOfficialDockerImage = $j('#dockerCloudImage_UseOfficialTCAgentImage');

                /* Test container */
                self.$imageTestContainerBtn = $j("#dockerTestImageButton");
                self.$testImageDialog = $j("#DockerTestContainerDialog");
                self.$testContainerCreateBtn = $j("#dockerCreateImageTest");
                self.$testContainerStartBtn = $j("#dockerStartImageTest");
                self.$testContainerLoader = $j('#dockerCloudTestContainerLoader');
                self.$testContainerLabel = $j('#dockerCloudTestContainerLabel');
                self.$testExecInfo = $j('#dockerTestExecInfo');
                self.$testContainerOutcome = $j('#dockerCloudTestContainerOutcome');
                self.$testContainerShellBtn = $j('#dockerCloudTestContainerShellBtn');
                self.$testContainerContainerLogsBtn = $j('#dockerCloudTestContainerContainerLogsBtn');
                self.$testContainerDisposeBtn = $j('#dockerCloudTestContainerDisposeBtn');
                self.$testContainerCancelBtn = $j('#dockerCloudTestContainerCancelBtn');
                self.$testContainerCloseBtn = $j('#dockerCloudTestContainerCloseBtn');
                self.$testContainerSuccessIcon = $j('#dockerCloudTestContainerSuccess');
                self.$testContainerWarningIcon = $j('#dockerCloudTestContainerWarning');
                self.$testContainerErrorIcon = $j('#dockerCloudTestContainerError');
                self.$dockerTestContainerOutput = $j("#dockerTestContainerOutput");
                self.$dockerTestContainerOutputTitle = $j("#dockerTestContainerOutputTitle");
                self.$testedImage = $j(BS.Util.escapeId("run.var.teamcity.docker.cloud.tested_image"));

                /* Diagnostic dialog */
                self.$diagnosticMsg = $j('#dockerCloudTestContainerErrorDetailsMsg');
                self.$diagnosticLogs = $j('#dockerCloudTestContainerErrorDetailsStackTrace');
                self.$diagnosticCopyBtn = $j('#dockerDiagnosticCopyBtn');
                self.$diagnosticCloseBtn = $j('#dockerDiagnosticCloseBtn');

                self._initImagesData();
                self._initDaemonInfo();
                self._initTabs();
                self._initValidators();
                self._bindHandlers();
                self._renderImagesTable();
                self._setupTooltips();
            },

            /* MAIN SETTINGS */

            _initDaemonInfo: function() {
                // Simple heuristic to check if this is a new cloud profile. At least one image must be saved for
                // existing profiles.
                var existingProfile = Object.keys(self.imagesData).length;

                if (existingProfile) {
                    setTimeout(self._checkConnection, 0);
                }
            },

            _renderImagesTable: function () {
                self.logDebug("Rendering image table.");
                self.$imagesTable.empty();

                var sortedKeys = [];

                self._safeKeyValueEach(self.imagesData, function(key, value) {
                    sortedKeys.push(key);
                });

                sortedKeys.sort();

                $j.each(sortedKeys, function(i, val) {
                    self._renderImageRow(self.imagesData[val]);
                });
                self._insertAddButton(self.$imagesTable, 4);
            },

            _setupTooltips: function() {
                self.logDebug("Setup help tooltip.");

                // Our tooltip div holder. Dynamically added at the end of the body and absolutely positioned under
                // the tooltip icon. Having the tooltip div outside of containers with non-visible overflow (like
                // dialogs), prevent it from being cut-off.
                self.tooltipHolder = $j('<div id="tooltipHolder"></div>').appendTo($j('body')).hide();
                $j('span.tooltiptext').hide();
                $j('i.tooltip').mouseover(function() {
                    var tooltipText = $j(this).siblings('span.tooltiptext');
                    self.tooltipHolder.html(tooltipText.html());
                    self.tooltipHolder.css('top', $j(this).offset()['top'] + 25);
                    self.tooltipHolder.css('left', $j(this).offset()['left'] - (self.tooltipHolder.width() / 2) + 8);
                    self.tooltipHolder.show();
                }).mouseleave(function() {
                    self.tooltipHolder.hide();
                })
            },
            _renderImageRow: function (image) {
                var imageLabel = image.Administration.UseOfficialTCAgentImage ? "Official TeamCity agent image" :
                    image.Container.Image;
                return self.$imagesTable.append($j('<tr><td>' + image.Administration.Profile + '</td>' +
                    '<td>' + imageLabel + '</td>' +
                    '<td class="center">' + (image.Administration.MaxInstanceCount ? image.Administration.MaxInstanceCount : 'unlimited') + '</td>' +
                    '<td class="center">' + (image.Administration.RmOnExit ? 'Yes' : 'No') + '</td>' +
                    '<td class="dockerCloudCtrlCell">' + self.arrayTemplates.settingsCell + self.arrayTemplates.deleteCell + '</td>' +
                    '</tr>').data('profile', image.Administration.Profile));
            },
            _instanceChange: function() {
                var useLocalInstance = self.$useLocalInstance.is(':checked');
                self.$dockerAddress.val(useLocalInstance ? self.defaultLocalSocketURI : "");
                self.$dockerAddress.prop('disabled', useLocalInstance);
                self._scheduleConnectionCheck();
            },
            _scheduleConnectionCheck: function() {
                if (self.checkConnectionTimeout) {
                    clearTimeout(self.checkConnectionTimeout);
                }
                self.checkConnectionTimeout = setTimeout(self._checkConnection, 500);
            },
            _checkConnection: function () {
                self.$checkConnectionResult.hide().removeClass('infoMessage errorMessage').empty();
                self.$checkConnectionWarning.hide().empty();

                if (!self.$dockerAddress.val()) {
                    return;
                }

                self._toggleCheckConnectionBtn();
                self.$checkConnectionLoader.show();

                var deferred = self._queryDaemonInfo();

                deferred.
                fail(function(msg, failureCause) {
                    self.logInfo("Checking connection failed");
                    var $container = self.$checkConnectionResult.addClass('errorMessage').append($j('<div>').append('<span>'));
                    $container.text(msg);
                    if (failureCause) {
                        self.prepareDiagnosticDialogWithLink($container, "Checking for connectivity failed.", failureCause);
                    }
                    self.$checkConnectionResult.append($container).show();
                }).
                done(function(daemonInfo) {
                    var effectiveApiVersion = daemonInfo.meta.effectiveApiVersion;

                    self.$checkConnectionResult.addClass('infoMessage');
                    self.$checkConnectionResult.text('Connection successful to Docker v' + daemonInfo.info.Version
                    + ' (API v ' + effectiveApiVersion + ') on '
                    + daemonInfo.info.Os + '/' + daemonInfo.info.Arch).show();

                    if (self.compareVersionNumbers(effectiveApiVersion, self.daemonMinVersion) < 0 ||
                        self.compareVersionNumbers(effectiveApiVersion, self.daemonTargetVersion) > 0) {
                        self.$checkConnectionWarning
                            .append('Warning: daemon API version is outside of supported version range (v'
                                + self.daemonMinVersion + " - v" + self.daemonTargetVersion + ").").show();

                        // Prevent further version check.
                        effectiveApiVersion = null;
                    }

                    self.effectiveApiVersion = effectiveApiVersion;
                }).
                always(function () {
                    self.$checkConnectionLoader.hide();
                    self._toggleCheckConnectionBtn(true);
                });

                return false; // to prevent link with href='#' to scroll to the top of the page
            },

            _queryDaemonInfo: function() {
                var deferred = $j.Deferred();

                if (self.queryDaemonInfoReq) {
                    self.logDebug("Aborting previous connection check.");
                    self.queryDaemonInfoReq.abort();
                }

                var data = self.queryDaemonInfoReqData = BS.Clouds.Admin.CreateProfileForm.serializeParameters();
                    self.queryDaemonInfoReq = $j.ajax({
                    url: self.checkConnectivityCtrlURL,
                    method: 'POST',
                    data: data,
                    error: function(response) {
                        var msg = response.statusText;
                        switch (msg) {
                            case 'abort':
                                return;
                            case 'timeout':
                                msg = 'Server did not reply in time.'
                        }

                        deferred.reject(msg);
                    },
                    success: function(responseMap) {
                        var error = responseMap.error;
                        if (error) {
                            deferred.reject(error, responseMap.failureCause);
                        } else {
                            deferred.resolve(responseMap);
                        }
                    },
                    complete: function() {
                      delete self.queryDaemonInfoReq;
                    },
                    timeout: 15000
                });
                return deferred;
            },

            /* IMAGE DATA MARSHALLING / UNMARSHALLING */

            _initImagesData: function () {
                self.logDebug("Processing images data.");

                var json = self.$images.val();

                var images = json ? JSON.parse(json) : [];
                self.imagesData = {};
                self.logDebug(images.length + " images to be loaded.");
                $j.each(images, function(i, image) {
                    self._migrateImagesData(image);
                    self.imagesData[image.Administration.Profile] = image;
                });

                // Update the image details when the configuration is initially loaded.
                self._writeImages();
                self._updateTCImageDetails();
            },

            _migrateImagesData: function(imageData) {
                var editor;
                switch(imageData.Administration.Version) {
                    case 1:
                        // V1: 'Binds' must be exported from the container configuration into the editor configuration,
                        // where they will not be stored using the Docker syntax ([host_path]:[container_path]:[mode])
                        // but splitted into JSON fields. This allow us to avoid to handle specially with colons in
                        // filename for unix (see docker issue #8604, still open today) and windows drive letters
                        // (solved in Docker using complexes regexes).
                        self.logInfo("Performing migration to version 2.");
                        var container = imageData.Container || {};
                        var hostConfig = container.HostConfig || {};
                        editor = imageData.Editor || {};
                        imageData.Editor = editor;

                        editor.Binds = [];

                        self._safeEach(hostConfig.Binds, function(bind) {
                            self.logDebug("Processing: " + bind);
                            var tokens = bind.split(':');
                            if (tokens.length > 3) {
                                // We are in difficulty as soon as we have more than three tokens: we will then not
                                // evaluate the whole binding definition. This is less crucial for unix file paths,
                                // because the Docker daemon will consider such definition invalid and reject them
                                // anyway.
                                // For Windows file paths, we apply a simple heuristic that should be "good enough":
                                // if a definition token looks like a drive letter then we merge it with the following
                                // token.
                                var copy = tokens.slice();
                                var newTokens = [];
                                var mode = copy.pop();
                                while(copy.length) {
                                    var token = copy.shift();
                                    if (token.match('^[a-zA-Z0-9]$') && copy.length) {
                                        token += ':' + copy.shift();
                                    }
                                    newTokens.push(token);
                                }
                                if (newTokens.length >= 2 && (mode === 'ro' || mode === 'rw')) {
                                    tokens = [newTokens[0], newTokens[1], mode];
                                    self.logInfo("Binding fix attempt: " + newTokens[0] + ":" + newTokens[1] + ":" + mode);
                                }
                            }
                            editor.Binds.push({ PathOnHost: tokens[0], PathInContainer: tokens[1],  ReadOnly: tokens[2] });
                        });
                    case 2:
                        self.logInfo("Performing migration to version 3.");
                        imageData.Administration.PullOnCreate = true;
                    case 3:
                        self.logInfo("Performing migration to version 4.");

                        editor = imageData.Editor || {};
                        self.migrationInfo = [];
                        imageData.Editor = editor;
                        $j([
                            {value: 'Memory', unit: 'MemoryUnit'},
                            {value: 'MemorySwap', unit: 'MemorySwapUnit'}]).each(function(i, mem) {
                                var value;
                                if (imageData.Container && imageData.Container.HostConfig) {
                                    value = imageData.Container.HostConfig[mem.value];
                                }
                                var unit = editor[mem.unit];
                                if (unit === 'MiB' || unit === 'GiB') {
                                    self.migrationInfo.push(editor);
                                    if (value && value !== -1) {
                                        imageData.Container.HostConfig[mem.value] = value * 8;
                                    }
                                }
                        });
                        imageData.Administration.Version = self.IMAGE_VERSION;
                    case self.IMAGE_VERSION:
                        break;
                    default:
                        self.logInfo("Warning: unsupported configuration version " + imageData.Administration.Version);
                }
            },

            _writeImages: function() {
                var tmp = [];
                self._safeKeyValueEach(self.imagesData, function(key, value) {
                    tmp.push(value);
                });
                self.$images.val(JSON.stringify(tmp));
            },

            _updateTCImageDetails: function(oldSourceId, newSourceId) {

                self.logDebug("Updating cloud image details (oldSourceId=" + oldSourceId + ", newSourceId=" +
                    newSourceId + ").");
                var newTCImagesDetails = [];
                var json = self.$tcImagesDetails.val();
                var oldTCImagesDetails = [];
                if (json) {
                    try { oldTCImagesDetails = JSON.parse(json) } catch (e) {
                        self.logError("Failed to parse image details: " + json);
                    }
                }

                self._safeKeyValueEach(self.imagesData, function(name) {
                    // If the profile name changed, then the source-id parameter in the image details must be
                    // translated as well.
                    var sourceImageName = name === newSourceId ? oldSourceId : name;
                    var oldImageDetails = $j.grep(oldTCImagesDetails, function (imageDetails) {
                        return imageDetails['source-id'] === sourceImageName;
                    });
                    var newImageDetails = oldImageDetails.length ? oldImageDetails[0] : {};
                    newImageDetails['source-id'] = name;
                    newTCImagesDetails.push(newImageDetails);
                });

                json = JSON.stringify(newTCImagesDetails);

                self.logDebug("Updated cloud image details: " + json);

                self.$tcImagesDetails.val(json);
            },

            _updateAllTablesMandoryStarsVisibility: function() {
                self.$dialogTables.each(function(i, table) {
                    self._updateTableMandoryStarsVisibility($j(table));
                });
            },

            _updateTableMandoryStarsVisibility: function($table) {
                $j(".mandatoryAsterix", $table).toggle($j('input, select', $table).length > 0);
            },

            _triggerAllFields: function(blurTextFields) {
                if (blurTextFields) {
                    // Blur all text fields to trigger "required" validations.
                    // Do not cache this selector to prevent problem with tables.
                    $j("#DockerCloudImageDialog input:text").blur();
                }

                // Change all fields to trigger validation and the other handlers.
                $j("#DockerCloudImageDialog input:not(input:text):not(input:button), #DockerCloudImageDialog select").change();
            },

            _applyViewModel: function(viewModel) {
                $j('[id^="dockerCloudImage_"], [name^="dockerCloudImage_"]').each(function(i, elt) {
                    var $elt = $j(elt);
                    // TODO: handle attribute name according to input type.
                    var key = $elt.attr('id') || $elt.attr('name');
                    var match = /^dockerCloudImage_([^_]+)$/.exec(key);
                    if (match) {
                        self._applyViewModelGroup(viewModel, match[1], $elt);
                    }
                });
            },
            _applyViewModelGroup: function(parentObject, key, $elt) {
                var tagName = $elt.prop("tagName");
                if (tagName === "TBODY") {
                    var arrayTemplate = self.arrayTemplates['dockerCloudImage_' + key];
                    var colCount = arrayTemplate.match(/<td/g).length;
                    // Clear the table content.
                    $elt.empty();
                    var value = parentObject[key];
                    self._safeEach(value, function(val) {
                        var $row = $j('<tr>').appendTo($elt);
                        var index = $elt.data("index") || 0;
                        $elt.data("index", index + 1);

                        $row.append(arrayTemplate.replace(/IDX/g, index));
                        var $rowItems = $j('input, select', $row);
                        if ($rowItems.length == 1) {
                            $rowItems.each(function(i, rowItem) {
                                $j(rowItem).val(val);
                            });
                        } else {
                            $rowItems.each(function(i, rowItem) {
                                var $rowItem  = $j(rowItem);
                                var regex = new RegExp('^dockerCloudImage_' + key + '_[0-9]+_(.*)$');
                                var match = regex.exec($rowItem.attr('id'));
                                if (match) {
                                    self._applyViewModelGroup(val, match[1], $rowItem);
                                }
                            });
                        }
                        $row.append('<td class="center dockerCloudCtrlCell">' + self.arrayTemplates.deleteCell + '</td>');

                    });
                    self._insertAddButton($elt, colCount);
                } else if ($elt.is(':text') || $elt.is(':password')) {
                    $elt.val(parentObject[key]);
                } else if ($elt.is(':checkbox')) {
                    $elt.prop('checked', parentObject[key] === true);
                } else if ($elt.is(':radio')) {
                    $elt.prop('checked', parentObject[key] == $elt.val());
                } else if (tagName == 'SELECT') {
                    $elt.val(parentObject[key]);
                } else {
                    self.logError("Unhandled tag type: " + tagName);
                }
            },

            _insertAddButton: function($tbody, colspan) {
                $tbody.append('<tr class="dockerCloudAddItem"><td colspan="' + colspan + '" class="dockerCloudCtrlCell"> <a class="btn dockerCloudAddBtn" href="#/"  title="Add item"><span class="dockerCloudAddBtn">Add</span></a></td></tr>');
            },
            showDialog: function (profileName) {

                var existingImage = !!profileName;

                self.$imageDataDialogTitle.text((existingImage ? 'Edit' : 'Add') + ' Image');

                // Clear all errors.
                $j.each(self.imageDataTabbedPane.getTabs(), function (i, tab) {
                    tab.clearAllMessages();
                });
                $j('span[id$="_error"], span[id$="_warning"]', self.$dialog).empty();

                var viewModel = self._convertSettingsToViewModel(self.imagesData[profileName] || {
                        /* Defaults for new images. */
                        Administration: {
                            UseOfficialTCAgentImage: true,
                            PullOnCreate: true,
                            MaxInstanceCount: 2
                        },
                        Editor: {
                            MemoryUnit: 'bytes',
                            MemorySwapUnit: 'bytes'
                        }
                    });
                self._applyViewModel(viewModel);
                self._updateAllTablesMandoryStarsVisibility();
                self.selectTabWithId("dockerCloudImageTab_general");
                self.imageDataTabbedPane.setActiveCaption("dockerCloudImageTab_general");

                if (existingImage) {
                    self.$imageDialogSubmitBtn.val('Save')
                        .data('image-id', profileName)
                        .data('profile', profileName);
                } else {
                    self.$imageDialogSubmitBtn.val('Add')
                        .removeData('image-id')
                        .removeData('profile');
                }

                BS.DockerImageDialog.showCentered();

                // Update validation status and all other kinds of handler. This must be done after showing the dialog
                // since validation will skip elements that are not visible.
                self._triggerAllFields(existingImage);
            },
            _showImageDialogClickHandler: function () {
                if (!self.$newImageBtn.attr('disabled')) {
                    self.showDialog();
                }
                return false;
            },

            _nullSafeGet: function(array, path) {
                if (path.length) {
                    var value = array.get(path[0]);
                    if (value) {
                        if (path.length == 1) {
                            return value;
                        }
                        path.splice(0);
                        return self._nullSafeGet(array, path);

                    }
                }
            },

            _isArray: function(object) {
                return $j.type(object) === "array";
            },

            _safeEach: function(array, fn) {
                if (self._isArray(array)) {
                    $j.each(array, function(i, val) {
                        fn(val);
                    });
                }
            },

            _safeKeyValueEach: function(array, fn) {
                if (array) {
                    $j.each(array, function (key, value) {
                        fn(key, value);
                    });
                }
            },

            _restoreViewModel: function(){
                var viewModel = {};
                $j('[id^="dockerCloudImage_"], [name^="dockerCloudImage_"]').each(function(i, elt) {
                    var $elt = $j(elt);
                    var id = $elt.attr('id') || $elt.attr('name');
                    var match = /^dockerCloudImage_([^_]+)$/.exec(id);
                    if (match) {
                        self._restoreViewModelGroup(viewModel, match[1], $elt);
                    }
                });
                return viewModel;
            },
            _restoreViewModelGroup: function(parentObject, key, $elt){
                var tagName = $elt.prop('tagName');
                if (tagName === "TBODY") {
                    $j('tr', $elt).each(function (i, row) {

                        var $row = $j(row);
                        var $rowItems = $j('input, select', $row);

                        var rowObject = parentObject[key];
                        if (!rowObject) {
                            rowObject = parentObject[key] = [];
                        }

                        if (!$rowItems.length) {
                            // Filter table rows without items (eg. table rows holding controls).
                            return;
                        }

                        if ($rowItems.length == 1) {
                            $rowItems.each(function (i, rowItem) {
                                parentObject[key].push($j(rowItem).val());
                            });
                        } else {
                            var childObject = {};
                            $rowItems.each(function (i, rowItem) {
                                var $rowItem = $j(rowItem);
                                var regex = new RegExp('^dockerCloudImage_' + key + '_[0-9]+_(.*)$');
                                var match = regex.exec($rowItem.attr('id'));
                                if (match) {
                                    self._restoreViewModelGroup(childObject, match[1], $rowItem);
                                }
                            });
                            rowObject.push(childObject);
                        }
                    });
                } else if (tagName == 'SELECT') {
                    parentObject[key] = $elt.val();
                } else if ($elt.is(':text') || $elt.is(':password')) {
                    parentObject[key] = $elt.val();
                } else if ($elt.is(':checkbox')) {
                    parentObject[key] = $elt.is(':checked');
                } else if ($elt.is(':radio')) {
                    if ($elt.is(':checked')) {
                        parentObject[key] = $elt.attr('value');
                    }
                }
            },

            _convertViewModelToSettings: function(viewModel) {
                var settings = {};

                var admin = settings.Administration = {};
                var editor = {};

                admin.Version = self.IMAGE_VERSION;

                self.copy(viewModel, admin, 'RmOnExit');
                self.copy(viewModel, admin, 'PullOnCreate');

                if (self.notEmpty(viewModel.MaxInstanceCount)) {
                    admin.MaxInstanceCount = parseInt(viewModel.MaxInstanceCount);
                }
                self.copy(viewModel, admin, 'UseOfficialTCAgentImage');
                self.copy(viewModel, admin, 'RegistryUser');
                self.copy(viewModel, admin, 'RegistryPassword', self.base64Utf16BEEncode);
                self.copy(viewModel, admin, 'Profile');

                var container = {};
                self.copy(viewModel, container, 'Hostname');
                self.copy(viewModel, container, 'Domainname');
                self.copy(viewModel, container, 'User');

                if (self.notEmpty(viewModel.Env)) {
                    container.Env = [];
                    self._safeEach(viewModel.Env, function (envEntry) {
                        container.Env.push(envEntry.Name + '=' + envEntry.Value);
                    });
                }

                if (self.notEmpty(viewModel.Labels)) {
                    container.Labels = {};
                    self._safeEach(viewModel.Labels, function (label) {
                        container.Labels[label.Key] = label.Value;
                    });
                }

                self.copy(viewModel, container, 'Cmd');
                self.copy(viewModel, container, 'Entrypoint');
                self.copy(viewModel, container, 'Image');

                if (self.notEmpty(viewModel.Volumes)) {
                    var volumes = {};
                    self._safeEach(viewModel.Volumes, function (volume) {
                        if (!volume.PathOnHost) {
                            volumes[volume.PathInContainer] = {};
                        }
                    });
                    if (Object.keys(volumes).length) {
                        container.Volumes = volumes;
                    }
                }

                self.copy(viewModel, container, 'WorkingDir');

                if (self.notEmpty(viewModel.Ports)) {
                    var exposedPorts = {};
                    self._safeEach(viewModel.Ports, function (port) {
                        if (!port.HostIp && !port.HostPort) {
                            container.ExposedPorts[port.HostPort + '/' + port.Protocol] = {};
                        }
                    });
                    if (Object.keys(exposedPorts).length) {
                        container.ExposedPorts = exposedPorts;
                    }
                }

                self.copy(viewModel, container, 'StopSignal');

                if (self.notEmpty(viewModel.StopTimeout)) {
                    container.StopTimeout = parseInt(viewModel.StopTimeout);
                }

                var hostConfig = {};

                if (self.notEmpty(viewModel.Volumes)) {
                    var editorBinds = [];
                    var hostConfigBinds = [];
                    self._safeEach(viewModel.Volumes, function (volume) {
                        if (volume.PathOnHost) {
                            var readOnly = volume.ReadOnly ? 'ro' : 'rw';
                            hostConfigBinds.push(volume.PathOnHost + ':' + volume.PathInContainer + ':' + readOnly);
                            editorBinds.push({ PathOnHost: volume.PathOnHost,
                                PathInContainer: volume.PathInContainer, ReadOnly: readOnly});
                        }
                    });

                    if (editorBinds.length) {
                        editor.Binds = editorBinds;
                        hostConfig.Binds = hostConfigBinds;
                    }
                }

                if (self.notEmpty(viewModel.Links)) {
                    hostConfig.Links = [];
                    self._safeEach(viewModel.Links, function (link) {
                        hostConfig.Links.push(link.Container + ':' + link.Alias);
                    });
                }

                if (self.notEmpty(viewModel.Memory)) {
                    hostConfig.Memory = parseInt(viewModel.Memory) * self._units_multiplier[viewModel.MemoryUnit];
                }

                if (viewModel.MemorySwapUnlimited) {
                    hostConfig.MemorySwap = -1;
                } else if (self.notEmpty(viewModel.MemorySwap)) {
                    hostConfig.MemorySwap = parseInt(viewModel.MemorySwap) * self._units_multiplier[viewModel.MemorySwapUnit];
                }
                if (self.notEmpty(viewModel.CPUs)) {
                    hostConfig.NanoCPUs = Math.floor(parseFloat(viewModel.CPUs) * 1e9);
                }
                if (self.notEmpty(viewModel.CpuQuota)) {
                    hostConfig.CpuQuota = parseInt(viewModel.CpuQuota);
                }
                if (self.notEmpty(viewModel.CpuShares)) {
                    hostConfig.CpuShares = parseInt(viewModel.CpuShares);
                }
                if (self.notEmpty(viewModel.CpuPeriod)) {
                    hostConfig.CpuPeriod = parseInt(viewModel.CpuPeriod);
                }
                self.copy(viewModel, hostConfig, 'CpusetCpus');
                self.copy(viewModel, hostConfig, 'CpusetMems');
                if (self.notEmpty(viewModel.BlkioWeight)) {
                    hostConfig.BlkioWeight = parseInt(viewModel.BlkioWeight);
                }
                self.copy(viewModel, hostConfig, 'OomKillDisable');

                if (self.notEmpty(viewModel.Ports)) {
                    var portBindings = {};
                    self._safeEach(viewModel.Ports, function (port) {
                        if (port.HostIp || port.HostPort) {
                            var key = port.ContainerPort + '/' + port.Protocol;
                            var binding = portBindings[key];
                            if (!binding) {
                                binding = portBindings[key] = [];
                            }
                            binding.push({HostIp: port.HostIp, HostPort: port.HostPort});
                        }
                    });

                    if (Object.keys(portBindings).length) {
                        hostConfig.PortBindings = portBindings;
                    }
                }

                self.copy(viewModel, hostConfig, 'PublishAllPorts');
                self.copy(viewModel, hostConfig, 'Privileged');
                self.copy(viewModel, hostConfig, 'Dns');
                self.copy(viewModel, hostConfig, 'DnsSearch');

                if (self.notEmpty(viewModel.ExtraHosts)) {
                    hostConfig.ExtraHosts = [];
                    self._safeEach(viewModel.ExtraHosts, function (extraHost) {
                        hostConfig.ExtraHosts.push(extraHost.Name + ':' + extraHost.Ip);
                    });
                }

                self.copy(viewModel, hostConfig, 'CapAdd');
                self.copy(viewModel, hostConfig, 'CapDrop');

                if (self.notEmpty(viewModel.NetworkMode)) {
                    var networkMode = viewModel.NetworkMode;
                    if (networkMode === 'bridge' || networkMode === 'host' || networkMode === 'none') {
                        hostConfig.NetworkMode = networkMode;
                    } else if (networkMode === 'container') {
                        hostConfig.NetworkMode = 'container:' + viewModel.NetworkContainer;
                    } else if (networkMode) {
                        hostConfig.NetworkMode = viewModel.NetworkCustom;
                    }
                }

                self.copy(viewModel, hostConfig, 'Devices');
                self.copy(viewModel, hostConfig, 'Ulimits');

                if (self.notEmpty(viewModel.Ulimits)) {
                    hostConfig.Ulimits = [];
                    self._safeEach(viewModel.Ulimits, function (ulimit) {
                        hostConfig.Ulimits.push({ Name: ulimit.Name, Hard: parseInt(ulimit.Hard), Soft: parseInt(ulimit.Soft)});
                    });
                }

                if (self.notEmpty(viewModel.LogType)) {
                    var config = {};
                    hostConfig.LogConfig = {
                        Type: viewModel.LogType
                    };

                    self._safeEach(viewModel.LogConfig, function (logConfig) {
                        config[logConfig.Key] = logConfig.Value;
                    });

                    if (Object.keys(config).length) {
                        hostConfig.LogConfig.Config = config;
                    }
                }

                if (self.notEmpty(viewModel.StorageOpt)) {
                    hostConfig.StorageOpt = {};
                    self._safeEach(viewModel.StorageOpt, function (storageOpt) {
                        hostConfig.StorageOpt[storageOpt.Key] = storageOpt.Value;
                    });
                }

                self.copy(viewModel, hostConfig, 'CgroupParent');
                self.copy(viewModel, editor, 'MemoryUnit');
                self.copy(viewModel, editor, 'MemorySwapUnit');

                if (Object.keys(hostConfig).length) {
                    container.HostConfig = hostConfig;
                }
                if (Object.keys(container).length) {
                    settings.Container = container;
                }
                if (Object.keys(editor).length) {
                    settings.Editor = editor;
                }

                return settings;
            },
            copy: function(source, target, fieldName, conversionFunction) {
                var value = source[fieldName];
                if (self.notEmpty(value)) {
                    target[fieldName] =  conversionFunction ? conversionFunction(value) : value;
                }
            },
            notEmpty: function(value) {
                return value !== undefined && value !== null && (value.length === undefined || value.length) && ($j.type(value) != "object" || Object.keys(value).length);
            },
            _convertSettingsToViewModel: function(settings) {
                var viewModel = {};

                var admin = settings.Administration || {};
                var container = settings.Container || {};
                var hostConfig = container.HostConfig || {};
                var editor = settings.Editor || {};

                self.copy(admin, viewModel, 'Profile');
                self.copy(admin, viewModel, 'PullOnCreate');
                self.copy(admin, viewModel, 'RmOnExit');
                self.copy(admin, viewModel, 'MaxInstanceCount');
                self.copy(admin, viewModel, 'UseOfficialTCAgentImage');
                self.copy(admin, viewModel, 'RegistryUser');

                self.copy(admin, viewModel, 'RegistryPassword', self.base64Utf16BEDecode);

                self.copy(container, viewModel, 'Hostname');
                self.copy(container, viewModel, 'Domainname');
                self.copy(container, viewModel, 'User');

                var env = [];
                self._safeEach(container.Env, function(envEntry) {
                    var sepIndex = envEntry.indexOf('=');
                    if (sepIndex !== -1) {
                        env.push({ Name: envEntry.substring(0, sepIndex), Value: envEntry.substring(sepIndex + 1)});
                    }
                });

                if (env.length) {
                    viewModel.Env = env;
                }

                self.copy(container, viewModel, 'Cmd');
                self.copy(container, viewModel, 'Entrypoint');
                self.copy(container, viewModel, 'Image');


                var volumes = [];
                self._safeKeyValueEach(container.Volumes, function(volume) {
                    volumes.push({ PathInContainer: volume });
                });

                self.copy(container, viewModel, 'WorkingDir');

                var labels = [];
                self._safeKeyValueEach(container.Labels, function(key, value) {
                    labels.push({ Key: key, Value: value});
                });
                if (labels.length) {
                    viewModel.Labels = labels;
                }

                var ports = [];
                self._safeEach(container.ExposedPorts, function(exposedPort) {
                    var tokens = exposedPort.split('/');
                    ports.push({ ContainerPort: tokens[0], Protocol: tokens[1] })
                });

                self.copy(container, viewModel, 'StopSignal');
                self.copy(container, viewModel, 'StopTimeout');


                self._safeEach(editor.Binds, function(bind) {
                    volumes.push({ PathOnHost: bind.PathOnHost, PathInContainer: bind.PathInContainer,  ReadOnly: bind.ReadOnly === 'ro' });
                });
                if (volumes.length) {
                    viewModel.Volumes = volumes;
                }

                var links = [];
                self._safeEach(hostConfig.Links, function(link) {
                    var tokens = link.split(':');
                    links.push({ Container: tokens[0], Alias: tokens[1] })
                });
                if (links.length) {
                    viewModel.Links = links;
                }

                self.copy(editor, viewModel, 'MemoryUnit');

                if (self.notEmpty(hostConfig.Memory)) {
                    viewModel.Memory = self.str(Math.floor(hostConfig.Memory / self._units_multiplier[viewModel.MemoryUnit]));
                }

                self.copy(editor, viewModel, 'MemorySwapUnit');
                if (self.notEmpty(hostConfig.MemorySwap)) {
                    if (hostConfig.MemorySwap == -1) {
                        viewModel.MemorySwapUnlimited = true;
                    } else {
                        viewModel.MemorySwap = self.str(Math.floor(hostConfig.MemorySwap / self._units_multiplier[viewModel.MemorySwapUnit]));
                    }
                }

                if (self.notEmpty(hostConfig.NanoCPUs)) {
                    viewModel.CPUs = self.str(hostConfig.NanoCPUs / 1e9);
                }

                self.copy(hostConfig, viewModel, 'CpuQuota', self.str);
                self.copy(hostConfig, viewModel, 'CpuShares', self.str);
                self.copy(hostConfig, viewModel, 'CpuPeriod', self.str);
                self.copy(hostConfig, viewModel, 'CpusetCpus');
                self.copy(hostConfig, viewModel, 'CpusetMems');
                self.copy(hostConfig, viewModel, 'BlkioWeight');
                self.copy(hostConfig, viewModel, 'OomKillDisable');

                self._safeKeyValueEach(hostConfig.PortBindings, function(port, bindings) {
                    var tokens = port.split("/");
                    var containerPort = tokens[0];
                    var protocol = tokens[1];
                    self._safeEach(bindings, function(binding) {
                        ports.push({ HostIp: binding.HostIp, HostPort: binding.HostPort, ContainerPort: containerPort, Protocol: protocol })
                    });
                });

                if (ports.length) {
                    viewModel.Ports = ports;
                }

                self.copy(hostConfig, viewModel, 'PublishAllPorts');
                self.copy(hostConfig, viewModel, 'Privileged');
                self.copy(hostConfig, viewModel, 'Dns');
                self.copy(hostConfig, viewModel, 'DnsSearch');

                var extraHosts = [];
                self._safeEach(hostConfig.ExtraHosts, function(extraHost) {
                    var tokens = extraHost.split(':');
                    extraHosts.push({ Name: tokens[0], Ip: tokens[1] });
                });
                if (extraHosts.length) {
                    viewModel.ExtraHosts = extraHosts;
                }

                self.copy(hostConfig, viewModel, 'CapAdd');
                self.copy(hostConfig, viewModel, 'CapDrop');

                var networkMode = hostConfig.NetworkMode;
                if (networkMode === "bridge" || networkMode === "host" || networkMode === "none") {
                    viewModel.NetworkMode = networkMode;
                } else if (/^container:/.test(networkMode)) {
                    viewModel.NetworkMode = "container";
                    viewModel.NetworkContainer = networkMode.substring('container:'.length);
                } else if (networkMode) {
                    viewModel.NetworkMode = 'custom';
                    viewModel.NetworkCustom = networkMode;
                }

                var devices = [];
                self._safeEach(hostConfig.Devices, function(device) {
                    devices.push(device);
                });
                if (devices.length) {
                    viewModel.Devices = devices;
                }

                var ulimits = [];
                self._safeEach(hostConfig.Ulimits, function(ulimit) {
                    ulimits.push(ulimit);
                });
                if (ulimits.length) {
                    viewModel.Ulimits = ulimits;
                }

                var logConfig = hostConfig.LogConfig;
                if (logConfig) {
                    viewModel.LogType = logConfig.Type;
                    var logConfigProps = [];
                    self._safeKeyValueEach(logConfig.Config, function (key, value) {
                        logConfigProps.push({ Key: key, Value: value});
                    });
                    if (logConfigProps.length) {
                        viewModel.LogConfig = logConfigProps;
                    }
                }

                var storageOpt = [];
                self._safeKeyValueEach(hostConfig.StorageOpt, function(key, value) {
                    storageOpt.push({ Key: key, Value: value});
                });
                if (storageOpt.length) {
                    viewModel.StorageOpt = storageOpt;
                }

                self.copy(hostConfig, viewModel, 'CgroupParent');

                return viewModel;
            },
            /* TABS */

            _initTabs: function () {
                self.logDebug("Initializing dialog tabs.");

                self.tabs = [{ id: "dockerCloudImageTab_general", lbl: "General" },
                    { id: "dockerCloudImageTab_run", lbl: "Run" },
                    { id: "dockerCloudImageTab_network", lbl: "Network" },
                    { id: "dockerCloudImageTab_resources", lbl: "Resources" },
                    { id: "dockerCloudImageTab_privileges", lbl: "Privileges" },
                    { id: "dockerCloudImageTab_advanced", lbl: "Advanced" }];

                var imageDataTabbedPane = new TabbedPane();
                $j.each(self.tabs, function(i, val) {
                    imageDataTabbedPane.addTab(val.id, {
                        caption: '<span>' + val.lbl + ' <span class="dockerCloudErrorIcon"></span></span>',
                        onselect: self._selectTab
                    });
                });

                $j.each(imageDataTabbedPane.getTabs(), function (i, tab) {
                    tab.errors = [];
                    tab.warnings = [];

                    tab.clearAllMessages = function() {
                        tab.errors = [];
                        tab.warnings = [];
                        tab._updateTabIcon();
                        self.updateOkBtnState();
                    };
                    tab.clearMessages = function (id) {
                        self._removeFromArray(tab.errors, id);
                        self._removeFromArray(tab.warnings, id);
                        tab._updateTabIcon();
                        self.updateOkBtnState();
                    };
                    tab.addMessage = function (id, warning) {
                        self._addToArrayIfAbsent(warning ? tab.warnings : tab.errors, id);
                        tab._updateTabIcon();
                        self.updateOkBtnState();
                    };
                    tab._updateTabIcon = function () {
                        var caption = $j(tab.myOptions.caption);
                        var span = caption.children("span").empty();
                        if (tab.errors.length) {
                            span.append('<img src="' + self.errorIconURL + '" />');
                        } else if (tab.warnings.length) {
                            span.append('<img src="' + self.warnIconURL + '" />');
                        }
                        tab.setCaption(caption[0].outerHTML);
                    };
                });

                self.$imageDataOkBtn.click(self._okBtnClicked);

                imageDataTabbedPane.showIn('dockerCloudImageTabContainer');

                self.imageDataTabbedPane = imageDataTabbedPane;
            },

            _getElementTab: function (elt) {
                var tab = self.imageDataTabbedPane.getTab(elt.closest('[id^="dockerCloudImageTab"]').attr("id"));
                return tab;
            },

            _selectTab: function (tab) {
                self.selectTabWithId(tab.getId());
            },

            selectTabWithId: function (id) {
                $j.each(self.tabs, function (i, val) {
                    $j("#" + val.id).toggle(val.id === id);
                });
            },

            /* HANDLERS */
            _bindHandlers: function () {
                self.logDebug("Binding handlers.");

                self.$useLocalInstance.change(self._instanceChange);
                self.$useCustomInstance.change(self._instanceChange);
                self.$useTls.change(self._scheduleConnectionCheck);

                var useLocalInstance = self.$useLocalInstance.is(':checked');
                self.$dockerAddress.prop('disabled', useLocalInstance);
                if (useLocalInstance) {
                    self.$dockerAddress.val(self.defaultLocalSocketURI);
                }

                self.$checkConnectionBtn.click(self._checkConnectionClickHandler);
                self.$newImageBtn.click(self._showImageDialogClickHandler);

                self.$dockerAddress.change(function() {
                    // Normalize the Docker address and do some auto-correction regarding count of slashes after the
                    // scheme.
                    var address = self.$dockerAddress.val();
                    var match = address.match(/([a-zA-Z]+?):\/*(.*)/);
                    var scheme;
                    var schemeSpecificPart;
                    var ignore = false;
                    if (match) {
                        // Some scheme detected.
                        scheme = match[1].toLowerCase();
                        schemeSpecificPart = match[2];
                    } else if (address.match(/[0-9].*/)) {
                        scheme = 'tcp';
                        schemeSpecificPart = address;
                    } else {
                        match = address.match(/\/+(.*)/);
                        if (match) {
                            scheme = 'unix';
                            schemeSpecificPart = match[1];
                        } else {
                            // Most certainly invalid, but let the server complain about it.
                            ignore = true;
                        }
                    }

                    if (!ignore) {
                        self.$dockerAddress.val(scheme + ':' + (scheme === 'unix' ? '///' : '//') + schemeSpecificPart);
                    }

                    self._scheduleConnectionCheck();
                });

                self.$imageDialogSubmitBtn.click(function() {
                    self._triggerAllFields(true);

                    if(!self.updateOkBtnState()) {
                        return false;
                    }

                    var viewModel = self._restoreViewModel();
                    var settings = self._convertViewModelToSettings(viewModel);

                    var currentProfile = self.$imageDialogSubmitBtn.data('profile');
                    var newProfile = settings.Administration.Profile;
                    self.logDebug("Saving profile: " + newProfile + " (was: " + currentProfile + ")");
                    delete self.imagesData[currentProfile];
                    self.imagesData[newProfile] = settings;
                    self._writeImages();
                    self._updateTCImageDetails(currentProfile, newProfile);
                    BS.DockerImageDialog.close();
                    self._renderImagesTable();
                });

                self.$imageDialogCancelBtn.click(function() {
                    BS.DockerImageDialog.close();
                });

                var editDelegates = self.selectors.imagesTableRow + ' .highlight, ' + self.selectors.editImageLink;
                self.$imagesTable.on('click', editDelegates, function () {
                    self.showEditDialog($j(this));
                    return false;
                });

                self.$useOfficialDockerImage.change(function () {
                    self.$image.prop('disabled', self.$useOfficialDockerImage.is(':checked'));
                    self.$registryUser.prop('disabled', self.$useOfficialDockerImage.is(':checked'));
                    self.$registryPassword.prop('disabled', self.$useOfficialDockerImage.is(':checked'));
                    self.$image.blur();
                }).change();

                self.$registryUser.change(function () {
                    self.$registryPassword.blur();
                }).change();
                self.$registryPassword.change(function () {
                    self.$registryUser.blur();
                }).change();

                var networkMode = $j("#dockerCloudImage_NetworkMode");
                var customNetwork = $j("#dockerCloudImage_NetworkCustom");
                var containerNetwork = $j("#dockerCloudImage_NetworkContainer");

                networkMode.change(function () {
                    var mode = networkMode.val();

                    var container = mode === "container";
                    containerNetwork.toggle(container);
                    containerNetwork.prop('disabled', !container);
                    var custom = mode === "custom";
                    customNetwork.toggle(custom);
                    customNetwork.prop('disabled', !custom)
                    containerNetwork.blur();
                    customNetwork.blur();
                }).change();

                var customKernelCap = $j("#dockerCloudImage_kernel_custom_cap");
                $j('input[name="dockerCloudImage_Capabilities"]').change(function () {
                    var radio = $j(this);
                    customKernelCap.toggle(radio.is(':checked') && radio.val() === "custom");
                }).change();


                var $swap = $j("#dockerCloudImage_MemorySwap");

                self.$memoryUnit.change(function () {
                    self.$memory.blur();
                    $swap.blur();
                });
                self.$memory.change(function () {
                    $swap.blur();
                });
                self.$swapUnit.change(function () {
                    $swap.blur();
                });

                self.$dialog.on("blur", 'input:text', self.validate);
                self.$dialog.on("change", 'input:not(input:text):not(input:button), select', self.validate);

                self.$imagesTable.on('click', ".dockerCloudDeleteBtn", function() {
                    if (!confirm('Do you really want to delete this image ?')) {
                        return;
                    }
                    var profile = $j(this).closest('tr').data('profile');
                    self.logDebug('Deleting image: ' + profile);
                    delete self.imagesData[profile];
                    self._writeImages();
                    self._updateTCImageDetails();
                    self._renderImagesTable();
                });

                self.$imagesTable.on('click', ".dockerCloudSettingsBtn", function(evt) {
                    self.showDialog($j(this).closest('tr').data('profile'));
                    evt.preventDefault();
                    return false;
                });

                self.$imagesTable.on('click', ".dockerCloudAddBtn", function() {
                    self.showDialog();
                });

                self.$dialog.on("click", ".dockerCloudDeleteBtn", function() {
                    var $row = $j(this).closest("tr");
                    var $table = $row.closest("table");
                    var tab = self._getElementTab($row);
                    // Clear error and warning messages related to this table row.
                    $j("input, select", $row).each(function (i, val) {
                        tab.clearMessages(val.id);
                    });
                    $row.remove();
                    self._updateTableMandoryStarsVisibility($table);
                });


                self.$dialog.on("click", ".dockerCloudAddBtn", function(e) {
                    // TODO: improve event binding. This handler will sometimes get called twice here if we do not
                    // stop propagation.
                    e.stopPropagation();

                    var $elt = $j(this);

                    // Fetch closest table.
                    var $tableBody = $elt.closest("tbody");
                    var key = $tableBody.attr("id");
                    var index = $j.data($tableBody.get(0), "index") || 0;
                    index++;
                    self.logDebug("Adding row #" + index + " to table " + key + ".");
                    var $table = $elt.closest("table");
                    $elt.closest("tr").before('<tr>' + self.arrayTemplates[key].replace(/IDX/g, index) + '<td' +
                    ' class="center dockerCloudCtrlCell">' + self.arrayTemplates.deleteCell + '</td></tr>');
                    $j.data($tableBody.get(0), "index", index);
                    self._updateTableMandoryStarsVisibility($table);
                });

                self.$swapUnlimited.change(function() {
                    var unlimited = self.$swapUnlimited.is(":checked");
                    $swap.prop("disabled", unlimited);
                    self.$swapUnit.prop("disabled", unlimited);
                    if (unlimited) {
                        $swap.val("")
                    }
                    $swap.blur();
                });

                self.$testContainerCreateBtn.click(function() {
                    self._testDialogHideAllBtns();

                    self.$testContainerCancelBtn.show();
                    self.$testContainerSuccessIcon.hide();
                    self.$testContainerWarningIcon.hide();
                    self.$testContainerErrorIcon.hide();

                    // Pack the current image settings into a hidden field to be submitted.
                    var viewModel = self._restoreViewModel();
                    var settings = self._convertViewModelToSettings(viewModel);
                    self.$testedImage.val(JSON.stringify(settings));

                    self.$testContainerLabel.text("Starting test...");
                    self._invokeTestAction('create', BS.Clouds.Admin.CreateProfileForm.serializeParameters())
                        .done(function (response) {
                            self.testUuid = JSON.parse(response.responseText).testUuid;
                            self._queryTestStatus();
                        });
                });

                self.$testContainerStartBtn.click(function() {
                    self._testDialogHideAllBtns();
                    self.$testContainerCancelBtn.show();
                    self.$testContainerSuccessIcon.hide();
                    self.$testContainerWarningIcon.hide();
                    self.$testContainerErrorIcon.hide();

                    self.$testContainerLabel.text("Waiting on server...");
                    self._invokeTestAction('start')
                        .done(function () {
                            self._queryTestStatus();
                        });
                });

                self.$testContainerCancelBtn.click(function() {
                    self._closeStatusSocket();
                    self.testCancelled = true;
                    self.$testContainerLoader.hide();
                    self.$testContainerErrorIcon.show();
                    self.$testContainerLabel.text("Cancelled by user.");
                    self.$testContainerCancelBtn.hide();
                    self.$testContainerCloseBtn.show();
                });

                self.$testContainerCloseBtn.click(function() {
                    self.cancelTest();
                });

                self.$testContainerContainerLogsBtn.click(function() {
                    self._invokeTestAction('logs', null, true)
                        .done(function(response) {
                            var logs = JSON.parse(response.responseText).logs;
                            self.prepareDiagnosticDialog("Container logs:", logs);
                            self.$testContainerLoader.hide();
                            BS.DockerDiagnosticDialog.showCentered();
                        });
                });

                BS.DockerTestContainerDialog.afterClose(self.cancelTest);

                self.$imageTestContainerBtn.click(function() {

                    self._triggerAllFields(true);

                    if(!self.updateOkBtnState()) {
                        return false;
                    }

                    self._initTestDialog();
                    BS.DockerTestContainerDialog.showCentered();
                });

                self.$diagnosticCloseBtn.click(function () {
                   BS.DockerDiagnosticDialog.close();
                });

                self.$diagnosticCopyBtn.hide();
            },

            _queryTestStatus: function() {
                if (self.testCancelled) {
                    return;
                }
                if (!self.testUuid) {
                    self.logError("Test UUID not resolved.");
                    return;
                }

                if (self.hasWebSocketSupport) {
                    if (!self.testStatusSocket) {
                        self.logInfo('Opening test status listener socket.');
                        var socketURL = self.resolveWebSocketURL(self.testStatusSocketPath + '?testUuid=' + self.testUuid);
                        self.testStatusSocket = new WebSocket(socketURL);
                        self.testStatusSocket.onmessage = function (event) {
                            self._processTestStatusResponse(self._parseTestStatusResponse(event.data));
                        }
                    }
                } else {
                    self._invokeTestAction('query', BS.Clouds.Admin.CreateProfileForm.serializeParameters())
                        .done(function(response){
                            var responseMap = self._parseTestStatusResponse(response.responseText);
                            if (responseMap.status == 'PENDING') {
                                self.logDebug('Scheduling status retrieval.');
                                setTimeout(self._queryTestStatus, 5000);
                            }
                            self._processTestStatusResponse(responseMap);
                        });
                }
            },

            cancelTest: function() {

                self.logDebug("Cancelling test: " + self.testUuid);
                self._closeStatusSocket();

                if (self.testUuid) {
                    self._invokeTestAction('cancel', null, true);
                    delete self.testUuid;
                }

                BS.DockerTestContainerDialog.close();
            },

            _closeStatusSocket: function() {
                if (self.testStatusSocket) {
                    try { self.testStatusSocket.close() } catch (e) {}
                    delete self.testStatusSocket;
                }
                if (self.logStreamingSocket) {
                    try { self.logStreamingSocket.close() } catch (e) {}
                    delete self.logStreamingSocket;
                }
            },

            _processTestStatusResponse: function (responseMap) {

                self.logDebug('Phase: ' + responseMap.phase + ' Status: ' + responseMap.status + ' Msg: ' +
                    responseMap.msg + ' Container ID: ' + responseMap.containerId + ' Uuid: ' + responseMap.testUuid +
                    ' Warnings: ' + responseMap.warnings.length);

                self.$testContainerLabel.text(responseMap.msg);


                if (responseMap.status == 'PENDING') {
                    if (responseMap.phase == "WAIT_FOR_AGENT") {
                        if (self.hasXTermSupport && !self.logStreamingSocket) {
                            console.log('Opening live logs sockt now.');

                            var url =self.resolveWebSocketURL(self.streamSocketPath + '?correlationId=' + self.testUuid);
                            self.$dockerTestContainerOutputTitle.fadeIn(400);
                            // Compensate the appearance of the terminal with a upward shift of the dialog window.
                            // Should be roughly half of the terminal height include the title.
                            self.$testImageDialog.animate({top: "-=150px", left: "-=75px", width: "+=150px"});
                            self.logStreamingSocket = new WebSocket(url);

                            self.$dockerTestContainerOutput.slideDown(400, function() {
                                var logTerm = new Terminal();
                                logTerm.open(self.$dockerTestContainerOutput[0]);
                                logTerm.fit();
                                logTerm.attach(self.logStreamingSocket);
                                logTerm.convertEol = true;
                            });
                        }
                    }
                } else {

                    self._testDialogHideAllBtns();
                    self._closeStatusSocket();

                    self.$testContainerCancelBtn.hide();
                    self.$testContainerLoader.hide();
                    self.$testContainerCloseBtn.show();
                    if (responseMap.phase == 'WAIT_FOR_AGENT') {
                        self.$testContainerContainerLogsBtn.show();
                        self.$testExecInfo.append('Note: you can access the running container by using the <code>exec</code> ' +
                            'command on the the Docker daemon host. For example: ' +
                            '<p class="mono">docker exec -t -i ' + responseMap.containerId + ' /bin/bash</p>');
                        self.$testExecInfo.slideDown();
                    }

                    if (responseMap.status == 'FAILURE') {
                        if (responseMap.phase == 'CREATE') {
                            self.$testContainerCloseBtn.val("Close");
                        } else {
                            self.$testContainerCloseBtn.val("Dispose container");
                        }

                        self.$testContainerLabel.addClass('containerTestError');
                        self.$testContainerErrorIcon.show();

                        if (responseMap.failureCause) {
                            self.prepareDiagnosticDialogWithLink(self.$testContainerLabel, responseMap.msg,
                                responseMap.failureCause);
                        }

                    } else if (responseMap.status == 'SUCCESS') {

                        self.$testContainerCloseBtn.val("Dispose container");

                        var hasWarning = !!responseMap.warnings.length;

                        if (hasWarning) {
                            self.$testContainerWarningIcon.show();
                        } else {
                            self.$testContainerSuccessIcon.show();
                        }

                        if (responseMap.phase == 'CREATE') {
                            self.$testContainerStartBtn.show();
                            if (hasWarning) {
                                self.$testContainerLabel.text("Container " + responseMap.containerId + " created with warnings:");
                            } else {
                                self.$testContainerLabel.text("Container " + responseMap.containerId + " successfully created.");
                            }
                        } else if (responseMap.phase == 'WAIT_FOR_AGENT') {

                            if (hasWarning) {
                                self.$testContainerLabel.text("Agent connection detected for container " + responseMap.containerId + ":");
                            } else {
                                self.$testContainerLabel.text("Agent connection detected for container " + responseMap.containerId + ".");
                            }
                        }

                        if (hasWarning) {
                            var $list = $j('<ul>');
                            self._safeEach(responseMap.warnings, function(warning) {
                                $list.append('<li>' + warning + '</li>');
                            });
                            self.$testContainerWarningIcon.show();
                            self.$testContainerLabel.append($list);
                        }
                    } else {
                        self.logError('Unrecognized status: ' + responseMap.status);
                    }
                }
            },

            _parseTestStatusResponse: function(json) {
                return JSON.parse(json).statusMsg;
            },

            _invokeTestAction: function(action, parameters, immediate) {

                self.logDebug('Will invoke action ' + action + ' for test UUID ' + self.taskUuid);

                var deferred = $j.Deferred();

                // Invoke test action.
                var url = self.testContainerCtrlURL + '?action=' + action;
                if (self.testUuid) {
                    url += '&testUuid=' + self.testUuid;
                }

                if (!immediate) {
                    self.$testContainerLoader.show();
                    self.$testContainerCancelBtn.show();
                    self.$testContainerCloseBtn.hide();
                }

                BS.ajaxRequest(url, {
                    parameters: parameters,
                    onSuccess: function (response) {
                        deferred.resolve(response);
                    },
                    onFailure: function (response) {
                        var txt;
                        if (response.responseText.length > 150 || response.responseText.indexOf('<html>') != -1) {
                            txt = response.statusText;
                        } else {
                            txt = response.responseText;
                        }
                        deferred.reject(txt);
                    }
                });

                if (!immediate) {
                    deferred.fail(function(errorMsg) {
                        self._testDialogHideAllBtns();
                        self.$testContainerCloseBtn.show();
                        self.$testContainerLabel.text(errorMsg).addClass('containerTestError');
                        self.$testContainerErrorIcon.show();
                        self.$testContainerLoader.hide();
                    });
                }

                return deferred;
            },
            _checkConnectionClickHandler: function () {
                if (self.$checkConnectionBtn.attr('disabled') !== 'true') { // it may be undefined
                    self._checkConnection();
                }

                return false; // to prevent link with href='#' to scroll to the top of the page
            },
            _toggleCheckConnectionBtn: function (enable) {
                self.$checkConnectionBtn.attr('disabled', !enable);
            },

            _okBtnClicked: function (evt) {
                // Blur all text fields to trigger "required" validations.
                // Do not cache this selector to prevent problem with tables.
                $j("#DockerCloudImageDialog input:text").blur();
                if (!$j(this).is(":enabled")) {
                    evt.preventDefault();
                }
            },

            updateOkBtnState: function () {
                var hasError = false;

                $j.each(self.imageDataTabbedPane.getTabs(), function (i, tab) {
                    if (tab.errors.length) {
                        hasError = true;
                        return false;
                    }
                });
                self.$imageDataOkBtn.prop("disabled", hasError);
                self.$imageTestContainerBtn.prop("disabled", hasError);
                return !hasError;
            },

            /* VALIDATORS */
            _initValidators: function () {
                self.logDebug("Validators setup.");

                var requiredValidator = function (elt) {
                    var value = elt.val().trim();
                    elt.val(value);
                    if (!value) {
                        return {msg: "This field is required."};
                    }
                };

                var ipv4OrIpv6Validator = function (elt) {
                    var regex = /((^\s*((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))\s*$)|(^\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))(%.+)?\s*$))/;
                    var value = elt.val().trim();
                    elt.val(value);
                    if (value && !regex.test(value)) {
                        return {msg: "Please specify a valid IPv4 or IPv6 address."};
                    }
                };

                var positiveIntegerValidator = function (elt) {
                    var value = elt.val().trim().replace(/^0+/, '');
                    if (!value) {
                        return;
                    }
                    if (/^[0-9]+$/.test(value)) {
                        // Check that we are in the positive range of a golang int64 max value.
                        if (parseInt(value) > 9223372036854775807) {
                            return {msg: "Value out of bound."};
                        }
                    } else {
                        return {msg: "Value must be a positive integer."};
                    }
                };

                var portNumberValidator = function (elt) {
                    var value = elt.val().trim();
                    elt.val(value);
                    if (!value) {
                        return;
                    }
                    if (!positiveIntegerValidator(elt)) {
                        var number = parseInt(elt.val());
                        if (number >= 1 && number <= 65535) {
                            return;
                        }
                    }

                    return {msg: "Port number must be between 1 and 65535."};
                };

                var cpusValidator = function(elt) {
                    var value = elt.val().trim().replace(/^0+\B/, '');
                    if (!value) {
                        return;
                    }
                    if (/^[0-9]+(\.[0-9]+)?$/.test(value)) {
                        var number = parseFloat(value) * 1e9;
                        if (number > 9223372036854775807) {
                            return {msg: "Value out of bound."};
                        }
                        if (number % 1 !== 0) { // Should have no decimal part left.
                            return {msg: "Value is too precise."};
                        }
                    } else {
                        return {msg: "Value must be a positive decimal number."};
                    }
                };

                var cpuSetValidator = function(elt) {
                    var value = elt.val().trim();
                    elt.val(value);
                    if (!value) {
                        return;
                    }

                    if (!/^[0-9]+(?:[-,][0-9]+)*$/.test(value)) {
                        return {msg: "Invalid Cpuset specification."};
                    }
                };

                var versionValidator = function(targetVersion, elt) {
                    if (!self.effectiveApiVersion) {
                        return;
                    }
                    var value = elt.val().trim();
                    elt.val(value);
                    if (!value) {
                        return;
                    }

                    var daemonVersion = self.effectiveApiVersion;

                    if (self.compareVersionNumbers(daemonVersion, targetVersion) < 0) {
                        return {msg: 'The daemon API version (v' + daemonVersion + ') is lower than required for ' +
                        'this configuration field (v' + targetVersion + ').', warning: true};
                    }
                };

                self.validators = {
                    dockerCloudImage_Profile: [requiredValidator, function($elt) {
                        if (!/^\w+$/.test($elt.val())) {
                            return {msg: 'Only alphanumerical characters without diacritic and underscores allowed.'}
                        }
                    }, function($elt) {
                        var newProfile = $elt.val();
                        var currentProfile = self.$imageDialogSubmitBtn.data('profile');
                        if (newProfile != currentProfile && self.imagesData[newProfile]) {
                            return {msg: 'An image profile with this name already exists.'}
                        }
                    }],
                    dockerCloudImage_Image: [requiredValidator],
                    dockerCloudImage_MaxInstanceCount: [positiveIntegerValidator, function($elt) {
                        if (parseInt($elt.val()) < 1) {
                            return {msg: "At least one instance must be permitted."};
                        }
                    }],
                    dockerCloudImage_RegistryUser: [function ($elt){
                        var pass = self.$registryPassword.val().trim();
                        var user = $elt.val().trim();
                        if (pass && !user) {
                            return {msg: 'Must specify user if password set.'}
                        }
                    }],
                    dockerCloudImage_RegistryPassword: [function ($elt){
                        var user = self.$registryUser.val().trim();
                        var pass = $elt.val().trim();
                        if (user && !pass) {
                            return {msg: 'Must specify password if user set.'}
                        }
                    }],
                    dockerCloudImage_StopTimeout: [positiveIntegerValidator, versionValidator.bind(this, '1.25')],
                    dockerCloudImage_Entrypoint_IDX: [function ($elt) {
                        var row = $elt.closest("tr");
                        if (row.index() === 0) {
                            var value = $elt.val().trim();
                            $elt.val(value);
                            if (!value) {
                                return {msg: "The first entry point argument must point to an executable."};
                            }
                        }
                    }],
                    dockerCloudImage_Volumes_IDX_PathInContainer: [requiredValidator],
                    dockerCloudImage_Ports_IDX_HostIp: [ipv4OrIpv6Validator],
                    dockerCloudImage_Ports_IDX_HostPort: [portNumberValidator],
                    dockerCloudImage_Ports_IDX_ContainerPort: [requiredValidator, portNumberValidator],
                    dockerCloudImage_Dns_IDX: [requiredValidator],
                    dockerCloudImage_DnsSearch_IDX: [requiredValidator],
                    dockerCloudImage_ExtraHosts_IDX_Name: [requiredValidator],
                    dockerCloudImage_ExtraHosts_IDX_Ip: [requiredValidator, ipv4OrIpv6Validator],
                    dockerCloudImage_NetworkCustom: [requiredValidator],
                    dockerCloudImage_NetworkContainer: [requiredValidator],
                    dockerCloudImage_Links_IDX_Container: [requiredValidator],
                    dockerCloudImage_Links_IDX_Alias: [requiredValidator],
                    dockerCloudImage_Ulimits_IDX_Name: [requiredValidator],
                    dockerCloudImage_Ulimits_IDX_Soft: [requiredValidator, positiveIntegerValidator],
                    dockerCloudImage_Ulimits_IDX_Hard: [requiredValidator, positiveIntegerValidator],
                    dockerCloudImage_LogConfig_IDX_Key: [requiredValidator],
                    dockerCloudImage_Devices_IDX_PathOnHost: [requiredValidator],
                    dockerCloudImage_Devices_IDX_PathInContainer: [requiredValidator],
                    dockerCloudImage_Env_IDX_Name: [requiredValidator],
                    dockerCloudImage_Labels_IDX_Key: [requiredValidator],
                    dockerCloudImage_StorageOpt_IDX_Key: [requiredValidator],
                    dockerCloudImage_Memory: [function ($elt) {
                        var value = $elt.val().trim();
                        $elt.val(value);
                        if (!value) {
                            return;
                        }
                        var result = positiveIntegerValidator($elt);
                        if (!result) {
                            var number = parseInt(value);
                            var multiplier = self._units_multiplier[self.$memoryUnit.val()];
                            if ((number * multiplier) < 524288) {
                                result = {msg: "Memory must be at least 4Mb."}
                            }
                        }
                        return result;
                    }],
                    dockerCloudImage_CPUs: [cpusValidator, versionValidator.bind(this, '1.25')],
                    dockerCloudImage_CpuQuota: [function($elt) {
                        var value = $elt.val().trim();
                        $elt.val(value);
                        if (!value) {
                            return;
                        }
                        var result = positiveIntegerValidator($elt);
                        if (!result) {
                            var number = parseInt(value);
                            if (number < 1000) {
                                result = {msg: "CPU Quota must be at least of 1000μs (1ms)."}
                            }
                        }
                        return result;
                    }],
                    dockerCloudImage_CpusetCpus: [cpuSetValidator],
                    dockerCloudImage_CpusetMems: [cpuSetValidator],
                    dockerCloudImage_CpuShares: [positiveIntegerValidator],
                    dockerCloudImage_CpuPeriod: [function($elt) {
                        var value = $elt.val().trim();
                        $elt.val(value);
                        if (!value) {
                            return;
                        }
                        var result = positiveIntegerValidator($elt);
                        if (!result) {
                            var number = parseInt(value);
                            if (number < 1000 || number > 1000000) {
                                result = {msg: "CPU period must be between 1000μs (1ms) and 1000000μs (1s)"}
                            }
                        }
                        return result;
                    }],
                    dockerCloudImage_BlkioWeight: [function ($elt) {
                        var value = $elt.val().trim();
                        $elt.val(value);
                        if (!value) {
                            return;
                        }
                        var result = positiveIntegerValidator($elt);
                        if (!result) {
                            var number = parseInt(value);
                            if (number < 10 || number > 1000) {
                                result = {msg: "IO weight must be between 10 and 1000"}
                            }
                        }
                        return result;
                    }],
                    dockerCloudImage_MemorySwap: [
                        function ($elt) {
                            var value = $elt.val().trim();
                            $elt.val(value);
                            if (!value) {
                                return;
                            }
                            if (self.$swapUnlimited.is(":checked")) {
                                return;
                            }
                            var memoryVal = self.$memory.val();
                            if (!memoryVal) {
                                return {msg: "Swap limitation can only be used in conjunction with the memory limit."};
                            }
                            if (!positiveIntegerValidator(self.$memory)) {
                                var memory = parseInt(memoryVal);
                                var result = positiveIntegerValidator($elt);
                                if (!result) {
                                    var swap = parseInt(value);
                                    var memoryUnitMultiplier = self._units_multiplier[self.$memoryUnit.val()];
                                    var swapUnitMultiplier = self._units_multiplier[self.$swapUnit.val()];
                                    if (swap * swapUnitMultiplier <= memory * memoryUnitMultiplier) {
                                        result = {msg: "Swap limit must be strictly greater than the memory limit."}
                                    }
                                }
                                return result;
                            }
                        }
                    ]
                };
            },

            _testDialogHideAllBtns: function() {
                self.$testContainerCreateBtn.hide();
                self.$testContainerStartBtn.hide();
                self.$testContainerContainerLogsBtn.hide();
                self.$testContainerDisposeBtn.hide();
                self.$testContainerShellBtn.hide();
            },

            _initTestDialog: function () {
                self._testDialogHideAllBtns();
                self.$dockerTestContainerOutputTitle.hide();
                self.$dockerTestContainerOutput.hide();
                self.$dockerTestContainerOutput.empty();
                self.$testContainerCreateBtn.show();
                self.$testContainerOutcome.text();
                self.$testContainerCancelBtn.hide();
                self.$testContainerCloseBtn.val("Close");
                self.$testContainerLoader.hide();
                self.$testContainerSuccessIcon.hide();
                self.$testContainerWarningIcon.hide();
                self.$testContainerErrorIcon.hide();
                self.$testContainerLabel.empty();
                self.$testExecInfo.empty();
                self.$testExecInfo.hide();
                self.$testContainerLabel.removeClass('containerTestError');
                self.$testContainerCancelBtn.attr('disabled', false)
                self.testCancelled = false;
            },

            validate: function () {

                var result;
                var elt = $j(this);
                var eltId = elt.attr("id") || elt.attr("name");

                var vals = self.validators[eltId];
                if (!vals) {
                    vals = self.validators[eltId.replace(/[0-9]+/, "IDX")];
                }

                // Only validate fields that are not disabled.
                // Note: fields that are not visible must always be validated in order to perform cross-tabs
                // validation.
                if (vals && !elt.is(':disabled')) {
                    $j.each(vals, function (i, validator) {
                        result = validator(elt);
                        if (result) {
                            return false;
                        }
                    });
                }
                var tab = self._getElementTab(elt);
                var errorMsg = $j("#" + eltId + "_error").empty();
                var warningMsg = $j("#" + eltId + "_warning").empty();
                if (result) {
                    var msg = result.warning ? warningMsg : errorMsg;
                    msg.append(result.msg);
                    tab.addMessage(eltId, result.warning);
                } else {
                    tab.clearMessages(eltId);
                }
                return true;
            },

            /* UTILS */
            logInfo: function(msg) {
                self._log(msg);
            },
            logError: function(msg) {
                // Catching all errors instead of simply testing for console existence to prevent issues with IE8.
                try { console.error(msg) } catch (e) {}
            },
            logDebug: function(msg) {
              if (self.debugEnabled) {
                  self._log(msg);
              }
            },
            str: function(obj) {
              return self.notEmpty(obj) ? obj.toString() : obj;
            },
            _log: function(msg) {
                // Catching all errors instead of simply testing for console existence to prevent issues with IE8.
                try { console.log(msg) } catch (e) {}
            },
            prepareDiagnosticDialogWithLink: function($container, msg, details) {
                self.prepareDiagnosticDialog(msg, details);
                var viewDetailsLink = $j('<a href="#/">view details</a>)').click(function () {
                    BS.DockerDiagnosticDialog.showCentered();
                });
                $container.append(' (').append(viewDetailsLink).append(')');
            },

            prepareDiagnosticDialog: function(msg, details) {
                self.$diagnosticMsg.text(msg);
                self.$diagnosticLogs.text(details);

                if (!self.clipboard && typeof Clipboard !== 'undefined') {
                    self.logInfo("Clipboard operations enabled.");
                    self.clipboard = new Clipboard('#dockerDiagnosticCopyBtn');
                    self.clipboard.on('success', function(e) {
                        e.clearSelection();
                    });
                    self.$diagnosticCopyBtn.show();
                }
            },

            resolveWebSocketURL: function(path) {
                var protocol = (location.protocol === 'https:') ? 'wss://' : 'ws://';
                var port =  ((location.port) ? (':' + location.port) : '');
                return protocol + location.hostname + port + path;
            },
            checkXtermBrowserSupport: function() {
                try {
                    var parser = new UAParser();
                    parser.setUA(navigator.userAgent);
                    var browser = parser.getBrowser();
                    var name = browser.name;
                    var versionToken = browser.version.split('.')[0];
                    if (!versionToken.match(/[0-9]+/)) {
                        return false;
                    }
                    var version = parseInt(versionToken);

                    self.logDebug('Detected browser name: ' + name + ' -- version: ' + version);
                    if ((name == 'Chrome' || name == 'Chromium') && version >= 48) {
                        return true;
                    }
                    if (name == 'Firefox' && version >= 44) {
                        return true;
                    }
                    if (name == 'IE' && version >= 11) {
                        return true;
                    }
                    if (name == 'Edge' && version >= 13) {
                        return true;
                    }
                    if (name == 'Opera' && version >= 8) {
                        return true;
                    }
                    if (name == 'Safari' && version >= 35) {
                        return true;
                    }
                } catch (e) {
                    self.logError('Failed to determine browser support: ' + e);
                }
            },
            compareVersionNumbers: function(v1, v2) {
                var v1Digits = self._convertVersionToDigits(v1);
                var v2Digits = self._convertVersionToDigits(v2);

                for (var i = 0; i < v1Digits.length && i < v2Digits.length; i++) {
                    var cmp = v1Digits[i] - v2Digits[i];
                    if (cmp !== 0) {
                        return cmp;
                    }
                }

                return v1Digits.length - v2Digits.length;
            },
            _convertVersionToDigits: function(version) {
                var tokens = version.split('.');
                var digits = [];
                $j(tokens).each(function(i, token) {
                    var digit;
                    if (token.match('^[0-9]+$')) {
                        digit = parseInt(token, 10);
                    }
                    if (!digit || isNaN(digit)) {
                        digit = 0;
                    }
                    digits.push(digit);
                });
                return digits;
            },
            _padLeft: function(txt, padChar, minSize) {
                txt = txt.toString();
                while (txt.length < minSize) txt = padChar + txt;
                return txt;
            },
            _units_multiplier:  {
                GiB: 1073741824,
                MiB: 1048576,
                KiB: 1024,
                bytes: 1
            },

            _resolveUnit: function(value) {
                var resolved = null;
                $j.each(self._units_multiplier, function(unit, multiplier) {
                    if (value % multiplier === 0) {
                        resolved = unit;
                    }
                });
                return resolved || 'bytes';
            },
            _removeFromArray: function (array, value) {
                var i = array.indexOf(value);
                if (i != -1) {
                    return array.splice(i, 1);
                }
            },
            _addToArrayIfAbsent: function (array, value) {
                var i = array.indexOf(value);
                if (i == -1) {
                    array.push(value);
                }
            },

            // Base-64 encoding/decoding is tricky to achieve in an unicode-safe way (especially when using the atob
            // and btoa standard functions). We leverage here an all-purpose binary-based Base64 encoder and do the
            // string to UTF-16BE conversion ourselves.
            base64Utf16BEEncode: function(str) {
                if (!str) {
                    return;
                }
                try {
                    var arr = [];
                    for (var i = 0; i < str.length; i++) {
                        var charcode = str.charCodeAt(i);
                        arr.push((charcode >> 8) & 0xff);
                        arr.push(charcode & 0xff);
                    }
                    return base64js.fromByteArray(arr);
                } catch (e) {
                    self.logError("Failed to encode base64 string.");
                }
            },
            base64Utf16BEDecode: function(base64) {
                if (!base64) {
                    return;
                }
                try {
                    var byteArray = base64js.toByteArray(base64);
                    if (byteArray.length % 2 !== 0) {
                        self.logError("Invalid content length.");
                    }
                    var charcodes = [];
                    for (var i = 0; i < byteArray.length - 1; i+=2) {
                        charcodes.push((((byteArray[i] & 0xff) << 8) | (byteArray[i+1] & 0xff)));
                    }
                    return String.fromCharCode.apply(null, charcodes);
                } catch (e) {
                    self.logError("Failed to decode base64 string.");
                }
            },

            arrayTemplates: {
                deleteCell: '<a class="btn dockerCloudCtrlBtn dockerCloudDeleteBtn" href="#/" title="Delete"><span></span></a>',
                settingsCell: '<a class="btn dockerCloudCtrlBtn dockerCloudSettingsBtn" href="#/" title="Settings"><span></span></a>',
                dockerCloudImage_imagesTableRow: '<tr class="imagesTableRow"><td class="image_data_Name highlight"></td>' +
                '<td class="maxInstance highlight"></td>' +
                '<td class="reusable highlight"></td>' +
                '<td class="edit highlight"><a href="#/" class="editImageLink">edit</a></td>\
<td class="remove"><a href="#/" class="removeImageLink">delete</a></td>' +
                '</tr>',
                dockerCloudImage_Entrypoint: '<td><input type="text" id="dockerCloudImage_Entrypoint_IDX"/><span class="error" id="dockerCloudImage_Entrypoint_IDX_error"></span></td>',
                dockerCloudImage_CapAdd: '<td><input type="text" id="dockerCloudImage_CapAdd_IDX"/><span class="error" id="dockerCloudImage_CapAdd_IDX_error"></span></td>',
                dockerCloudImage_CapDrop: '<td><input type="text" id="dockerCloudImage_CapDrop_IDX"/><span class="error" id="dockerCloudImage_CapDrop_IDX_error"></span></td>',
                dockerCloudImage_Cmd: '<td><input type="text" id="dockerCloudImage_Cmd_IDX"/></td>',
                dockerCloudImage_Volumes: '<td><input type="text" id="dockerCloudImage_Volumes_IDX_PathOnHost" /></td>\
        <td><input type="text" id="dockerCloudImage_Volumes_IDX_PathInContainer" /><span class="error" id="dockerCloudImage_Volumes_IDX_PathInContainer_error"></span></td>\
        <td class="center"><input type="checkbox" id="dockerCloudImage_Volumes_IDX_ReadOnly" /></td>',
                dockerCloudImage_Devices: '<td><input type="text" id="dockerCloudImage_Devices_IDX_PathOnHost" /></td>\
        <td><input type="text" id="dockerCloudImage_Devices_IDX_PathInContainer" /></td>\
        <td><input type="text" id="dockerCloudImage_Devices_IDX_CgroupPermissions" /></td>',
                dockerCloudImage_Env: '<td><input type="text" id="dockerCloudImage_Env_IDX_Name" /><span class="error" id="dockerCloudImage_Env_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Env_IDX_Value" /></td>',
                dockerCloudImage_Labels: '<td><input type="text" id="dockerCloudImage_Labels_IDX_Key" /><span class="error" id="dockerCloudImage_Labels_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Labels_IDX_Value" /></td>',
                dockerCloudImage_Links: '<td><input type="text" id="dockerCloudImage_Links_IDX_Container" /><span class="error" id="dockerCloudImage_Links_IDX_Container_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Links_IDX_Alias" /><span class="error" id="dockerCloudImage_Links_IDX_Alias_error"></span></td>',
                dockerCloudImage_LogConfig: '<td><input type="text" id="dockerCloudImage_LogConfig_IDX_Key" /><span class="error" id="dockerCloudImage_LogConfig_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_LogConfig_IDX_Value" /></td>',
                dockerCloudImage_StorageOpt: '<td><input type="text" id="dockerCloudImage_StorageOpt_IDX_Key" /><span class="error" id="dockerCloudImage_StorageOpt_IDX_Key_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_StorageOpt_IDX_Value" /></td>',
                dockerCloudImage_Ulimits: ' <td><input type="text" id="dockerCloudImage_Ulimits_IDX_Name" /><span class="error" id="dockerCloudImage_Ulimits_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Ulimits_IDX_Soft" /><span class="error" id="dockerCloudImage_Ulimits_IDX_Soft_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_Ulimits_IDX_Hard" /><span class="error" id="dockerCloudImage_Ulimits_IDX_Hard_error"></span></td>',
                dockerCloudImage_Ports: '<td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_HostIp" />\
                <span class="error" id="dockerCloudImage_Ports_IDX_HostIp_error"></td>\
        <td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_HostPort" size="5"/><span class="error"\
         id="dockerCloudImage_Ports_IDX_HostPort_error"></td>\
        <td class="center"><input type="text" id="dockerCloudImage_Ports_IDX_ContainerPort" size="5"/><span class="error" id="dockerCloudImage_Ports_IDX_ContainerPort_error"></span></td>\
        <td class="center"><select id="dockerCloudImage_Ports_IDX_Protocol">\
            <option value="tcp" selected="selected">tcp</option>\
            <option value="udp">udp</option>\
        </select></td>',
                dockerCloudImage_Dns: '<td><input type="text" id="dockerCloudImage_Dns_IDX"/><span class="error" id="dockerCloudImage_Dns_IDX_error"></span></td>',
                dockerCloudImage_DnsSearch: '<td><input type="text" id="dockerCloudImage_DnsSearch_IDX"/><span class="error" id="dockerCloudImage_DnsSearch_IDX_error"></span></td>',
                dockerCloudImage_ExtraHosts: ' <td><input type="text" id="dockerCloudImage_ExtraHosts_IDX_Name" /><span class="error" id="dockerCloudImage_ExtraHosts_IDX_Name_error"></span></td>\
        <td><input type="text" id="dockerCloudImage_ExtraHosts_IDX_Ip" /><span class="error" id="dockerCloudImage_ExtraHosts_IDX_Ip_error"></span></td>'
            }
        };
        return self;
    })();

if (typeof OO !== "undefined") {
    BS.DockerImageDialog = OO.extend(BS.AbstractModalDialog, {
            getContainer: function () {
                return $('DockerCloudImageDialog');
            }
        });

    BS.DockerTestContainerDialog = OO.extend(BS.AbstractModalDialog, {
            getContainer: function () {
                return $('DockerTestContainerDialog');
            }
        });

    BS.DockerDiagnosticDialog = OO.extend(BS.AbstractModalDialog, {
            getContainer: function () {
                return $('DockerDiagnosticDialog');
            }
        });
}


