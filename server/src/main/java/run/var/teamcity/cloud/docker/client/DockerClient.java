package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.math.BigInteger;
import java.time.Duration;
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
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(-1);

    // Api version: must be settable after instantiation to handle with API version negotiation.

    /**
     * Gets the target API version when communicating with the daemon.
     *
     * @return target API version
     */
    @Nonnull
    DockerAPIVersion getApiVersion();

    /**
     * Sets the target API version when communicating with the daemon.
     *
     * @param apiVersion the target API version
     *
     * @throws NullPointerException if {@code apiVersion} is {@code null}
     */
    void setApiVersion(@Nonnull DockerAPIVersion apiVersion);

    /**
     * Queries the daemon version.
     *
     * @return the daemon version node
     *
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node getVersion();

    /**
     * Queries system information about the daemon.
     *
     * @return the daemon system information
     */
    @Nonnull
    Node getInfo();

    /**
     * Creates a new container with the given container specification.
     *
     * @param containerSpec the container specification
     * @param name the container name (may be {@code null})
     *
     * @return the container creation outcome
     *
     * @throws NullPointerException if {@code containerSpec} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node createContainer(@Nonnull Node containerSpec, @Nullable String name);

    /**
     * Starts the container with the given id.
     *
     * @param containerId the container id
     *
     * @throws NullPointerException if {@code containerId} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    void startContainer(@Nonnull String containerId);

    /**
     * Creates a service with the given service specification.
     *
     * @param serviceSpec the service specification
     *
     * @return the service creation outcome
     *
     * @throws NullPointerException if {@code serviceSpec} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node createService(@Nonnull Node serviceSpec);

    /**
     * Inspects a service.
     *
     * @param service the service name or id
     *
     * @return the service inspection outcome
     *
     * @throws NullPointerException if {@code service} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    Node inspectService(@Nonnull String service);

    /**
     * Updates a service with the given service specification.
     *
     * @param service the service to be updated
     * @param serviceSpec the updated service specification
     * @param version the service specification version number
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    void updateService(@Nonnull String service, @Nonnull Node serviceSpec, @Nonnull BigInteger version);

    /**
     * Restarts the container with the given id.
     *
     * @param containerId the container id
     *
     * @throws NullPointerException if {@code containerId} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    void restartContainer(@Nonnull String containerId);

    /**
     * Inspects the container with the given name or id.
     *
     * @param container the container name or id
     *
     * @return the inspection node
     *
     * @throws NullPointerException if {@code container} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node inspectContainer(@Nonnull String container);

    /**
     * Inspects the image with the given name or id.
     *
     * @param image the image name or id
     *
     * @return the inspection node
     *
     * @throws NullPointerException if {@code container} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node inspectImage(@Nonnull String image);

    /**
     * Creates an image in the local registry.
     *
     * @param from source image name
     * @param tag tag to fetch (may be {@code null})
     * @param credentials the credentials to access registry
     *
     * @return the stream of nodes to track the creation progress
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    NodeStream createImage(@Nonnull String from, @Nullable String tag, @Nonnull DockerRegistryCredentials credentials);

    /**
     * Streams the container logs.
     *
     * @param containerId the container id
     * @param lineCount the number of line of context
     * @param stdioTypes the types of stream to be fetched
     * @param follow if the logs must be fetched continuously
     * @param demuxStdio {@code true} if the logs content must be demultiplexed using Stdio frames
     *
     * @return a stream handler to consume the logs
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    StreamHandler streamLogs(@Nonnull String containerId, int lineCount, @Nonnull Set<StdioType> stdioTypes,
                             boolean follow, boolean demuxStdio);

    /**
     * Streams the service logs.
     *
     * @param containerId the container id
     * @param lineCount the number of line context
     * @param stdioTypes the types of stream to be fetched
     * @param follow {@code true} if the logs must be streamed continuously
     *
     * @return a stream handler to consume the logs
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    StreamHandler streamServiceLogs(@Nonnull String containerId, int lineCount, @Nonnull Set<StdioType> stdioTypes,
                                  boolean follow, boolean demuxStream);

    /**
     * Stops the container with the given name or id and stop timeout. Use {@link #DEFAULT_TIMEOUT} as timeout value
     * to let the daemon use the default timeout.
     *
     * @param container the container
     * @param timeout the stop timeout
     *
     * @throws NullPointerException if {@code container} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    void stopContainer(@Nonnull String container, Duration timeout);

    /**
     * Removes the container with the given name or id.
     *
     * @param container the container
     * @param removeVolumes flag to remove the container volumes (default: {@code false})
     * @param force force the container removal also when currently running
     *
     * @throws NullPointerException if {@code container} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    void removeContainer(@Nonnull String container, boolean removeVolumes, boolean force);

    /**
     * Removes the service with the given name or id.
     *
     * @param service the service
     *
     * @throws NullPointerException if {@code service} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    void removeService(@Nonnull String service);

    /**
     * Lists the containers filtered using the given sets of labels. For a container to be included in the list, all of
     * the labels from the filter map will need to be set with the corresponding value.
     *
     * @param labelFilters the label filter map
     *
     * @return the list of containers
     *
     * @throws NullPointerException if {@code labelFilters}, or any of its keys or values, are {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node listContainersWithLabel(@Nonnull Map<String, String> labelFilters);

    /**
     * Lists the services filtered using the given sets of labels. For a service to be included in the list, all of
     * the labels from the filter map will need to be set with the corresponding value.
     *
     * @param labelFilters the label filter map
     *
     * @return the list of services
     *
     * @throws NullPointerException if {@code labelFilters}, or any of its keys or values, are {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node listServicesWithLabel(@Nonnull Map<String, String> labelFilters);

    /**
     * Closes this client. Has no effect if the client is already closed.
     */
    @Override
    void close();

    /**
     * Lists the tasks for the given service.
     *
     * @param serviceId the service
     *
     * @return the list of tasks
     *
     * @throws NullPointerException if {@code serviceId} is {@code null}
     * @throws DockerClientException if an error occurred while communicating with the daemon
     */
    @Nonnull
    Node listTasks(@Nonnull String serviceId);
}
