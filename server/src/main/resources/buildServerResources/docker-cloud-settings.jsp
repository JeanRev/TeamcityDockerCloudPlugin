<%@ page import="run.var.teamcity.cloud.docker.util.DockerCloudUtils" %>
<%@ page import="jetbrains.buildServer.clouds.CloudImageParameters" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<!-- Disable IDEA warnings about unused variables. -->
<%--@elvariable id="resPath" type="java.lang.String"--%>
<%--@elvariable id="defaultTCUrl" type="java.net.URL"--%>
<%--@elvariable id="debugEnabled" type="java.lang.Boolean"--%>
<%--@elvariable id="defaultUnixSocketAvailable" type="java.lang.Boolean"--%>
<c:set var="paramName" value="<%=DockerCloudUtils.IMAGES_PARAM%>"/>

<jsp:useBean id="serverUrl" scope="request" type="java.lang.String"/>

</table>

<script type="text/javascript">
    <jsp:include page="/js/bs/blocks.js"/>
    <jsp:include page="/js/bs/blocksWithHeader.js"/>
</script>


<div class="dockerCloudSettings">

    <h2 class="noBorder section-header">Docker Connection Settings</h2>

    <script type="text/javascript">
        BS.LoadStyleSheetDynamically("<c:url value='${resPath}docker-cloud.css'/>");
        BS.LoadStyleSheetDynamically("<c:url value='${resPath}xterm.css'/>");
    </script>

    <table class="runnerFormTable">
        <tbody>
        <tr>
            <th>Docker instance:&nbsp;<l:star/></th>
            <td>

                <c:choose>
                    <c:when test="${defaultUnixSocketAvailable}">
                        <p>
                            <props:radioButtonProperty name="<%=DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM%>"
                                                       id="dockerCloudUseLocalInstance"
                                                       value="true"/>
                            <label for="dockerCloudUseLocalInstance">Use local Docker instance</label>
                        </p>
                        <p>
                            <props:radioButtonProperty name="<%=DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM%>"
                                                       id="dockerCloudUseCustomInstance"
                                                       value="false"/>
                            <label for="dockerCloudUseCustomInstance">Use custom Docker instance URL</label>
                        </p>
                        <span class="error" id="error_<%=DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM%>"></span>
                    </c:when>
                    <c:otherwise>
                        <props:hiddenProperty name="<%=DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM%>" value="false"/>
                    </c:otherwise>
                </c:choose>

                <p>
                    <label for="dockerCloudDockerAddress">Address:&nbsp;<span id="addressStar"><l:star/></span>&nbsp;
                    </label>
                    <props:textProperty name="<%=DockerCloudUtils.INSTANCE_URI%>" id="dockerCloudDockerAddress"
                                        className="longField"/>
                    <a href="#/" class="btn" id="dockerCloudCheckConnectionBtn">Check connection</a>
                    <span class="smallNote">Daemon URI, starting either with a <code>tcp:</code> or <code>unix:</code> scheme.</span>
                    <span class="error" id="error_<%=DockerCloudUtils.INSTANCE_URI%>"></span>
                </p>
                <p>
                    <props:checkboxProperty name="<%=DockerCloudUtils.USE_TLS%>"/>
                    <label for="<%=DockerCloudUtils.USE_TLS%>">Use Transport Layer Security (TLS)</label>
                    <i class="icon icon16 tc-icon_help_small tooltip"></i>
                    <span class="tooltiptext">Activate TLS support when connecting to Docker over TCP socket. Checkout the plugin wiki for additional info on how to configure TLS properly.</span>
                </p>
                <div class="hidden" id="dockerCloudCheckConnectionLoader"><i class="icon-refresh icon-spin"></i>&nbsp;Connecting
                    to Docker instance...
                </div>
            </td>
        </tr>
    </table>
    <div id="dockerCloudCheckConnectionResult" class="message hidden"></div>
    <div id="dockerCloudCheckConnectionWarning" class="message warningMessage hidden"></div>

    <h2 class="noBorder section-header">Agent Images <span class="error"
                                                           id="error_<%=DockerCloudUtils.IMAGES_PARAM%>"></span></h2>

    <props:hiddenProperty name="<%=DockerCloudUtils.TEST_IMAGE_PARAM%>"/>
    <props:hiddenProperty name="<%=DockerCloudUtils.CLIENT_UUID%>"/>
    <jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
    <c:set var="sourceImagesJson" value="${propertiesBean.properties['source_images_json']}"/>
    <input type="hidden" name="prop:source_images_json" id="source_images_json" value="<c:out value='${sourceImagesJson}'/>" data-err-id="source_images_json"/>
    <c:set var="imagesData" value="${propertiesBean.properties['run.var.teamcity.docker.cloud.img_param']}"/>
    <input type="hidden" name="prop:run.var.teamcity.docker.cloud.img_param"
           id="run.var.teamcity.docker.cloud.img_param" value="<c:out value="${imagesData}"/>"/>
    <c:set var="daemonInfo" value="${propertiesBean.properties['run.var.teamcity.docker.cloud.daemon_info']}"/>
    <input type="hidden" name="prop:run.var.teamcity.docker.cloud.daemon_info"
           id="run.var.teamcity.docker.cloud.daemon_info" value="<c:out value="${daemonInfo}"/>"/>

    <table class="settings" style="width: 75%; margin-left: 25%">
        <thead>
        <tr>
            <th class="name" style="width: 30%;">Profile</th>
            <th class="name" style="width: 30%;">Image name</th>
            <th class="name center" style="width: 15%;">Max Instance #</th>
            <th class="name center" style="width: 15%;">Delete on exit</th>
            <th class="dockerCloudCtrlCell" style="width: 10%;"></th>
        </tr>
        </thead>
        <tbody id="dockerCloudImagesTable">

        </tbody>
    </table>

    <table class="runnerFormTable">
        <tbody>
        <tr>
            <th>TeamCity server URL:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">TeamCity server URL for the agents to connect. May be left empty to use the default server address.</span>
            </th>
            <td>
                <props:textProperty name="<%=DockerCloudUtils.SERVER_URL_PARAM%>" className="longField"/>
                <span class="error" id="error_<%=DockerCloudUtils.SERVER_URL_PARAM%>"></span>
            </td>
        </tr>
        </tbody>
    </table>

    <bs:dialog dialogId="DockerCloudImageDialog" title="Add Image" closeCommand="BS.DockerImageDialog.close()"
               titleId="DockerImageDialogTitle">
    <div id="dockerCloudImageTabContainer" class="simpleTabs"></div>

    <div class="dockerCloudSettings" id="dockerCloudImageContainer">
        <div id="dockerCloudImageTab_general">
            <table class="dockerCloudSettings runnerFormTable">
                <tr>
                    <th><label for="dockerCloudImage_Profile">Profile name:&nbsp;<l:star/></label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_Profile" class="mediumField"/>
                        <span class="error" id="dockerCloudImage_Profile_error"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_Image">Docker image:&nbsp;<l:star/></label>
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Docker image name to be started, using the usual syntax: <code>[registry[:registry_port]/]name[:tag]</code>. If
        no tag is specified, <code>latest</code> will be used as a default. The image name may also be prefixed with a
        registry hostname and port from which the image must be pulled. If you do not specify a registry hostname, the
        default Docker public registry will be used instead.</span>
                    </th>
                    <td>
                        <p>
                            <input type="checkbox" id="dockerCloudImage_UseOfficialTCAgentImage"/>
                            <label for="dockerCloudImage_UseOfficialTCAgentImage">Use official TeamCity agent
                                image</label>
                        </p>
                        <p>
                            <input type="text" id="dockerCloudImage_Image" class="mediumField"/>
                            <span class="error" id="dockerCloudImage_Image_error"></span>
                        </p>
                        <span class="smallNote">
      Docker image name to be started.
    </span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_Image">Maximum instance count:&nbsp;</label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_MaxInstanceCount" class="mediumField"/>
                        <span class="error" id="dockerCloudImage_MaxInstanceCount_error"></span>
                    </td>
                </tr>
                <tr>
                    <th>Management:</th>
                    <td>
                        <p>
                            <input type="checkbox" id="dockerCloudImage_PullOnCreate"/>
                            <label for="dockerCloudImage_PullOnCreate">Pull image before creating container</label>
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">Always attempt to pull the agent image before creating
                                containers. If this box is unchecked, the agent image will need to be already available
                                in the daemon local image store.</span>
                        </p>
                        <p>
                            <input type="checkbox" id="dockerCloudImage_RmOnExit"/>
                            <label for="dockerCloudImage_RmOnExit">Delete container when cloud agent is stopped</label>
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">You may check this box if you want to remove the container as
                                soon as the corresponding cloud instance is stopped. This may potentially free some
                            disk space, and ensure that the next build will start in a totally fresh state. However, it
                            also means that all the agent meta-data and applied server plugin upgrade so far will be
                                lost.</span>
                        </p>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_RegistryUser">Registry User:</label></th>
                    <td>
                        <p>
                            <input type="text" id="dockerCloudImage_RegistryUser" class="mediumField"/>
                            <span class="error" id="dockerCloudImage_RegistryUser_error"></span>
                        </p>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_RegistryPassword">Registry Password:</label></th>
                    <td>
                        <p>
                            <input type="password" id="dockerCloudImage_RegistryPassword" class="mediumField"/>
                            <span class="error" id="dockerCloudImage_RegistryPassword_error"></span>
                        </p>
                    </td>
                </tr>
            </table>
        </div>
        <div id="dockerCloudImageTab_run">

            <table class="dockerCloudSettings runnerFormTable">
                <tr>
                    <th><label for="dockerCloudImage_User">User:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">A string value specifying the user inside the container.</span>
                    </label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_User"/>
                    </td>
                </tr>
                <tr>
                    <th>
                        <label for="dockerCloudImage_WorkingDir">Working <span style="white-space: nowrap">
                            directory:
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">A string specifying the working directory for commands to run
                                in.</span>
                            </span>
                        </label>
                    </th>
                    <td>
                        <input type="text" id="dockerCloudImage_WorkingDir"/>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_StopSignal">Stop signal:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Signal to stop a container as a string or unsigned integer.
                            <code>SIGTERM</code> by default.</span>
                    </label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_StopSignal"/>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_StopTimeout">Stop timeout:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Timeout to stop a container in seconds.
                            <code>10</code> seconds by default.<br/><b>Requires API:</b> v1.25</span>
                    </label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_StopTimeout"/>
                        <span class="error" id="dockerCloudImage_StopTimeout_error"></span>
                        <span class="warning" id="dockerCloudImage_StopTimeout_warning"></span>
                    </td>
                </tr>
            </table>
            <h4>Command:</h4>
            <div class="dockerCloudSimpleTables">
                <table class="settings">
                    <thead>
                    <tr>
                        <th class="name" style="width: 82%;">
                            Entrypoint executable / args

                            <i class="icon icon16 tc-icon_help_small tooltip">
                            </i>

                            <span class="tooltiptext">Overwrite the default <code>ENTRYPOINT</code> of the image using
                            an array
                            of string.</span>

                        </th>
                        <th class="dockerCloudCtrlCell"></th>
                    </tr>
                    </thead>
                    <tbody id="dockerCloudImage_Entrypoint">
                    </tbody>
                </table>
                <table class="settings">
                    <thead>
                    <tr>
                        <th class="name" style="width: 82%;">
                            Command executable / args
                            <i class="icon icon16 tc-icon_help_small tooltip">
                            </i>
                            <span class="tooltiptext">Overwrite the default <code>CMD</code> of the image using an
                                array of string.
                        </span>


                        </th>
                        <th class="dockerCloudCtrlCell"></th>
                    </tr>
                    </thead>
                    <tbody id="dockerCloudImage_Cmd">
                    </tbody>
                </table>
            </div>
            <h4>
                Environment variables:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">Set environment variables.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 45%;">Name&nbsp;<l:star/></th>
                    <th class="name" style="width: 45%;">Value</th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Env">
                </tbody>
            </table>
        </div>
        <div id="dockerCloudImageTab_privileges">
            <table class="dockerCloudSettings runnerFormTable">
                <tr>
                    <th>
                        Privileged:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Give extended privileges to this container. The default is false.</span>
                    </th>
                    <td>
                        <input type="checkbox" id="dockerCloudImage_Privileged"/>
                        <label for="dockerCloudImage_Privileged">Extended privileges</label>
                    </td>
                </tr>

                <tr>
                    <th>
                        <label for="dockerCloudImage_CgroupParent">Cgroup parent:
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">Path to cgroups under which the container's cgroup is created. If
                                the path is not absolute, the path is considered to be relative to the cgroups path of
                                the init process. Cgroups are created if they do not already exist.</span>
                        </label>
                    </th>
                    <td>
                        <input type="text" id="dockerCloudImage_CgroupParent"/>
                    </td>
                </tr>
            </table>
            <h4>Kernel capabilities:</h4>
            <div class="dockerCloudSimpleTables">
                <table class="settings">
                    <thead>
                    <tr>
                        <th class="name" style="width: 82%;">
                            Added capabilities
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">A list of kernel capabilities to add to the container.</span>
                        </th>
                        <th class="dockerCloudCtrlCell"></th>
                    </tr>
                    </thead>
                    <tbody id="dockerCloudImage_CapAdd">
                    </tbody>
                </table>
                <table class="settings">
                    <thead>
                    <tr>
                        <th class="name" style="width: 82%;">
                            Dropped capabilities
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">A list of kernel capabilities to drop from the container.</span>
                        </th>
                        <th class="dockerCloudCtrlCell"></th>
                    </tr>
                    </thead>
                    <tbody id="dockerCloudImage_CapDrop">
                    </tbody>
                </table>
            </div>
        </div>

        <div id="dockerCloudImageTab_network">
            <table class="dockerCloudSettings runnerFormTable">
                <tr>
                    <th>
                        <label for="dockerCloudImage_Hostname">
                            Hostname:
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">A string value containing the hostname to use for the container. This
                            must be a valid RFC 1123 hostname.</span>
                        </label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_Hostname"/>
                    </td>
                </tr>
                <tr>
                    <th>
                        <label for="dockerCloudImage_Domainname">Domain name:</label>
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">A string value containing the domain name to use for the container
                        .</span>
                    </th>
                    <td>
                        <input type="text" id="dockerCloudImage_Domainname"/>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_NetworkMode">Network mode:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">
                    Set the Network mode for the container:
                            <ul>
                                <li>Bridge: create a network stack on the default Docker bridge.</li>
                                <li>Container: reuse another container's network stack.</li>
                                <li>Host: use the Docker host network stack. Note: the host mode gives the container
                                    full access to local system services such as D-bus and is therefore considered
                                    insecure.</li>
                                <li>
                                    Custom: connect to a user-defined network.
                                </li>
                            </ul>
                        </span>


                    </label></th>
                    <td>
                        <table class="dockerCloudSubtable">
                            <tr>
                                <td>
                                    <select id="dockerCloudImage_NetworkMode">
                                        <option value="">Default</option>
                                        <option value="bridge">Bridge</option>
                                        <option value="host">Host</option>
                                        <option value="container">Container:</option>
                                        <option value="custom">Custom:</option>
                                        <!--Well, not a valid use case for an agent. <option value="none">None</option>-->
                                    </select>
                                </td>
                                <td>
                                    <input type="text" id="dockerCloudImage_NetworkContainer" class="mediumField"/>
                                    <input type="text" id="dockerCloudImage_NetworkCustom" class="mediumField"/>
                                </td>
                            </tr>
                            <tr>
                                <td></td>
                                <td>
                                    <span class="error" id="dockerCloudImage_NetworkContainer_error"></span>
                                    <span class="error" id="dockerCloudImage_NetworkCustom_error"></span>
                                </td>
                            </tr>
                        </table>
            </table>
            <h4>Exposed/published ports:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">A list of containers ports to be exposed to the host, and optionally published
                to one of the host interface.
            </span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name center" style="width: 32%">Host IP</th>
                    <th class="name center" style="width: 20%">Host port</th>
                    <th class="name center" style="width: 20%">Container Port&nbsp;<l:star/></th>
                    <th class="name center" style="width: 20%">Protocol</th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Ports">
                </tbody>
            </table>
            <h4>DNS:</h4>
            <div class="dockerCloudSimpleTables">
                <table class="settings">
                    <thead>
                    <tr>
                        <th class="name" style="width: 82%;">Server Address&nbsp;<l:star/>
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">A list of DNS servers for the container to use.</span>
                        </th>
                        <th class="dockerCloudCtrlCell"></th>
                    </tr>
                    </thead>
                    <tbody id="dockerCloudImage_Dns">
                    </tbody>
                </table>
                <table class="settings">
                    <thead>
                    <tr>
                        <th class="name" style="width: 82%;">Search domains&nbsp;<l:star/>
                            <i class="icon icon16 tc-icon_help_small tooltip"></i>
                            <span class="tooltiptext">A list of DNS search domains.</span>
                        </th>
                        <th class="dockerCloudCtrlCell"></th>
                    </tr>
                    </thead>
                    <tbody id="dockerCloudImage_DnsSearch">
                    </tbody>
                </table>
            </div>
            <h4>Extra hosts:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">A list of hostnames/IP mappings to add to the container's <code>/etc/hosts</code>
                file</span></h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 45%;">Name&nbsp;<l:star/></th>
                    <th class="name" style="width: 45%;">IP
                        Address&nbsp;<l:star/></th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_ExtraHosts">
                </tbody>
            </table>
            <h4>Link container:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">A list of links for the container.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 45%;">Container&nbsp;<l:star/></th>
                    <th class="name" style="width: 45%;">Alias&nbsp;<l:star/></th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Links">
                </tbody>
            </table>
        </div>

        <div id="dockerCloudImageTab_resources">
            <table class="dockerCloudSettings runnerFormTable">
                <tr>
                    <th><label for="dockerCloudImage_Memory">Memory:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Memory limit.</span>
                    </label></th>
                    <td>
                        <input type="text" class="textField" id="dockerCloudImage_Memory"/>
                        <select id="dockerCloudImage_MemoryUnit">
                            <option value="bytes" selected="selected">bytes</option>
                            <option value="KiB">KiB</option>
                            <option value="MiB">MiB</option>
                            <option value="GiB">GiB</option>
                        </select>
                        <span class="error" id="dockerCloudImage_Memory_error"></span>
                    </td>
                </tr>
                <tr>
                    <th>Swap:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Total memory limit (memory limit + swap). Must be greater than <b>
                        Memory</b>.</span>
                    </th>
                    <td>
                        <p>
                            <input type="checkbox" id="dockerCloudImage_MemorySwapUnlimited"/>
                            <label for="dockerCloudImage_MemorySwapUnlimited">Unlimited</label>
                        </p>
                        <p>
                            <input type="text" class="textField" id="dockerCloudImage_MemorySwap"/>
                            <select id="dockerCloudImage_MemorySwapUnit">
                                <option value="bytes" selected="selected">bytes</option>
                                <option value="KiB">KiB</option>
                                <option value="MiB">MiB</option>
                                <option value="GiB">GiB</option>
                            </select>
                            <span class="error" id="dockerCloudImage_MemorySwap_error"></span>
                        </p>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_CPUs">Number of CPUs:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Limit the number of CPUs available for execution by the container.
                            Decimal values can be specified (e.g. <code>1.5</code> to allocate at most 1.5 CPU to the
                            container). This option is equivalent to the <code>--cpus</code> CLI flag and is effective
                            for both Linux and Windows containers.
                        <br/><b>Requires API:</b> v1.25</span></label>
                    </th>
                    <td>
                        <input type="text" id="dockerCloudImage_CPUs" class="textField"/>
                        <span class="error" id="dockerCloudImage_CPUs_error"></span>
                        <span class="warning" id="dockerCloudImage_CPUs_warning"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_CpuQuota">CPU Quota:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Microseconds of CPU time that the container can get in a CPU period.</span></label>
                    </th>
                    <td>
                        <input type="text" id="dockerCloudImage_CpuQuota" class="textField"/>
                        <span class="error" id="dockerCloudImage_CpuQuota_error"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_CpuPeriod">CPU Period:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">The length of a CPU period in microseconds. Accepts a value between
                        1000&micro;s (1ms) and 1000000&micro;s (1s)</span></label></th>
                    <td>
                        <input type="text" class="textField" id="dockerCloudImage_CpuPeriod"/>
                        <span class="error" id="dockerCloudImage_CpuPeriod_error"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_CpusetCpus">cpuset - CPUs:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">String value containing the cgroups <code>CpusetCpus</code> to use.</span></label>
                    </th>
                    <td>
                        <input type="text" id="dockerCloudImage_CpusetCpus" class="textField"/>
                        <span class="error" id="dockerCloudImage_CpusetCpus_error"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_CpusetMems">cpuset - MEMs:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Memory nodes (MEMs) in which to allow execution. Only
                        effective on NUMA systems. Format: CPU index, or range of index using <code>-</code> as
                        separator. Example: <code>0-3, 0, 1</code></span></label></th>
                    <td>
                        <input type="text" class="textField" id="dockerCloudImage_CpusetMems"/>

                        <span class="error" id="dockerCloudImage_CpusetMems_error"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_CpuShares">CPU Shares:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">CPU shares (relative weight).</span></label></th>
                    <td>
                        <input type="text" class="textField" id="dockerCloudImage_CpuShares"/>
                        <span class="error" id="dockerCloudImage_CpuShares_error"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="dockerCloudImage_BlkioWeight">Bulk IO weight:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Block IO weight (relative weight) accepts a weight value between 10 and
                        1000.</span></label></th>
                    <td>
                        <input type="text" id="dockerCloudImage_BlkioWeight" class="textField"/>
                        <span class="error" id="dockerCloudImage_BlkioWeight_error"></span>
                    </td>
                </tr>
            </table>

            <h4>Ulimit:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">A list of ulimits to set in the container.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 30%">Name&nbsp;<l:star/></th>
                    <th class="name" style="width: 30%">Soft
                        limit&nbsp;<l:star/></th>
                    <th class="name" style="width: 30%">Hard limit&nbsp;<l:star/></th>
                    <th
                            class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Ulimits">

                </tbody>
            </table>
        </div>

        <div id="dockerCloudImageTab_advanced">
            <table class="dockerCloudSettings runnerFormTable">
                <tr>
                    <th>OOM killer:
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Disable OOM Killer for the container or not.</span></th>
                    <td>
                        <input type="checkbox" id="dockerCloudImage_OomKillDisable"
                               data-bind="checked: oom_kill_disable"/>
                        <label for="dockerCloudImage_OomKillDisable">Disable OOM killer.</label>
                    </td>
                </tr>
                <tr>
                    <th>
                        <label for="dockerCloudImage_LogType">Logging drivers:</label>
                        <i class="icon icon16 tc-icon_help_small tooltip"></i>
                        <span class="tooltiptext">Log configuration for the container.</span>
                    </th>
                    <td>
                        <input id="dockerCloudImage_LogType" type="text">
                    </td>
                </tr>
            </table>
            <h4>Logging options:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">Configuration map for the logging driver.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 45%;">Option Key&nbsp;<l:star/></th>
                    <th class="name" style="width: 45%;">Option Value</th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_LogConfig">
                </tbody>
            </table>
            <h4>Storage options:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">Configuration map for the storage.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 45%;">Option Key&nbsp;<l:star/></th>
                    <th class="name" style="width: 45%;">Option Value</th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_StorageOpt">
                </tbody>
            </table>
            <h4>Volumes:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">Defines volumes, and optionally, their bound location on the host file system.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 35%">Host path</th>
                    <th class="name" style="width: 35%">Container path&nbsp;<l:star/></th>
                    <th class="name center" style="width: 20%;">Read only</th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Volumes">
                </tbody>
            </table>
            <h4>Labels:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext">Adds a map of labels to a container.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 45%;">Key&nbsp;<l:star/></th>
                    <th class="name" style="width: 45%;">Value</th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Labels">
                </tbody>
            </table>
            <h4>Devices:
                <i class="icon icon16 tc-icon_help_small tooltip"></i>
                <span class="tooltiptext"> A list of devices to add to the container.</span>
            </h4>
            <table class="settings">
                <thead>
                <tr>
                    <th class="name" style="width: 30%;">Host path&nbsp;<l:star/></th>
                    <th class="name"
                        style="width: 30%;">Container
                        path&nbsp;<l:star/></th>
                    <th class="name" style="width: 30%;">CGroup permissions&nbsp;<l:star/></th>
                    <th class="dockerCloudCtrlCell"></th>
                </tr>
                </thead>
                <tbody id="dockerCloudImage_Devices">
                </tbody>
            </table>
        </div>

    </div>
    <div class="popupSaveButtonsBlock dockerCloudBtnBlock">
        <input type="button" class="btn" id="dockerTestImageButton" value="Test container"/>
        <input type="button" class="btn btn_primary" id="dockerAddImageButton" value="Add"/>
        <input type="button" class="btn" id="dockerCancelAddImageButton" value="Cancel"/>
    </div>
    </bs:dialog>

    <bs:dialog dialogId="DockerTestContainerDialog" title="Test Container"
               closeCommand="BS.Clouds.Docker.cancelTest()">
    <div>
        <p>
            This test will create a container using the provided settings, which can then be started in order to ensure
            that the agent is able to connect to your TeamCity instance.
        </p>
        <h4 id="dockerTestContainerOutputTitle">Container live logs:</h4>
        <div id="dockerTestContainerOutput">
        </div>
        <span class="hidden" id="dockerCloudTestContainerLoader"><i class="icon-refresh icon-spin"></i>
        </span>
        <img class="hidden dockerCloudStatusIcon" id="dockerCloudTestContainerSuccess"
             src="<c:url value="${resPath}img/checked.png"/>">
        <img class="hidden dockerCloudStatusIcon" id="dockerCloudTestContainerWarning"
             src="<c:url value="${resPath}img/warning.png"/>">
        <img class="hidden dockerCloudStatusIcon" id="dockerCloudTestContainerError"
             src="<c:url value="${resPath}img/error.png"/>">
        <span id="dockerCloudTestContainerLabel"></span>

        <p id="dockerTestExecInfo">
        </p>
        <div class="dockerCloudBtnBlock">
            <!--
            <input type="button" class="btn" id="dockerCloudTestContainerShellBtn" value="Start a shell"/>
            <input type="button" class="btn" id="dockerCloudTestContainerCopyLogsBtn" value="Copy logs"/>
            <input type="button" class="btn" id="dockerCloudTestContainerDisposeBtn" value="Dispose container"/>
            -->
            <input type="button" class="btn" id="dockerCreateImageTest" value="Create container"/>
            <input type="button" class="btn" id="dockerStartImageTest" value="Start container"/>
            <input type="button" class="btn" id="dockerCloudTestContainerContainerLogsBtn" value="Container logs"/>
            <input type="button" class="btn" id="dockerCloudTestContainerCancelBtn" value="Cancel"/>
            <input type="button" class="btn" id="dockerCloudTestContainerCloseBtn" value="Close"/>
        </div>
    </div>
    </bs:dialog>

    <bs:dialog dialogId="DockerDiagnosticDialog" title="Diagnostic"
               closeCommand="BS.DockerDiagnosticDialog.close()">

    <span id="dockerCloudTestContainerErrorDetailsMsg" class="mono"></span>
    <div id="dockerCloudTestContainerErrorDetailsStackTrace" class="problemDetails mono custom-scroll">
    </div>
    <div class="dockerCloudBtnBlock">
        <p>
            <input type="button" class="btn" id="dockerDiagnosticCopyBtn" value="Copy to clipboard"
                   data-clipboard-target="#dockerCloudTestContainerErrorDetailsStackTrace"/>
            <input type="button" class="btn" id="dockerDiagnosticCloseBtn" value="Close"/>
        </p>
    </div>
    </bs:dialog>
    <script type="text/javascript">
        $j.when(
            $j.getScript("<c:url value="${resPath}clipboard.min.js"/>"),
            $j.getScript("<c:url value="${resPath}ua-parser.min.js"/>"),
            $j.getScript("<c:url value="${resPath}base64js.min.js"/>"),
            $j.when($j.getScript("<c:url value="${resPath}xterm.js"/>"))
                .done(function () {
                    $j.getScript("<c:url value="${resPath}attach/attach.js"/>");
                    $j.getScript("<c:url value="${resPath}fit/fit.js"/>");
                }))
            .done(function () {
                $j.ajax({
                    url: "<c:url value="${resPath}docker-cloud.js"/>",
                    dataType: "script",
                    success: function () {
                        BS.Clouds.Docker.init({
                            defaultLocalSocketURI: '<%=DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI%>',
                            checkConnectivityCtrlURL: '<c:url value="${resPath}checkconnectivity.html"/>',
                            testContainerCtrlURL: '<c:url value="${resPath}test-container.html"/>',
                            useTlsParam: '<%=DockerCloudUtils.USE_TLS%>',
                            imagesParam: '<%=DockerCloudUtils.IMAGES_PARAM%>',
                            tcImagesDetails: '<%= CloudImageParameters.SOURCE_IMAGES_JSON %>',
                            daemonTargetVersion: '<%=DockerCloudUtils.DOCKER_API_TARGET_VERSION.getVersionString()%>',
                            daemonMinVersion: '<%=DockerCloudUtils.DOCKER_API_MIN_VERSION.getVersionString()%>',
                            errorIconURL: '<c:url value="/img/attentionCommentRed.png"/>',
                            warnIconURL: '<c:url value="/img/attentionComment.png"/>',
                            testStatusSocketPath: '<c:url value="/app/docker-cloud/test-container/getStatus"/>',
                            streamSocketPath: '<c:url value="/app/docker-cloud/streaming/logs"/>',
                            defaultUnixSocketAvailable: ${defaultUnixSocketAvailable},
                            debugEnabled: ${debugEnabled}
                        });
                    }
                })
            });
    </script>

    <table class="runnerFormTable">