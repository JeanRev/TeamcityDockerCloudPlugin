package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;

/**
 * A Docker client.
 */
public interface DockerClient extends Closeable {

    /**
     * Flag indicating that the default timeout must be used when stopping containers. This will be either the timeout
     * specified in the container configuration (for Docker <= 12.x) if any, or the Docker daemon default timeout
     * (10 seconds).
     */
    long DEFAULT_TIMEOUT = -1;

    // Api version: must be settable after instantiation to handle with API version negotiation.

    @Nonnull
    DockerAPIVersion getApiVersion();

    void setApiVersion(@Nonnull DockerAPIVersion apiVersion);

    @Nonnull
    Node getVersion();

    @Nonnull
    Node createContainer(@Nonnull Node containerSpec, @Nullable String name);

    void startContainer(@Nonnull String containerId);

    void restartContainer(@Nonnull String containerId);

    @Nonnull
    Node inspectContainer(@Nonnull String containerId);

    @Nonnull
    NodeStream createImage(@Nonnull String from, @Nullable String tag, @Nonnull DockerClientCredentials credentials);

    void stopContainer(@Nonnull String containerId, long timeoutSec);

    void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force);

    @Nonnull
    Node listContainersWithLabel(@Nonnull String key, @Nonnull String value);

    @Override
    void close();
}
