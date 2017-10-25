package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Resources;

import javax.annotation.Nonnull;
import java.util.ResourceBundle;

/**
 * Productive set of {@link DockerCloudSupport}s.
 */
public enum DefaultDockerCloudSupport implements DockerCloudSupport {
    /**
     * Docker cloud with internally managed orchestration.
     */
    VANILLA("VRDC") {
        @Nonnull
        @Override
        public DockerClientFacade createClientFacade(@Nonnull DockerClientConfig dockerClientConfig) {
            DockerCloudUtils.requireNonNull(dockerClientConfig, "Docker client configuration cannot be null.");
            return new DefaultDockerClientFacade(createClient(dockerClientConfig));
        }

        @Nonnull
        @Override
        public DockerImageConfigParser createImageConfigParser() {
            return new DefaultDockerImageConfigParser();
        }

        @Nonnull
        @Override
        public Resources resources() {
            return VANILLA_RESOURCES;
        }
    },
    /**
     * Docker cloud with Swarm-managed orchestration.
     */
    SWARM("VRDS") {
        @Nonnull
        @Override
        public DockerClientFacade createClientFacade(@Nonnull DockerClientConfig dockerClientConfig) {
            DockerCloudUtils.requireNonNull(dockerClientConfig, "Docker client configuration cannot be null.");
            return new SwarmDockerClientFacade(createClient(dockerClientConfig));
        }

        @Nonnull
        @Override
        public DockerImageConfigParser createImageConfigParser() {
            return new SwarmDockerImageConfigParser();
        }

        @Nonnull
        @Override
        public Resources resources() {
            return SWARM_RESOURCES;
        }
    };

    private static final Resources VANILLA_RESOURCES;
    private static final Resources SWARM_RESOURCES;

    static {
        ResourceBundle defaultBundle = ResourceBundle.getBundle("run.var.teamcity.cloud.docker.resources");
        ResourceBundle swarmBundle = ResourceBundle.getBundle("run.var.teamcity.cloud.docker.resourcesSwarm");

        VANILLA_RESOURCES = new Resources(defaultBundle);
        SWARM_RESOURCES = new Resources(swarmBundle, defaultBundle);
    }

    private final String code;

    DefaultDockerCloudSupport(String code) {
        assert code != null;
        assert code.length() == 4: "Per spec, the cloud code must be 4 chars long.";
        this.code = code;
    }

    @Nonnull
    @Override
    public String code() {
        return code;
    }

    private static DockerClient createClient(DockerClientConfig clientConfig) {
        return DockerClientFactory.getDefault().createClientWithAPINegotiation(clientConfig);
    }
}
