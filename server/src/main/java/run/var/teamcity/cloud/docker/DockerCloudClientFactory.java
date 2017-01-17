package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeStorage;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.web.DockerCloudSettingsController;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Docker {@link CloudClientFactory}.
 */
public class DockerCloudClientFactory implements CloudClientFactory {

    private final String editProfileUrl;
    private final SBuildServer buildServer;
    private final DockerClientFactory dockerClientFactory;
    private final AgentTypeStorage agentTypeStorage;

    public DockerCloudClientFactory(@Nonnull final SBuildServer buildServer,
                                    @Nonnull final CloudRegistrar cloudRegistrar,
                                    @Nonnull final PluginDescriptor pluginDescriptor,
                                    @Nullable final AgentTypeStorage agentTypeStorage) {
        this(buildServer, cloudRegistrar, pluginDescriptor, DockerClientFactory.getDefault(), agentTypeStorage);
    }

    DockerCloudClientFactory(@Nonnull final SBuildServer buildServer,
                             @Nonnull final CloudRegistrar cloudRegistrar,
                             @Nonnull final PluginDescriptor pluginDescriptor,
                             @Nonnull final DockerClientFactory dockerClientFactory,
                             @Nullable final AgentTypeStorage agentTypeStorage) {
        this.editProfileUrl = pluginDescriptor.getPluginResourcesPath(DockerCloudSettingsController.EDIT_PATH);
        cloudRegistrar.registerCloudFactory(this);
        this.buildServer = buildServer;
        this.dockerClientFactory = dockerClientFactory;
        this.agentTypeStorage = agentTypeStorage;
    }


    @Nonnull
    @Override
    public CloudClientEx createNewClient(@Nonnull CloudState state, @Nonnull CloudClientParameters params) {
        Collection<String> paramNames = params.listParameterNames();
        Map<String, String> properties = new HashMap<>(paramNames.size());
        for (String paramName : paramNames) {
            properties.put(paramName, params.getParameter(paramName));
        }
        DockerCloudClientConfig clientConfig = DockerCloudClientConfig.processParams(properties, dockerClientFactory);
        List<DockerImageConfig> imageConfigs = DockerImageConfig.processParams(properties);

        final int threadPoolSize = Math.min(imageConfigs.size() * 2, Runtime.getRuntime().availableProcessors() + 1);
        clientConfig.getDockerClientConfig()
                .connectionPoolSize(threadPoolSize);

        return new DefaultDockerCloudClient(clientConfig, dockerClientFactory, imageConfigs,
                OfficialAgentImageResolver.forCurrentServer(DockerRegistryClientFactory.getDefault()), state,
                buildServer, agentTypeStorage);
    }

    @Nonnull
    @Override
    public String getCloudCode() {
        return DockerCloudUtils.CLOUD_CODE;
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Docker";
    }

    @Nonnull
    @Override
    public String getEditProfileUrl() {
        return editProfileUrl;
    }

    @Nonnull
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

    @Nonnull
    @Override
    public jetbrains.buildServer.serverSide.PropertiesProcessor getPropertiesProcessor() {
        return new DockerCloudPropertiesProcessor();
    }

    @Override
    public boolean canBeAgentOfType(@Nonnull AgentDescription description) {
        return DockerCloudUtils.getClientId(description) != null;
    }
}
