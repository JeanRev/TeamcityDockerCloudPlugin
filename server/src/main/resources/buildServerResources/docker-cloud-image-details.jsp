<%@ page import="run.var.teamcity.cloud.docker.DockerInstance" %>
<%@ page import="run.var.teamcity.cloud.docker.util.DockerCloudUtils" %>
<%@ page import="run.var.teamcity.cloud.docker.util.Node" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Locale" %>
<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="image" type="run.var.teamcity.cloud.docker.DockerImage" scope="request"/>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='/plugins/docker-cloud/docker-cloud.css'/>");
</script>

<div class="dockerCloudImageDetails">
    <h4>Registered containers:</h4>
    <div style="margin: 5px 10%; width: 90%">
        <table style="width: 80%;">
            <thead>
            <tr>
                <th style="width: 30%;">Container ID</th>
                <th style="width: 20%;">Created</th>
                <th style="width: 20%;">State</th>
                <th style="width: 30%;">Names</th>
            </tr>
            </thead>
            <tbody>
            <%
                DateFormat dateFmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH);
                for (DockerInstance instance : image.getInstances()) {
                    Node containerInfo = instance.getContainerInfo();
                    if (containerInfo == null) {
                        continue;
                    }
                    StringBuilder displayName = new StringBuilder();
                    for (Node name : containerInfo.getArray("Names").getArrayValues()) {
                        if (displayName.length() > 0) {
                            displayName.append(", ");
                        }
                        displayName.append(name);
                    }
            %>
            <tr>
                <td><%= DockerCloudUtils.toShortId(containerInfo.getAsString("Id")) %>
                </td>
                <td><%= dateFmt.format(containerInfo.getAsLong("Created") * 1000) %>
                </td>
                <td><%= containerInfo.getAsString("State") %>
                </td>
                <td><%= displayName.toString() %>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>
    </div>
    <%
        String lastSync;
        long lastDockerSyncTimeMillis = image.getCloudClient().getLastDockerSyncTimeMillis();
        if (lastDockerSyncTimeMillis != -1) {
            lastSync = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, Locale.ENGLISH).format(lastDockerSyncTimeMillis);
        } else {
            lastSync = "not performed yet.";
        }

    %>
    Last sync with docker: <%= lastSync %>
</div>