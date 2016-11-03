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
        /* CONSTANTS
         var START_STOP = 'START_STOP',
         FRESH_CLONE = 'FRESH_CLONE',
         ON_DEMAND_CLONE = 'ON_DEMAND_CLONE',
         CURRENT_STATE = '__CURRENT_STATE__',
         LATEST_SNAPSHOT='*';
         */
        /*
         var $j=$;
         */
        //noinspection JSUnresolvedVariable
        var self = {
            selectors: {
                editImageLink: '.editImageLink',
                imagesTableRow: '.imagesTableRow'
            },
            init: function (defaultLocalAddress, checkConnectionUrl, testContainerUrl, imagesId) {
                self.defaultLocalAddress = defaultLocalAddress;
                self.checkConnectionUrl = checkConnectionUrl;
                self.testContainerUrl = testContainerUrl;


                self.hasWebSocketSupport = 'WebSocket' in window;

                self.$image = $j("#dockerCloudImage_Image");
                self.$checkConnectionBtn = $j("#dockerCloudCheckConnectionBtn");
                self.$checkConnectionError = $j('#dockerCloudCheckConnectionError');
                self.$newImageBtn = $j('#dockerShowDialogButton');
                self.$imageDialogSubmitBtn = $j('#dockerAddImageButton');
                self.$imageDialogCancelBtn = $j('#dockerCancelAddImageButton');
                self.$imagesTable = $j('#dockerCloudImagesTable');
                self.$images = $j(BS.Util.escapeId(imagesId));
                console.log("Load image from " + BS.Util.escapeId(imagesId) + " (" + imagesId + ")");
                self.$dockerAddress = $j("#dockerCloudDockerAddress");
                self.$useLocalInstance = $j("#dockerCloudUseLocalInstance");
                self.$useCustomInstance = $j("#dockerCloudUseCustomInstance");
                self.$checkConnectionLoader = $j('#dockerCloudCheckConnectionLoader');
                self.$testContainerLoader = $j('#dockerCloudTestContainerLoader');
                self.$testContainerLabel = $j('#dockerCloudTestContainerLabel');
                self.$testContainerOutcome = $j('#dockerCloudTestContainerOutcome');
                self.$testContainerCancelBtn = $j('#dockerCloudTestContainerCancelBtn');
                self.$testContainerShellBtn = $j('#dockerCloudTestContainerShellBtn');
                self.$testContainerCopyLogsBtn = $j('#dockerCloudTestContainerCopyLogsBtn');
                self.$testContainerDisposeBtn = $j('#dockerCloudTestContainerDisposeBtn');
                self.$testContainerCloseBtn = $j('#dockerCloudTestContainerCloseBtn');
                self.$testContainerSuccessIcon = $j('#dockerCloudTestContainerSuccess');
                self.$testContainerErrorIcon = $j('#dockerCloudTestContainerError');
                self.$checkConnectionSuccess = $j('#dockerCloudCheckConnectionSuccess');

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
                self.$imageTestContainerCreateBtn = $j("#dockerCreateImageTest");
                self.$imageTestContainerStartBtn = $j("#dockerStartImageTest");
                self.$dockerTestContainerOutput = $j("#dockerTestContainerOutput");
                self.$testedImage = $j(BS.Util.escapeId("run.var.teamcity.docker.cloud.tested_image"));


                self._initImagesData();
                self._initTabs();
                self._initValidators();
                self._bindHandlers();
                self._renderImagesTable();
                self._setupTooltips();
            },

            /* MAIN SETTINGS */

            _renderImagesTable: function () {
                self.$imagesTable.empty();
                $j.each(self.imagesData, function(i, val) {
                    self._renderImageRow(val);
                });
                self._insertAddButton(self.$imagesTable, 4);
            },

            _setupTooltips: function() {
                // Our tooltip div holder. Dynamically added at the end of the body and absolutely positioned under
                // the tooltip icon. Having the tooltip div outside of containers with non-visible overflow (like
                // dialogs), prevent it from being cut-off.
                self.tooltipHolder = $j('<div id="tooltipHolder"></div>').appendTo($j('body')).hide();
                $j('span.tooltiptext').hide();
                $j('i.tooltip').mouseover(function() {
                    var tooltipText = $j(this).siblings('span.tooltiptext');
                    self.tooltipHolder.html(tooltipText.html());
                    console.log('offset ist ' + $j(this).offset());
                    console.log('top is ' + $j(this).offset()['top']);
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
                console.log('updating address');
                var useLocalInstance = self.$useLocalInstance.is(':checked');
                self.$dockerAddress.prop('disabled', useLocalInstance).val(useLocalInstance ? self.defaultLocalAddress : "");
            },

            _checkConnection: function () {
                var checkConnectionDeferred = $j.Deferred()
                    .done(function (response) {
                        var $response = $j(response.responseXML);
                        var $error = $response.find("error");
                        if ($error.length) {
                            self._setConnectionError($error.text());
                        } else {
                            var $version = $response.find("version");
                            self.$checkConnectionSuccess.text('Connection successful to Docker version ' + $version.attr('docker') +
                                ' (API: ' + $version.attr('api') + ') on ' +
                                $version.attr('os') + '/' + $version.attr('arch')).removeClass('hidden');
                        }

                        return response;
                    })
                    .fail(function (errorText) {
                        self._setConnectionError('Unable to check for connectivity: ' + errorText);
                        return errorText;
                    })
                    .always(function () {
                        self.$checkConnectionLoader.addClass('hidden');
                        self._toggleCheckConnectionBtn(true);
                    });

                self._toggleCheckConnectionBtn();
                //self._toggleLoadingMessage('_checkConnection', true);
                self.$checkConnectionLoader.removeClass('hidden');
                self.$checkConnectionSuccess.addClass('hidden');

                BS.ajaxRequest(self.checkConnectionUrl, {
                    parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                    onFailure: function (response) {
                        self.checkConnectionDeferred.reject(response.getStatusText());
                    },
                    onSuccess: function (response) {
                        var $response = $j(response.responseXML),
                            $errors = $response.find('errors:eq(0) error');

                        if ($errors.length) {
                            checkConnectionDeferred.reject($errors.text());
                        } else {
                            checkConnectionDeferred.resolve(response);
                        }
                    }
                });

                return false; // to prevent link with href='#' to scroll to the top of the page
            },

            _setConnectionError: function (errorHTML) {
                self.$checkConnectionError.append($j('<div>').html(errorHTML));
            },

            /* IMAGE DATA MARSHALLING / UNMARSHALLING */

            _initImagesData: function () {

                var images = [{
                    Administration: {
                        Profile: 'my_image',
                        RmOnExit: true,
                        BindAgentProps: true,
                        UseOfficialTCAgentImage: false,
                        MaxInstanceCount: 2,
                        Version: 1
                    },
                    Container: {
                        Image: "test/someimage",
                        Labels:  { "com.github.blah": "42" },
                        Entrypoint: ["/bin/bash", "B", "C"],
                        Cmd: ["Arg 1", "", "Arg 3"],
                        WorkingDir: "/tmp/some_dir",
                        StopSignal: "SIGINT",
                        Volumes: [ "/tmp/some_volume" ],
                        Env: ["JAVA_HOME=/opt/java"],
                        LogType: "syslog",
                        LogConfig: { Type:  "syslog", Config: {"max-file-size" : "42"}},
                        User: "root",
                        Hostname: "localhost",
                        Domainname: "test.com",
                        ExposedPorts: {"22/tcp": {}, "23/udp": {}},
                        HostConfig: {
                            Links: [ "some_container:web"],
                            Binds: [  "/tmp/host_dir:/tmp/container_dir:ro" ],
                            PortBindings: {"24/tcp" : [{ HostIp: "129.0.0.1", HostPort: 122 }]},
                            Privileged: true,
                            Devices: [{
                                PathOnHost: "/dev/some_dev",
                                PathInContainer: "/dev/other_dev",
                                CgroupPermissions: "blarh"
                            }],
                            OomKillDisable: true,
                            Capabilities: "allow_all",
                            CapAdd: ["CHOWN", "DAC_OVERRIDE", "FSETID"],
                            CapDrop: ["FOWNER", "MKNOD", "NET_RAW"],
                            NetworkMode: "host",
                            NetworkContainer: "some_container",
                            NetworkCustom: "my great network",
                            Dns: ["8.8.8.8"],
                            DnsSearch: ["example.com"],
                            ExtraHosts: ["google.com:9.9.9.9"],
                            PublishAllPorts: true,
                            Memory: 268435456,
                            MemorySwap: 134217728,
                            CpuShares: 50,
                            CpuPeriod: 10,
                            CpusetCpus: "cpus",
                            CpusetMems: "mems",
                            BlkioWeight: 50,
                            Ulimits: [{Name: "nofile", Soft: 5000, Hard: 6000}],
                            CgroupParent: "cpu/Multimedia",
                        }
                    },
                    Editor: {
                        MemoryUnit: "GiB",
                        MemorySwapUnit: "MiB"
                    }
                }];

                var json = self.$images.val();
                console.log("Image data: " + self.$images.val().length + " " + self.$images.val());

                images = json ? JSON.parse(self.$images.val()) : [];
                self.imagesData = {};
                console.log(images.length + " images to be loaded.");
                $j.each(images, function(i, image) {
                    console.log("Loading profile: " + image.Administration.Profile);
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

            _resetImageDataFields: function() {
                // Only clears dynamically added rows. First-level fields are assumed to be always cleared when binding
                // is performed.
              $j('tbody', self.$dialog).empty();
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
                }
            },

            _insertAddButton: function($tbody, colspan) {
                $tbody.append('<tr class="dockerCloudAddItem"><td colspan="' + colspan + '" class="dockerCloudCtrlCell"> <a class="btn dockerCloudAddBtn" href="#"  title="Add item"><span class="dockerCloudAddBtn">Add</span></a></td></tr>');
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
                    } else {
                        hostConfig.NetworkMode = viewModel.NetworkCustom;
                    }
                }


                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'Devices');
                self._convertViewModelFieldToSettingsField(viewModel, hostConfig, 'Ulimits');

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
                if (networkMode === "bridge" || networkMode === "host" || networkMode === "none") {
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
                            span.append('<img src="/img/attentionCommentRed.png" />');
                        } else if (tab.warnings.length) {
                            span.append('<img src="/img/attentionComment.png" />');
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
                console.log("Binding handlers now");

                self.$useLocalInstance.change(self._addressState);
                self.$useCustomInstance.change(self._addressState)
                self._addressState();

                self.$checkConnectionBtn.on('click', self._checkConnectionClickHandler);
                self.$newImageBtn.on('click', self._showImageDialogClickHandler);

                self.$imageDialogSubmitBtn.click(function() {

                    self._triggerAllFields(true);

                    if(!self.updateOkBtnState()) {
                        return false;
                    }

                    var viewModel = self._restoreViewModel();
                    var settings = self._convertViewModelToSettings(viewModel);

                    var currentProfile = self.$imageDialogSubmitBtn.data('profile');
                    var newProfile = settings.Administration.Profile;
                    delete self.imagesData[currentProfile];
                    self.imagesData[newProfile] = settings;
                    self.$images.val(JSON.stringify(self.imagesData));
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
                    console.log("Add handler invoked");
                    var $elt = $j(this);

                    // Fetch closest table.
                    var $tableBody = $elt.closest("tbody");
                    var key = $tableBody.attr("id");
                    var index = $j.data($tableBody.get(0), "index") || 0;
                    console.log('Index on table body:' + index);
                    index++;
                    var $table = $elt.closest("table");
                    console.log('Adding entry ' + index + ' to table ' + key);
                    console.log('<tr>' + self.arrayTemplates[key].replace(/IDX/g, index) + '<td' +
                        ' class="center dockerCloudCtrlCell">' + self.arrayTemplates.deleteCell + '</td></tr>');
                    $elt.closest("tr").before('<tr>' + self.arrayTemplates[key].replace(/IDX/g, index) + '<td' +
                    ' class="center dockerCloudCtrlCell">' + self.arrayTemplates.deleteCell + '</td></tr>');
                    $j.data($tableBody.get(0), "index", index);
                    self._updateTableMandoryStarsVisibility($table);
                    console.log('Saving new index on table body: ' + index);
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


                self.$imageTestContainerCreateBtn.click(function() {

                    self._testContainerProgress = null;

                    // Pack the current image settings into a hidden field to be submitted.
                    var viewModel = self._restoreViewModel();
                    var settings = self._convertViewModelToSettings(viewModel);
                    self.$testedImage.val(JSON.stringify(settings));

                    self._invokeTestAction('create', null, BS.Clouds.Admin.CreateProfileForm.serializeParameters());
                });

                self.$imageTestContainerBtn.click(function() {

                    self._triggerAllFields(true);

                    if(!self.updateOkBtnState()) {
                        return false;
                    }


                    self._initTestDialog();
                    BS.DockerTestContainerDialog.showCentered();
                });

                self.$testContainerCloseBtn.click(function() {
                    BS.DockerTestContainerDialog.close();
                });

            },

            _processTestResponse: function (responseMap) {
                self._testDialogHideAllBtns();

                console.log('Progress: ' + responseMap.progress + ' Status: ' + responseMap.status + ' Msg: ' + responseMap.msg + ' Uuid: ' + responseMap.taskUuid);

                if (responseMap.status == 'PENDING') {
                    self.$testContainerLabel.text(responseMap.msg);
                    self.$testContainerLoader.show();
                    self.$testContainerCancelBtn.show();

                    if (self.hasWebSocketSupport) {
                        if (!self.testStatusSocket) {
                            console.log('Opening test status listener socket now.');
                            // TODO: FIX URL
                            self.testStatusSocket = new WebSocket("ws://129.0.0.1:8111/app/docker-cloud/test-container/getStatus?taskUuid=" + responseMap.taskUuid);
                            self.testStatusSocket.onmessage = function (event) {
                                console.log('Processing status from server.');
                                self._processTestResponse(self._parseResponse(event.data));
                            }
                        }
                    } else {
                        console.log('Scheduling status retrieval.');
                        setTimeout(self._invokeTestAction.bind(this, 'query', responseMap.taskUuid), 5000);
                    }
                } else {
                    self.$testContainerLoader.hide();
                    self.$testContainerCancelBtn.hide();

                    if (self.testStatusSocket) {
                        self.testStatusSocket.close();
                        delete self.testStatusSocket;
                    }

                    if (responseMap.status == 'FAILURE') {
                        self.$testContainerLabel.addClass('systemProblemsBar');
                        self.$testContainerErrorIcon.show();
                        self.$testContainerCloseBtn.show();

                        if (responseMap.failureCause) {
                            //var errorDetails = $j('#dockerCloudTestContainerErrorDetails');
                            var errorDetailsMsg = $j('#dockerCloudTestContainerErrorDetailsMsg').empty();
                            var errorDetailsStackTrace = $j('#dockerCloudTestContainerErrorDetailsStackTrace').empty();

                            errorDetailsMsg.text(responseMap.msg);
                            errorDetailsStackTrace.text(responseMap.failureCause);

                            var viewDetailsLink = $j('<a href="#">view details</a>)').click(function () {
                                BS.DockerDiagnosticDialog.showCentered();
                            });
                            self.$testContainerLabel.append(' (').append(viewDetailsLink).append(')');
                        }
                    } else if (responseMap.status == 'SUCCESS') {
                        self.$testContainerSuccessIcon.show();
                        self.$testContainerLabel.text("Test completed successfully.");
                        self.$testContainerCloseBtn.show();
                    } else {
                        console.error('Unrecognized status: ' + responseMap.status)
                    }
                }

                /*
                var NONE = 0;
                var CREATED = 1;
                var STARTED = 2;

                var containerState = NONE;
                if (responseMap.phase === 'CREATE' && responseMap.status === 'SUCCESS') {
                    containerState = CREATED;
                } else if (responseMap.phase === 'START') {
                    containerState = responseMap.status === 'SUCCESS' ? STARTED : CREATED;
                } else if (responseMap.phase === 'WAIT_FOR_AGENT') {
                    containerState = STARTED;
                }

                if (containerState == STARTED) {
                    // Container has been started, display live logs if possible.

                    if (self.hasWebSocketSupport && !self.logStreamingSocket) {
                        console.log('Opening live logs sockt now.');
                        var url = 'ws://localhost:8111/app/docker-cloud/streaming/logs?correlationId=' +
                            responseMap.taskUuid;
                        self.logStreamingSocket = new WebSocket(url);
                        var logTerm = new Terminal();
                        logTerm.open(self.$dockerTestContainerOutput[0]);
                        logTerm.fit();
                        logTerm.attach(self.logStreamingSocket);
                        logTerm.convertEol = true;
                    }

                }

                if (responseMap.status === 'PENDING') {
                    self.$testContainerLoader.show();
                    self.$testContainerCancelBtn.show();

                    if (self.hasWebSocketSupport) {
                        if (!self.testStatusSocket) {
                            console.log('Opening test status listener socket now.');
                            self.testStatusSocket = new WebSocket("ws://localhost:8111/app/docker-cloud/test-container/getStatus?taskUuid=" + responseMap.taskUuid);
                            self.testStatusSocket.onmessage = function(event) {
                                console.log('Processing status from server.');
                                self._processTestResponse(self._parseResponse(event.data));
                            }
                        }
                    } else {
                        console.log('Scheduling status retrieval.');
                        setTimeout(self._invokeTestAction.bind(this, 'query', responseMap.taskUuid), 5000);
                    }
                } else {
                    self.$testContainerLoader.hide();
                    self.$testContainerCancelBtn.hide();

                    if (containerState === CREATED) {
                        self.$imageTestContainerStartBtn.click(self._invokeTestAction.bind(this, 'start', responseMap.taskUuid));
                        self.$imageTestContainerStartBtn.show();
                    }

                    if (containerState >= CREATED) {
                        self.$testContainerDisposeBtn.click(self._invokeTestAction.bind(this, 'dispose', responseMap.taskUuid));
                        self.$testContainerDisposeBtn.show();
                    }

                    if (containerState == STARTED) {
                        self.$testContainerDisposeBtn.show();
                        self.$testContainerCopyLogsBtn.show();
                        self.$testContainerShellBtn.show();

                    } else if (self.logStreamingSocket) {
                        self.logStreamingSocket.close();
                    }
                    if (self.testStatusSocket) {
                        self.testStatusSocket.close();
                        delete self.testStatusSocket;
                    }
                }*/
            },

            _parseResponse: function(xml) {
                var $xml = $j(xml);
                return {
                    msg: $xml.find('msg').text(),
                    status: $xml.find('status').text(),
                    phase: $xml.find('phase').text(),
                    taskUuid: $xml.find("taskUuid").text(),
                    failureCause: $xml.find("failureCause").text()
                };
            },

            _invokeTestAction: function(action, taskUuid, parameters) {

                console.log('Will invoke action ' + action + ' for UUID ' + taskUuid);
                var deferred = $j.Deferred();

                // Invoke test action.
                var url = self.testContainerUrl + '?action=' + action;
                if (taskUuid) {
                    url += '&taskUuid=' + taskUuid;
                }

                BS.ajaxRequest(url, {
                    parameters: parameters,
                    onSuccess: function (response) {
                        var responseMap = self._parseResponse(response.responseXML);
                        deferred.resolve(responseMap);
                    },
                    onFailure: function (response) {;
                        deferred.reject(response.responseText);
                    }
                });

                // Global failure handler: only show
                deferred.fail(function(errorMsg) {
                    // Invocation failure, show the message, but left the UI untouched, the user may choose to retry
                    // the failed operation.
                    self.$testContainerLabel.text(errorMsg);
                    self.$testContainerLabel.addClass('systemProblemsBar');
                });

                deferred.done(self._processTestResponse);

                return deferred;
            },
            _checkConnectionClickHandler: function () {
                if (self.$checkConnectionBtn.attr('disabled') !== 'true') { // it may be undefined
                    self._checkConnection();
                }

                return false; // to prevent link with href='#' to scroll to the top of the page
            },
            _toggleCheckConnectionBtn: function (enable) {
                // $fetchOptionsButton is basically an anchor, also attribute allows to add styling
                self.$checkConnectionBtn.attr('disabled', !enable);
            },
            _toggleLoadingMessage: function (loaderName, show) {
                self.loaders[loaderName][show ? 'removeClass' : 'addClass']('message_hidden');
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
                self.$imageTestContainerCreateBtn.hide();
                self.$imageTestContainerStartBtn.hide();
                self.$testContainerCancelBtn.hide();;
                self.$testContainerCloseBtn.hide();
                self.$testContainerCopyLogsBtn.hide();
                self.$testContainerDisposeBtn.hide();
                self.$testContainerShellBtn.hide();
            },

            _initTestDialog: function () {
                self._testDialogHideAllBtns();
                self.$imageTestContainerCreateBtn.show()
                self.$testContainerOutcome.text();
                self.$testContainerCloseBtn.show();
                self.$testContainerLoader.hide();
                self.$testContainerSuccessIcon.hide();
                self.$testContainerErrorIcon.hide();
                self.$testContainerLabel.empty();
                self.$testContainerLabel.removeClass('systemProblemsBar');
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
                deleteCell: '<a class="btn dockerCloudCtrlBtn dockerCloudDeleteBtn" href="#" title="Delete"><span></span></a>',
                settingsCell: '<a class="btn dockerCloudCtrlBtn dockerCloudSettingsBtn" href="#" title="Settings"><span></span></a>',
                dockerCloudImage_imagesTableRow: '<tr class="imagesTableRow"><td class="image_data_Name highlight"></td>' +
                '<td class="maxInstance highlight"></td>' +
                '<td class="reusable highlight"></td>' +
                '<td class="edit highlight"><a href="#" class="editImageLink">edit</a></td>\
<td class="remove"><a href="#" class="removeImageLink">delete</a></td>' +
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

