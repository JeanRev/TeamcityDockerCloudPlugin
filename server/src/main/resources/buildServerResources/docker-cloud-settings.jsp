
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
<%--@elvariable id="defaultLocalInstanceAvailable" type="java.lang.Boolean"--%>
<%--@elvariable id="defaultLocalInstanceParam" type="java.lang.String"--%>
<%--@elvariable id="defaultLocalInstanceURI" type="java.net.URI"--%>
<%--@elvariable id="windowsHost" type="java.lang.Boolean"--%>
<%--@elvariable id="webSocketEndpointsAvailable" type="java.lang.Boolean"--%>
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

                <c:if test="${defaultLocalInstanceAvailable}">
                    <p>
                        <props:radioButtonProperty name="${defaultLocalInstanceParam}"
                                                   id="dockerCloudUseLocalInstance"
                                                   value="true"/>
                        <label for="dockerCloudUseLocalInstance">Use local Docker instance</label>
                    </p>
                    <p>
                        <props:radioButtonProperty name="${defaultLocalInstanceParam}"
                                                   id="dockerCloudUseCustomInstance"
                                                   value="false"/>
                        <label for="dockerCloudUseCustomInstance">Use custom Docker instance URL</label>
                    </p>
                    <span class="error" id="error_${defaultLocalInstanceParam}"></span>
                </c:if>

                <p>
                    <label for="dockerCloudDockerAddress">Address:&nbsp;<span id="addressStar"><l:star/></span>&nbsp;
                    </label>
                    <props:textProperty name="<%=DockerCloudUtils.INSTANCE_URI%>" id="dockerCloudDockerAddress"
                                        className="longField"/>
                    <a href="#/" class="btn" id="dockerCloudCheckConnectionBtn">Check connection</a>
                    <c:choose>
                        <c:when test="${windowsHost}">
                            <span class="smallNote">Daemon URI, starting either with a <code>tcp:</code> or <code>
                                npipe:</code> (<span style="font-variant: all-small-caps;">EXPERIMENTAL</span>)
                                scheme.</span>
                        </c:when>
                        <c:otherwise>
                            <span class="smallNote">Daemon URI, starting either with a <code>tcp:</code> or <code>unix
                                :</code> scheme.</span>
                        </c:otherwise>
                    </c:choose>

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
        <%@include file="image-settings.html" %>
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
                            defaultLocalInstanceURI: '${defaultLocalInstanceURI}',
                            checkConnectivityCtrlURL: '<c:url value="${resPath}checkconnectivity.html"/>',
                            testContainerCtrlURL: '<c:url value="${resPath}test-container.html"/>',
                            useTlsParam: '<%=DockerCloudUtils.USE_TLS%>',
                            imagesParam: '<%=DockerCloudUtils.IMAGES_PARAM%>',
                            tcImagesDetails: '<%= CloudImageParameters.SOURCE_IMAGES_JSON %>',
                            daemonTargetVersion: '<%=DockerCloudUtils.DOCKER_API_TARGET_VERSION.getVersionString()%>',
                            daemonMinVersion: '<%=DockerCloudUtils.DOCKER_API_MIN_VERSION.getVersionString()%>',
                            errorIconURL: '<c:url value="/img/attentionCommentRed.png"/>',
                            warnIconURL: '<c:url value="/img/attentionComment.png"/>',
                            testStatusSocketPath: '<c:url value="/app/docker-cloud/ws/test-container-status"/>',
                            streamSocketPath: '<c:url value="/app/docker-cloud/ws/container-logs"/>',
                            webSocketEndpointsAvailable: ${webSocketEndpointsAvailable},
                            windowsHost: ${windowsHost},
                            debugEnabled: ${debugEnabled}
                        });
                    }
                })
            });
    </script>

    <table class="runnerFormTable">