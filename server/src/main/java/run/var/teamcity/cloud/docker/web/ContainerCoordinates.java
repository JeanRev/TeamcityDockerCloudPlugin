package run.var.teamcity.cloud.docker.web;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

/**
 * Coordinate to access container. Composed of the required Docker client configuration and a container ID.
 */
public class ContainerCoordinates {

    private final String containerId;
    private final DockerClientConfig clientConfig;

    public ContainerCoordinates(@NotNull String containerId, @NotNull DockerClientConfig clientConfig) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        this.containerId = containerId;
        this.clientConfig = clientConfig;
    }

    @NotNull
    public String getContainerId() {
        return containerId;
    }

    @NotNull
    public DockerClientConfig getClientConfig() {
        return clientConfig;
    }
}
