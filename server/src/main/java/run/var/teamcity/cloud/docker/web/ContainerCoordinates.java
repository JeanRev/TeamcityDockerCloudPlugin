package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;

/**
 * Coordinate to access container. Composed of the required Docker client configuration and a container ID.
 */
public class ContainerCoordinates {

    private final String containerId;
    private final DockerClientConfig clientConfig;

    public ContainerCoordinates(@Nonnull String containerId, @Nonnull DockerClientConfig clientConfig) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        this.containerId = containerId;
        this.clientConfig = clientConfig;
    }

    @Nonnull
    public String getContainerId() {
        return containerId;
    }

    @Nonnull
    public DockerClientConfig getClientConfig() {
        return clientConfig;
    }
}
