/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

var BS = BS || {};
BS.Clouds = BS.Clouds || {};
BS.Clouds.Docker = BS.Clouds.Docker || (function () {

        //noinspection JSUnresolvedVariable
        var self = {
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

                var imagesParam = params.imagesParam;

                self.hasWebSocketSupport = 'WebSocket' in window;
                self.hasXTermSupport = self.hasWebSocketSupport && self.checkXtermBrowserSupport();
                if (self.hasXTermSupport) {
                    self.logInfo("XTerm support enabled.")
                }

                self.$image = $j("#dockerCloudImage_Image");
                self.$checkConnectionBtn = $j("#dockerCloudCheckConnectionBtn");
                self.$checkConnectionSuccess = $j('#dockerCloudCheckConnectionSuccess');
                self.$checkConnectionError = $j('#dockerCloudCheckConnectionError');
                self.$newImageBtn = $j('#dockerShowDialogButton');
                self.$imageDialogSubmitBtn = $j('#dockerAddImageButton');
                self.$imageDialogCancelBtn = $j('#dockerCancelAddImageButton');
                self.$imagesTable = $j('#dockerCloudImagesTable');
                self.$images = $j(BS.Util.escapeId(imagesParam));
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
                self._initTabs();
                self._initValidators();
                self._bindHandlers();
                self._renderImagesTable();
                self._setupTooltips();
            },

            /* MAIN SETTINGS */

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
            _addressState: function () {
                var useLocalInstance = self.$useLocalInstance.is(':checked');
                self.$dockerAddress.prop('disabled', useLocalInstance).val(useLocalInstance ? self.defaultLocalSocketURI : "");
            },

            _checkConnection: function () {
                self._toggleCheckConnectionBtn();
                self.$checkConnectionLoader.show();
                self.$checkConnectionSuccess.hide().empty();
                self.$checkConnectionError.hide().empty();

                var deferred = $j.Deferred();

                BS.ajaxRequest(self.checkConnectivityCtrlURL, {
                    parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                    onFailure: function (response) {
                        deferred.reject(response.getStatusText());
                    },
                    onSuccess: function (response) {
                        deferred.resolve(response);
                    }
                });

                deferred.
                fail(function(msg) {
                    self.$checkConnectionError.append($j('<div>')).text(msg);
                }).
                done(function(response) {
                    var $response = $j(response.responseXML);
                    var $error = $response.find("error");
                    if ($error.length) {
                        var $container = self.$checkConnectionError.append($j('<div>').append('<span>'));
                        $container.text($error.text());
                        var $failureCause = $response.find("failureCause");
                        if ($failureCause.length) {
                            self.prepareDiagnosticDialogWithLink($container, "Checking for connectivity failed.", $failureCause.text());
                        }
                        self.$checkConnectionError.append($container).show();
                    } else {
                        var $version = $response.find("version");
                        self.$checkConnectionSuccess.text('Connection successful to Docker version ' + $version.attr('docker') +
                            ' (API: ' + $version.attr('api') + ') on ' +
                            $version.attr('os') + '/' + $version.attr('arch')).show();
                    }
                }).
                always(function () {
                    self.$checkConnectionLoader.hide();
                    self._toggleCheckConnectionBtn(true);
                });

                return false; // to prevent link with href='#' to scroll to the top of the page
            },

            /* IMAGE DATA MARSHALLING / UNMARSHALLING */

            _initImagesData: function () {
                self.logDebug("Processing images data.");

                var json = self.$images.val();

                images = json ? JSON.parse(self.$images.val()) : [];
                self.imagesData = {};
                self.logDebug(images.length + " images to be loaded.");
                $j.each(images, function(i, image) {
                    self.imagesData[image.Administration.Profile] = image;
                });

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
                    if (!arrayTemplate) {
                        alert("Template not found: " + key)
                    }
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
                } else if ($elt.is(':text')) {
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
                            MaxInstanceCount: 2
                        }
                    });
                self._applyViewModel(viewModel);
                self._updateAllTablesMandoryStarsVisibility();
                self.selectTabWithId("dockerCloudImageTab_general");
                self.imageDataTabbedPane.setActiveCaption("dockerCloudImageTab_general");

                self.$imageDialogSubmitBtn.val(existingImage ? 'Save' : 'Add').data('image-id', profileName).data('profile', profileName);

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
                } else if ($elt.is(':text')) {
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

                admin.Version = 1;

                self._convertViewModelFieldToSettingsField(viewModel, admin, 'RmOnExit');
                self._convertViewModelFieldToSettingsField(viewModel, admin, 'BindAgentProps');
                if (self._filterFromSettings(viewModel.MaxInstanceCount)) {
                    admin.MaxInstanceCount = parseInt(viewModel.MaxInstanceCount);
                }
                self._convertViewModelFieldToSettingsField(viewModel, admin, 'UseOfficialTCAgentImage');
                self._convertViewModelFieldToSettingsField(viewModel, admin, 'Profile');

                var container = settings.Container = {};
                self._convertViewModelFieldToSettingsField(viewModel, container, 'Hostname');
                self._convertViewModelFieldToSettingsField(viewModel, container, 'Domainname');
                self._convertViewModelFieldToSettingsField(viewModel, container, 'User');

                if (self._filterFromSettings(viewModel.Env)) {
                    container.Env = [];
                    self._safeEach(viewModel.Env, function (envEntry) {
                        container.Env.push(envEntry.Name + '=' + envEntry.Value);
                    });
                }

                if (self._filterFromSettings(viewModel.Labels)) {
                    container.Labels = {};
                    self._safeEach(viewModel.Labels, function (label) {
                        container.Labels[label.Key] = label.Value;
                    });
                }

                self._convertViewModelFieldToSettingsField(viewModel, container, 'Cmd');
                self._convertViewModelFieldToSettingsField(viewModel, container, 'Entrypoint');
                self._convertViewModelFieldToSettingsField(viewModel, container, 'Image');

                if (self._filterFromSettings(viewModel.Volumes)) {
                    container.Volumes = {};
                    self._safeEach(viewModel.Volumes, function (volume) {
                        if (!volume.PathOnHost) {
                            container.Volumes[volume.PathInContainer] = {};
                        }
                    });
                }

                self._convertViewModelFieldToSettingsField(viewModel, container, 'WorkingDir');

                if (self._filterFromSettings(viewModel.Ports)) {
                    container.ExposedPorts = {};
                    self._safeEach(viewModel.Ports, function (port) {
                        if (!port.HostIp && !port.HostPort) {
                            container.ExposedPorts[port.HostPort + '/' + port.Protocol] = {};
                        }
                    });
                }

                self._convertViewModelFieldToSettingsField(viewModel, container, 'StopSignal');

                var hostConfig = container.HostConfig = {};

                if (self._filterFromSettings(viewModel.Volumes)) {
                    hostConfig.Binds = [];
                    self._safeEach(viewModel.Volumes, function (volume) {
                        if (volume.PathOnHost) {
                            hostConfig.Binds.push(volume.PathOnHost + ':' + volume.PathInContainer + ':' + (volume.ReadOnly ? 'ro' : 'rw'));
                        }
                    });
                }

                if (self._filterFromSettings(viewModel.Links)) {
                    hostConfig.Links = [];
                    self._safeEach(viewModel.Links, function (link) {
                        hostConfig.Links.push(link.Container + ':' + link.Alias);
                    });
                }

                if (self._filterFromSettings(viewModel.Memory)) {
                    hostConfig.Memory = parseInt(viewModel.Memory) * self._units_multiplier[viewModel.MemoryUnit];
                }
                if (self._filterFromSettings(viewModel.MemorySwap)) {
                    hostConfig.MemorySwap = viewModel.MemorySwapUnlimited ? -1 : (parseInt(viewModel.MemorySwap) * self._units_multiplier[viewModel.MemorySwapUnit]);
                }

                if (self._filterFromSettings(viewModel.CpuQuota)) {
                    hostConfig.CpuQuota = parseInt(viewModel.CpuQuota);
                }
                if (self._filterFromSettings(viewModel.CpuShares)) {
                    hostConfig.CpuShares = parseInt(viewModel.CpuShares);
                }
                if (self._filterFromSettings(viewModel.CpuPeriod)) {
                    hostConfig.CpuPeriod = parseInt(viewModel.CpuPeriod);
                }
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'CpusetCpus');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'CpusetMems');
                if (self._filterFromSettings(viewModel.BlkioWeight)) {
                    hostConfig.BlkioWeight = parseInt(viewModel.BlkioWeight);
                }
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'OomKillDisable');

                if (self._filterFromSettings(viewModel.Ports)) {
                    hostConfig.PortBindings = {};
                    self._safeEach(viewModel.Ports, function (port) {
                        if (port.HostIp || port.HostPort) {
                            var key = port.ContainerPort + '/' + port.Protocol;
                            var binding = hostConfig.PortBindings[key];
                            if (!binding) {
                                binding = hostConfig.PortBindings[key] = [];
                            }
                            binding.push({HostIp: port.HostIp, HostPort: port.HostPort});
                        }
                    });
                }


                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'PublishAllPorts');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'Privileged');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'Dns');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'DnsSearch');

                if (self._filterFromSettings(viewModel.ExtraHosts)) {
                    hostConfig.ExtraHosts = [];
                    self._safeEach(viewModel.ExtraHosts, function (extraHost) {
                        hostConfig.ExtraHosts.push(extraHost.Name + ':' + extraHost.Ip);
                    });
                }

                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'CapAdd');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'CapDrop');

                if (self._filterFromSettings(viewModel.NetworkMode)) {
                    var networkMode = viewModel.NetworkMode;
                    if (networkMode === 'bridge' || networkMode === 'host' || networkMode === 'none') {
                        hostConfig.NetworkMode = networkMode;
                    } else if (networkMode === 'container') {
                        hostConfig.NetworkMode = 'container:' + viewModel.NetworkContainer;
                    } else if (networkMode !== 'default') {
                        hostConfig.NetworkMode = viewModel.NetworkCustom;
                    }
                }


                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'Devices');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'Ulimits');

                if (self._filterFromSettings(viewModel.Ulimits)) {
                    hostConfig.Ulimits = [];
                    self._safeEach(viewModel.Ulimits, function (ulimit) {
                        hostConfig.Ulimits.push({ Name: ulimit.Name, Hard: parseInt(ulimit.Hard), Soft: parseInt(ulimit.Soft)});
                    });
                }

                if (self._filterFromSettings(viewModel.LogType)) {
                    hostConfig.LogConfig = {
                        Type: viewModel.LogType,
                        Config: {}
                    };

                    self._safeEach(viewModel.LogConfig, function (logConfig) {
                        hostConfig.LogConfig.Config[logConfig.Key] = logConfig.Value;
                    });
                }

                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'CgroupParent');

                var editor = settings.Editor = {};
                editor.MemoryUnit = viewModel.MemoryUnit;
                editor.MemorySwapUnit = viewModel.MemorySwapUnit;

                return settings;
            },
            _convertViewModelFieldToSettingsField: function(viewModel, settings, fieldName) {
                var value = viewModel[fieldName];
                if (self._filterFromSettings(value)) {
                    settings[fieldName] = value;
                }
            },
            _filterFromSettings: function(value) {
                return value !== undefined && (value.length === undefined || value.length) && ($j.type(value) != "object" || Object.keys(value).length);
            },
            _convertSettingsToViewModel: function(settings) {
                var viewModel = {};

                var admin = settings.Administration || {};
                var container = settings.Container || {};
                var editor = settings.Editor || {};

                viewModel.Profile = admin.Profile;
                viewModel.RmOnExit = admin.RmOnExit;
                viewModel.BindAgentProps = admin.BindAgentProps;
                viewModel.MaxInstanceCount = admin.MaxInstanceCount;
                viewModel.UseOfficialTCAgentImage = admin.UseOfficialTCAgentImage;

                viewModel.Hostname = container.Hostname;
                viewModel.Domainname = container.Domainname;
                viewModel.User = container.User;

                viewModel.Env = [];
                self._safeEach(container.Env, function(envEntry) {
                    var sepIndex = envEntry.indexOf('=');
                    if (sepIndex != -1) {
                        viewModel.Env.push({ Name: envEntry.substring(0, sepIndex), Value: envEntry.substring(sepIndex + 1)});
                    }
                });

                viewModel.Cmd = container.Cmd;
                viewModel.Entrypoint = container.Entrypoint;
                viewModel.Image = container.Image;

                viewModel.Volumes = [];
                self._safeKeyValueEach(container.Volumes, function(volume) {
                    viewModel.Volumes.push({ PathInContainer: volume, ReadOnly: false });
                });

                viewModel.WorkingDir = container.WorkingDir;

                viewModel.Labels = [];
                self._safeKeyValueEach(container.Labels, function(key, value) {
                    viewModel.Labels.push({ Key: key, Value: value});
                });

                viewModel.Ports = [];
                self._safeEach(container.ExposedPorts, function(exposedPort) {
                    var tokens = exposedPort.split('/');
                    viewModel.Ports.push({ ContainerPort: tokens[0], Protocol: tokens[1] })
                });

                viewModel.StopSignal = container.StopSignal;

                var hostConfig = container.HostConfig || {};

                self._safeEach(hostConfig.Binds, function(bind) {
                    var tokens = bind.split(':');
                    viewModel.Volumes.push({ PathOnHost: tokens[0], PathInContainer: tokens[1],  ReadOnly: tokens[2] === 'ro' });
                });

                viewModel.Links = [];
                self._safeEach(hostConfig.Links, function(link) {
                    var tokens = link.split(':');
                    viewModel.Links.push({ Container: tokens[0], Alias: tokens[1] })
                });

                viewModel.MemoryUnit = editor.MemoryUnit || 'bytes';
                viewModel.Memory = hostConfig.Memory && Math.floor(hostConfig.Memory / self._units_multiplier[viewModel.MemoryUnit]);
                viewModel.MemorySwapUnit = editor.MemorySwapUnit || 'bytes';
                viewModel.MemorySwap = hostConfig.MemorySwap && Math.floor(hostConfig.MemorySwap / self._units_multiplier[viewModel.MemorySwapUnit]);
                viewModel.MemorySwapUnlimited = hostConfig.MemorySwap == -1;
                viewModel.CpuQuota = hostConfig.CpuQuota;
                viewModel.CpuShares = hostConfig.CpuShares;
                viewModel.CpuPeriod = hostConfig.CpuPeriod;
                viewModel.CpusetCpus = hostConfig.CpusetCpus;
                viewModel.CpusetMems = hostConfig.CpusetMems;
                viewModel.BlkioWeight = hostConfig.BlkioWeight;
                viewModel.OomKillDisable = hostConfig.OomKillDisable;

                self._safeKeyValueEach(hostConfig.PortBindings, function(port, bindings) {
                    var tokens = port.split("/");
                    var containerPort = tokens[0];
                    var protocol = tokens[1];
                    self._safeEach(bindings, function(binding) {
                        viewModel.Ports.push({ HostIp: binding.HostIp, HostPort: binding.HostPort, ContainerPort: containerPort, Protocol: protocol })
                    });
                });

                viewModel.PublishAllPorts = hostConfig.PublishAllPorts;
                viewModel.Privileged = hostConfig.Privileged;
                viewModel.Dns = hostConfig.Dns;
                viewModel.DnsSearch = hostConfig.DnsSearch;

                viewModel.ExtraHosts = [];
                self._safeEach(hostConfig.ExtraHosts, function(extraHost) {
                    var tokens = extraHost.split(':');
                    viewModel.ExtraHosts.push({ Name: tokens[0], Ip: tokens[1] });
                });

                viewModel.CapAdd = hostConfig.CapAdd;
                viewModel.CapDrop = hostConfig.CapDrop;

                var networkMode = hostConfig.NetworkMode;
                if (!networkMode) {
                    viewModel.NetworkMode = 'default';
                } else if (networkMode === "bridge" || networkMode === "host" || networkMode === "none") {
                    viewModel.NetworkMode = networkMode;
                } else if (/^container:/.test(networkMode)) {
                    viewModel.NetworkMode = "container";
                    viewModel.NetworkContainer = networkMode.substring('container:'.length);
                } else {
                    viewModel.NetworkMode = 'custom';
                    viewModel.NetworkCustom = networkMode;
                }

                viewModel.Devices = [];
                self._safeEach(hostConfig.Devices, function(device) {
                    viewModel.Devices.push(device);
                });

                viewModel.Ulimits = [];
                self._safeEach(hostConfig.Ulimits, function(ulimit) {
                    viewModel.Ulimits.push(ulimit);
                });

                var logConfig = hostConfig.LogConfig;
                if (logConfig) {
                    viewModel.LogType = logConfig.Type;
                    viewModel.LogConfig = [];
                    self._safeKeyValueEach(logConfig.Config, function (key, value) {
                        viewModel.LogConfig.push({ Key: key, Value: value});
                    });
                }

                viewModel.CgroupParent = hostConfig.CgroupParent;

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

                self.$useLocalInstance.change(self._addressState);
                self.$useCustomInstance.change(self._addressState)
                self._addressState();

                self.$checkConnectionBtn.click(self._checkConnectionClickHandler);
                self.$newImageBtn.click(self._showImageDialogClickHandler);

                self.$dockerAddress.change(function() {
                    // Normalize the Docker address and do some auto-correction regarding count of slashes after the
                    // scheme.
                    var address = self.$dockerAddress.val();
                    var match = address.match(/([a-zA-Z]+?):\/*(.*)/);
                    var scheme;
                    var schemeSpecificPart;
                    if (match) {
                        // Some scheme detected.
                        scheme = match[1].toLowerCase();
                        schemeSpecificPart = match[2];
                    } else if (address.match(/[0-9].*/)) {
                        scheme = 'tcp';
                        schemeSpecificPart = address;
                    } else if (address.startsWith('/')) {
                        scheme = 'unix';
                        schemeSpecificPart = address
                    } else {
                        // Most certainly invalid, but let the server complain about it.
                        return;
                    }

                    self.$dockerAddress.val(scheme + ':' + (scheme == 'unix' ? '///' : '//') + schemeSpecificPart);
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
                    self.logDebug("Saving profile: " + newProfile + "(was: " + currentProfile + ")");
                    delete self.imagesData[currentProfile];
                    self.imagesData[newProfile] = settings;
                    var tmp = [];
                    self._safeKeyValueEach(self.imagesData, function(key, value) {
                       tmp.push(value);
                    });
                    self.$images.val(JSON.stringify(tmp));
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
                    self.$image.blur();
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
                    delete self.imagesData[$j(this).closest('tr').data('profile')];
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
                            self.testUuid = $j(response.responseXML).find('testUuid').text();
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
                            var logs = $j(response.responseXML).find('logs').text();
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
                            var responseMap = self._parseTestStatusResponse(response.responseXML);
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

            _parseTestStatusResponse: function(xml) {
                var $xml = $j(xml);
                var responseMap = {
                    msg: $xml.find('msg').text(),
                    containerId: $xml.find('containerId').text(),
                    status: $xml.find('status').text(),
                    phase: $xml.find('phase').text(),
                    taskUuid: $xml.find("taskUuid").text(),
                    failureCause: $xml.find("failureCause").text(),
                    warnings: []
                };

                $xml.find('warning').each(function() {
                    responseMap.warnings.push(this.innerText);
                });

                return responseMap;
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
                    if (value && !/^[0-9]+$/.test(value)) {
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

            _units_multiplier:  {
                GiB: 134217728,
                MiB: 131072,
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

