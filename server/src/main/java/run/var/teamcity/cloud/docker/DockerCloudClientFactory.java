package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudClientFactory;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.OfficialAgentImageResolver;
import run.var.teamcity.cloud.docker.util.Resources;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for a Docker {@link CloudClientFactory}.
 */
public class DockerCloudClientFactory implements CloudClientFactory {

    private final DockerCloudSupport cloudSupport;
    private final DockerCloudSupportRegistry cloudSupportRegistry;
    private final String editProfileUrl;
    private final SBuildServer buildServer;

    public DockerCloudClientFactory(
            @Nonnull DockerCloudSupport cloudSupport,
            @Nonnull DockerCloudSupportRegistry cloudSupportRegistry,
            @Nonnull final SBuildServer buildServer,
            @Nonnull final PluginDescriptor pluginDescriptor) {
        this.cloudSupport = cloudSupport;
        this.cloudSupportRegistry = cloudSupportRegistry;
        this.editProfileUrl = pluginDescriptor.getPluginResourcesPath(cloudSupport.resources().text("cloud.settingsPath"));
        this.buildServer = buildServer;
    }

    @Nonnull
    @Override
    public final CloudClientEx createNewClient(@Nonnull CloudState state, @Nonnull CloudClientParameters params) {
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

        DockerCloudClientConfig clientConfig = DockerCloudClientConfig.processParams(properties, cloudSupportRegistry);

        if (!clientConfig.getCloudSupport().equals(cloudSupport)) {
            throw new IllegalArgumentException("Cloud type mismatch, expected: " + cloudSupport + " got: " +
                    clientConfig.getCloudSupport());
        }

        List<DockerImageConfig> imageConfigs = DockerImageConfig.processParams(cloudSupport.createImageConfigParser(),
                properties);

        final int threadPoolSize = Math.min(imageConfigs.size() * 2, Runtime.getRuntime().availableProcessors() + 1);
        clientConfig.getDockerClientConfig()
                .connectionPoolSize(threadPoolSize);

        return new DefaultDockerCloudClient(clientConfig, imageConfigs,
                OfficialAgentImageResolver.forCurrentServer(DockerRegistryClientFactory.getDefault()), state,
                buildServer);
    }

    @Nonnull
    @Override
    public final Map<String, String> getInitialParameterValues() {
        HashMap<String, String> params = new HashMap<>();
        // The cloud client UUID is generated here for new cloud profiles. It will be then persisted in the profile
        // plugin configuration.
        params.put(DockerCloudUtils.CLOUD_TYPE_PARAM, cloudSupport.code());
        params.put(DockerCloudUtils.CLIENT_UUID_PARAM, UUID.randomUUID().toString());
        params.put(DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM,
                String.valueOf(DockerCloudUtils.isDefaultDockerSocketAvailable()));
        params.put(DockerCloudUtils.USE_DEFAULT_WIN_NAMED_PIPE_PARAM,
                String.valueOf(DockerCloudUtils.isDefaultDockerNamedPipeAvailable()));
        return params;
    }

    @NotNull
    @Override
    public String getCloudCode() {
        return cloudSupport.code();
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return cloudSupport.resources().text("cloud.title");
    }

    @Nonnull
    @Override
    public final String getEditProfileUrl() {
        return editProfileUrl;
    }

    @Nonnull
    @Override
    public final jetbrains.buildServer.serverSide.PropertiesProcessor getPropertiesProcessor() {
        return new DockerCloudPropertiesProcessor(cloudSupportRegistry, cloudSupport.createImageConfigParser());
    }

    @Override
    public final boolean canBeAgentOfType(@Nonnull AgentDescription description) {
        return DockerCloudUtils.getClientId(description) != null;
    }
}
