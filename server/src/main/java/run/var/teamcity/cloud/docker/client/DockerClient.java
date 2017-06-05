package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.Map;
import java.util.Set;

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
    Node inspectImage(@Nonnull String image);

    @Nonnull
    NodeStream createImage(@Nonnull String from, @Nullable String tag, @Nonnull DockerRegistryCredentials credentials);

    /**
     * Stream the container logs.
     *
     * @param containerId the container id
     * @param lineCount the number of line context
     * @param stdioTypes the types of stream to be fetched
     * @param follow {@code true} if the logs must be streamed continuously
     *
     * @return a stream handler to consume the logs
     */
    @Nonnull
    StreamHandler streamLogs(@Nonnull String containerId, int lineCount, Set<StdioType> stdioTypes, boolean
            follow);

    void stopContainer(@Nonnull String containerId, long timeoutSec);

    void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force);

    /**
     * List the containers filtered using the given sets of labels. For a container to be included in the list, all of
     * the labels from the filter map will need to be set with the corresponding value.
     *
     * @param labelFilters the label filter map
     *
     * @return the list of containers
     *
     * @throws NullPointerException if {@code labelFilters}, or any of its keys or values, are {@code null}
     */
    @Nonnull
    Node listContainersWithLabel(@Nonnull Map<String, String> labelFilters);

    @Override
    void close();
}
