<%@ page import="run.var.teamcity.cloud.docker.AgentHolderInfo" %>
<%@ page import="run.var.teamcity.cloud.docker.DockerInstance" %>
<%@ page import="run.var.teamcity.cloud.docker.util.DockerCloudUtils" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.FormatStyle" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Optional" %>
<%@ page import="run.var.teamcity.cloud.docker.util.Resources" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="image" type="run.var.teamcity.cloud.docker.DockerImage" scope="request"/>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='/plugins/docker-cloud/docker-cloud.css'/>");
</script>

<div class="dockerCloudImageDetails">

    <%
        Resources resources = image.getCloudClient().getCloudSupport().resources();
    %>

    <h4><%= resources.text("web.imageDetails.title") %></h4>
    <div style="margin: 5px 10%; width: 90%">
        <table style="width: 85%;">
            <thead>
            <tr>
                <th style="width: 20%;"><%= resources.text("web.imageDetails.col.agentHolderId") %></th>
                <th style="width: 20%;">Created</th>
                <th style="width: 20%;">State</th>
                <th style="width: 20%;">Name</th>
                <th style="width: 20%;">Image</th>
            </tr>
            </thead>
            <tbody>
            <%
                DateTimeFormatter dateFmt = DateTimeFormatter.
                        ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).
                        withLocale(Locale.ENGLISH);
                for (DockerInstance instance : image.getInstances()) {
                    Optional<AgentHolderInfo> optAgentHolderInfo = instance.getAgentHolderInfo();
                    if (!optAgentHolderInfo.isPresent()) {
                        continue;
                    }
                    AgentHolderInfo agentHolderInfo = optAgentHolderInfo.get();
            %>
            <tr>
                <td><%= DockerCloudUtils.toShortId(agentHolderInfo.getId()) %>
                </td>
                <td><%= dateFmt.format(agentHolderInfo.getCreationTimestamp().atZone(ZoneId.systemDefault())) %>
                </td>
                <td><%= agentHolderInfo.getStateMsg() %>
                </td>
                <td><%= agentHolderInfo.getName() %>
                </td>
                <td><%= instance.getResolvedImageName().orElse("") %>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>
    </div>
    <%
        Optional<Instant> lastDockerSyncTime = image.getCloudClient().getLastDockerSyncTime();
        String lastSync;
        //noinspection OptionalIsPresent
        if (lastDockerSyncTime.isPresent()) {
            //noinspection unchecked
            lastSync = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).
                            withLocale(Locale.ENGLISH).
                            format(lastDockerSyncTime.get().atZone(ZoneId.systemDefault()));
        } else {
            lastSync = "not performed yet.";
        }
    %>
    Last sync with docker: <%= lastSync %>
</div>