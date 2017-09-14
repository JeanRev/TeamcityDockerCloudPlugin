package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.web.DockerCloudSettingsController;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Docker {@link CloudClientFactory}.
 */
public class DockerCloudClientFactory implements CloudClientFactory {

    private final String editProfileUrl;
    private final SBuildServer buildServer;
    private final DockerClientFacadeFactory clientFacadeFactory;

    public DockerCloudClientFactory(@Nonnull final SBuildServer buildServer,
                                    @Nonnull final CloudRegistrar cloudRegistrar,
                                    @Nonnull final PluginDescriptor pluginDescriptor) {
        this(buildServer, cloudRegistrar, pluginDescriptor, DockerClientFacadeFactory.getDefault());
    }

    DockerCloudClientFactory(@Nonnull final SBuildServer buildServer,
                             @Nonnull final CloudRegistrar cloudRegistrar,
                             @Nonnull final PluginDescriptor pluginDescriptor,
                             @Nonnull final DockerClientFacadeFactory clientFacadeFactory) {
        this.editProfileUrl = pluginDescriptor.getPluginResourcesPath(DockerCloudSettingsController.EDIT_PATH);
        cloudRegistrar.registerCloudFactory(this);
        this.buildServer = buildServer;
        this.clientFacadeFactory = clientFacadeFactory;
    }


    @Nonnull
    @Override
    public CloudClientEx createNewClient(@Nonnull CloudState state, @Nonnull CloudClientParameters params) {
        Collection<String> paramNames = params.listParameterNames();
        Map<String, String> properties = new HashMap<>(paramNames.size());
        for (String paramName : paramNames) {
            properties.put(paramName, params.getParameter(paramName));
        }

        // Issue #12: their might be inconsistencies between the cloud client parameters map, and the cloud image
        // parameter list that may have been "externally" (ie. not through the plugin settings) updated. To work
        // around this, the image parameters are serialized back into the properties map.
        properties.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                CloudImageParameters.collectionToJson(params.getCloudImages()));

        DockerCloudClientConfig clientConfig = DockerCloudClientConfig.processParams(properties, clientFacadeFactory);
        List<DockerImageConfig> imageConfigs = DockerImageConfig.processParams(properties);

        final int threadPoolSize = Math.min(imageConfigs.size() * 2, Runtime.getRuntime().availableProcessors() + 1);
        clientConfig.getDockerClientConfig()
                .connectionPoolSize(threadPoolSize);

        return new DefaultDockerCloudClient(clientConfig, clientFacadeFactory, imageConfigs,
                                            OfficialAgentImageResolver
                                                    .forCurrentServer(DockerRegistryClientFactory.getDefault()), state,
                                            buildServer);
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
        params.put(DockerCloudUtils.USE_DEFAULT_WIN_NAMED_PIPE_PARAM,
                String.valueOf(DockerCloudUtils.isDefaultDockerNamedPipeAvailable()));
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
