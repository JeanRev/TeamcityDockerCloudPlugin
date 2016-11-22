package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.web.DockerCloudSettingsController;

import java.util.*;

/**
 * Docker {@link CloudClientFactory}.
 */
public class DockerCloudClientFactory implements CloudClientFactory {

    @NotNull private final String editProfileUrl;
    @NotNull private final SBuildServer buildServer;
    @NotNull private final DockerClientFactory dockerClientFactory;

    public DockerCloudClientFactory(@NotNull final SBuildServer buildServer,
                                    @NotNull final CloudRegistrar cloudRegistrar,
                                   @NotNull final PluginDescriptor pluginDescriptor) {
        this(buildServer, cloudRegistrar, pluginDescriptor, DockerClientFactory.getDefault());
    }

    DockerCloudClientFactory(@NotNull final SBuildServer buildServer,
                                    @NotNull final CloudRegistrar cloudRegistrar,
                                    @NotNull final PluginDescriptor pluginDescriptor,
                                    @NotNull final DockerClientFactory dockerClientFactory) {
        this.editProfileUrl = pluginDescriptor.getPluginResourcesPath(DockerCloudSettingsController.EDIT_PATH);
        cloudRegistrar.registerCloudFactory(this);
        this.buildServer = buildServer;
        this.dockerClientFactory = dockerClientFactory;
    }


    @NotNull
    @Override
    public CloudClientEx createNewClient(@NotNull CloudState state, @NotNull CloudClientParameters params) {
        Collection<String> paramNames = params.listParameterNames();
        Map<String, String> properties = new HashMap<>(paramNames.size());
        for (String paramName : paramNames) {
            properties.put(paramName, params.getParameter(paramName));
        }
        DockerCloudClientConfig clientConfig = DockerCloudClientConfig.processParams(properties, dockerClientFactory);
        List<DockerImageConfig> imageConfigs = DockerImageConfig.processParams(properties);

        final int threadPoolSize = Math.min(imageConfigs.size() * 2, Runtime.getRuntime().availableProcessors() + 1);
        clientConfig.getDockerClientConfig().threadPoolSize(threadPoolSize);

        return new DockerCloudClient(clientConfig, dockerClientFactory, imageConfigs,
                OfficialAgentImageResolver.forServer(buildServer), state, buildServer);
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
        // The cloud client UUID is generated here for new cloud profiles. It will be then persisted in the profile
        // plugin configuration.
        params.put(DockerCloudUtils.CLIENT_UUID, UUID.randomUUID().toString());
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM,
                String.valueOf(DockerCloudUtils.isDefaultDockerSocketAvailable()));
        return params;
    }

    @NotNull
    @Override
    public jetbrains.buildServer.serverSide.PropertiesProcessor getPropertiesProcessor() {
        return new DockerCloudPropertiesProcessor();
    }

    @Override
    public boolean canBeAgentOfType(@NotNull AgentDescription description) {
        return DockerCloudUtils.getClientId(description) != null;
    }
}
