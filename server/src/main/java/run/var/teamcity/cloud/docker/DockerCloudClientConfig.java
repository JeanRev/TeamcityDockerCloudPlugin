package run.var.teamcity.cloud.docker;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Configuration of a {@link DockerCloudClient}. Could be instantiated directly, or from a cloud parameter map.
 * The wrapped Docker client config will use the currently supported API version.
 */
public class DockerCloudClientConfig {

    private static final long DEFAULT_DOCKER_SYNC_RATE_SEC = 30;
    private static final long DEFAULT_TASK_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final UUID uuid;
    private final DockerClientConfig dockerClientConfig;
    private final boolean usingDaemonThreads;
    private final long dockerSyncRateSec;
    private final long taskTimeoutMillis;
    private final URL serverURL;

    /**
     * Creates a new configuration instance.
     *
     * @param uuid               the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     * @param usingDaemonThreads {@code true} if the client must use daemon threads to manage containers
     * @param serverURL          the server URL to be configured on the agents
     * @throws NullPointerException if any argument is {@code null}
     */
    public DockerCloudClientConfig(@Nonnull UUID uuid, @Nonnull DockerClientConfig dockerClientConfig,
                                   boolean usingDaemonThreads, @Nullable URL serverURL) {
        this(uuid, dockerClientConfig, usingDaemonThreads, DEFAULT_DOCKER_SYNC_RATE_SEC, DEFAULT_TASK_TIMEOUT_MILLIS,
                serverURL);
    }

    /**
     * Creates a new configuration instance.
     *
     * @param uuid               the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     * @param usingDaemonThreads {@code true} if the client must use daemon threads to manage containers
     * @param dockerSyncRateSec  the rate at which the client is synchronized with the Docker daemon, in seconds
     * @param serverURL          the server URL to be configured on the agents
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the Docker sync rate is below 2 seconds
     */
    public DockerCloudClientConfig(@Nonnull UUID uuid, @Nonnull DockerClientConfig dockerClientConfig,
                                   boolean usingDaemonThreads, long dockerSyncRateSec, long taskTimeoutMillis,
                                   @Nullable URL serverURL) {
        DockerCloudUtils.requireNonNull(uuid, "Client UUID cannot be null.");
        DockerCloudUtils.requireNonNull(dockerClientConfig, "Docker client configuration cannot be null.");
        if (dockerSyncRateSec < 2) {
            throw new IllegalArgumentException("Docker sync rate must be of at least 2 seconds.");
        }
        if (taskTimeoutMillis < 10) {
            throw new IllegalArgumentException("Task timeout must be of at least 10 seconds.");
        }
        this.uuid = uuid;
        this.dockerClientConfig = dockerClientConfig;
        this.usingDaemonThreads = usingDaemonThreads;
        this.dockerSyncRateSec = dockerSyncRateSec;
        this.taskTimeoutMillis = taskTimeoutMillis;
        this.serverURL = serverURL;
    }

    /**
     * Gets the cloud client UUID.
     *
     * @return the cloud client UUID.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the Docker client configuration.
     *
     * @return the Docker client configuration
     */
    @Nonnull
    public DockerClientConfig getDockerClientConfig() {
        return dockerClientConfig;
    }

    /**
     * Checks if the cloud client must use daemon threads for managing containers.
     *
     * @return {@code true} if daemon threads must be used
     */
    public boolean isUsingDaemonThreads() {
        return usingDaemonThreads;
    }

    /**
     * Frequency in second at which synchronization with the Docker daemon is performed.
     *
     * @return the synchronisation rate
     */
    public long getDockerSyncRateSec() {
        return dockerSyncRateSec;
    }

    /**
     * The maximal duration in milliseconds of cloud related operations.
     *
     * @return the maximal duration
     */
    public long getTaskTimeoutMillis() {
        return taskTimeoutMillis;
    }

    /**
     * Gets the server URL for the agents to connect. May be null to use the default server URL.
     *
     * @return the server URL or {@code null}
     */
    @Nullable
    public URL getServerURL() {
        return serverURL;
    }

    /**
     * Load the configuration from the Teamcity properties map.
     *
     * @param properties the properties map
     * @return the loaded configuration
     * @throws DockerCloudClientConfigException if no valid configuration could be build from the properties map
     */
    @Nonnull
    public static DockerCloudClientConfig processParams(@Nonnull Map<String, String> properties,
                                                        @Nonnull DockerClientFactory dockerClientFactory) {
        DockerCloudUtils.requireNonNull(properties, "Properties map cannot be null.");
        DockerCloudUtils.requireNonNull(properties, "Docker client factory cannot be null.");

        List<InvalidProperty> invalidProperties = new ArrayList<>();

        String clientUuidStr = notEmpty("Cloud UUID ist not set", DockerCloudUtils.CLIENT_UUID, properties,
                invalidProperties);
        UUID clientUuid = null;
        if (clientUuidStr != null) {
            try {
                clientUuid = UUID.fromString(clientUuidStr);
            } catch (IllegalArgumentException e) {
                invalidProperties.add(new InvalidProperty(DockerCloudUtils.CLIENT_UUID, "Invalid client UUID."));
            }
        }

        boolean usingTls = Boolean.valueOf(properties.get(DockerCloudUtils.USE_TLS));

        String useDefaultInstanceStr = notEmpty("Select an instance type", DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, properties,
                invalidProperties);
        boolean useDefaultUnixSocket = Boolean.parseBoolean(useDefaultInstanceStr);
        URI instanceURI = null;
        if (useDefaultUnixSocket) {
            instanceURI = DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI;
        } else if (useDefaultInstanceStr != null) {
            String instanceURLStr = notEmpty("Instance URL ist not set", DockerCloudUtils.INSTANCE_URI, properties, invalidProperties);
            if (instanceURLStr != null) {
                try {
                    instanceURI = new URI(instanceURLStr);
                    DockerClientConfig dockerConfig = new DockerClientConfig(instanceURI,
                            DockerCloudUtils.DOCKER_API_TARGET_VERSION).usingTls(usingTls);
                    try {
                        dockerClientFactory.createClient(dockerConfig);
                    } catch (IllegalArgumentException e) {
                        invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, e.getMessage()));
                    }
                } catch (URISyntaxException e) {
                    invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Not a valid URI"));
                }
            }
        }

        URL serverURL = null;

        String serverURLStr = properties.get(DockerCloudUtils.SERVER_URL_PARAM);

        if (!StringUtil.isEmpty(serverURLStr)) {
            try {
                serverURL = new URL(serverURLStr);
            } catch (MalformedURLException e) {
                invalidProperties.add(new InvalidProperty(DockerCloudUtils.SERVER_URL_PARAM, "Not a valid URL"));
            }
        }

        if (!invalidProperties.isEmpty()) {
            throw new DockerCloudClientConfigException(invalidProperties);
        }

        assert clientUuid != null && instanceURI != null;

        DockerClientConfig dockerClientConfig =
                new DockerClientConfig(instanceURI, DockerCloudUtils.DOCKER_API_TARGET_VERSION).usingTls(usingTls);

        return new DockerCloudClientConfig(clientUuid, dockerClientConfig, true, serverURL);
    }

    /**
     * Generic validation method for a required field.
     *
     * @param msg               the error message to be used if the condition is not met
     * @param key               the property key
     * @param properties        the properties map
     * @param invalidProperties the collection of invalid properties to be used
     * @return the property value or {@code null} if missing
     */
    @Nullable
    private static String notEmpty(String msg, String key, Map<String, String> properties, Collection<InvalidProperty> invalidProperties) {
        assert msg != null && key != null && properties != null && invalidProperties != null;
        String value = properties.get(key);
        if (StringUtil.isEmptyOrSpaces(properties.get(key))) {
            invalidProperties.add(new InvalidProperty(key, msg));
            return null;
        }
        return value.trim();
    }
}
