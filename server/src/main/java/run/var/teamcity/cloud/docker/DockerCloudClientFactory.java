package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.web.DockerCloudSettingsController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker {@link CloudClientFactory}.
 */
public class DockerCloudClientFactory implements CloudClientFactory {

    @NotNull private final String editProfileUrl;
    @NotNull private final SBuildServer buildServer;
    @NotNull private final ServerPaths serverPaths;

    public DockerCloudClientFactory(@NotNull final SBuildServer buildServer,
                                    @NotNull final CloudRegistrar cloudRegistrar,
                                   @NotNull final PluginDescriptor pluginDescriptor,
                                    @NotNull final ServerPaths serverPaths) {
        this.editProfileUrl = pluginDescriptor.getPluginResourcesPath(DockerCloudSettingsController.EDIT_PATH);
        cloudRegistrar.registerCloudFactory(this);
        this.buildServer = buildServer;
        this.serverPaths = serverPaths;
    }


    @NotNull
    @Override
    public CloudClientEx createNewClient(@NotNull CloudState state, @NotNull CloudClientParameters params) {
        serverPaths.getPluginDataDirectory();
        Collection<String> paramNames = params.listParameterNames();
        Map<String, String> properties = new HashMap<>(paramNames.size());
        for (String paramName : paramNames) {
            properties.put(paramName, params.getParameter(paramName));
        }
        DockerCloudClientConfig clientConfig = DockerCloudClientConfig.processParams(properties);
        List<DockerImageConfig> imageConfigs = DockerImageConfig.processParams(properties);
        return new DockerCloudClient(clientConfig, imageConfigs, state, buildServer);
    }

    @NotNull
    @Override
    public String getCloudCode() {
        return "VRDC";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Docker";
    }

    @NotNull
    @Override
    public String getEditProfileUrl() {
        return editProfileUrl;
    }

    @NotNull
    @Override
    public Map<String, String> getInitialParameterValues() {
        HashMap<String, String> params = new HashMap<>();
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, Boolean.TRUE.toString());
        return params;
    }

    @NotNull
    @Override
    public jetbrains.buildServer.serverSide.PropertiesProcessor getPropertiesProcessor() {
        return new DockerCloudPropertiesProcessor();
    }

    @Override
    public boolean canBeAgentOfType(@NotNull AgentDescription description) {
        return DockerCloudUtils.getImageId(description) != null && DockerCloudUtils.getInstanceId(description) != null;
    }
}
