package run.var.teamcity.cloud.docker;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DefaultDockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration of a {@link DockerCloudClient}.
 * <p>
 *     Beyond basic configuration parameters, a cloud client can be configured to use daemon threads. Daemon threads
 *     are, generally speaking, not a very good practice. They are however are necessity when running on the real
 *     server instance since our clean-up process can take some time to complete which in turn can slow the server
 *     shutdown time significantly. This should not be critical, except that Tomcat will complain if the JVM takes
 *     too long to shutdown, and then refuse to clean-up resources such as PID files properly.
 * </p>
 *
 * <p>Instances of this class are immutable.</p>
 */
public class DockerCloudClientConfig {

    private static final int DEFAULT_DOCKER_SYNC_RATE_SEC = 30;

    private final UUID uuid;
    private final DockerClientConfig dockerClientConfig;
    private final boolean usingDaemonThreads;
    private final int dockerSyncRateSec;

    /**
     * Creates a new configuration instance.
     *
     * @param uuid the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     * @param usingDaemonThreads {@code true} if the client must use daemon threads to manage containers
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public DockerCloudClientConfig(@NotNull UUID uuid, @NotNull DockerClientConfig dockerClientConfig,
                                   boolean usingDaemonThreads) {
        this(uuid, dockerClientConfig, usingDaemonThreads, DEFAULT_DOCKER_SYNC_RATE_SEC);
    }

    /**
     * Creates a new configuration instance.
     *
     * @param uuid the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     * @param usingDaemonThreads {@code true} if the client must use daemon threads to manage containers
     * @param dockerSyncRateSec the rate at which the client is synchronized with the Docker daemon, in seconds
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the Docker sync rate is below 2 seconds
     */
    public DockerCloudClientConfig(@NotNull UUID uuid, @NotNull DockerClientConfig dockerClientConfig,
                                   boolean usingDaemonThreads, int dockerSyncRateSec) {
        DockerCloudUtils.requireNonNull(uuid, "Client UUID cannot be null.");
        DockerCloudUtils.requireNonNull(dockerClientConfig, "Docker client configuration cannot be null.");
        if (dockerSyncRateSec < 2) {
            throw new IllegalArgumentException("Docker sync rate must be of at least 2 second.");
        }
        this.uuid = uuid;
        this.dockerClientConfig = dockerClientConfig;
        this.usingDaemonThreads = usingDaemonThreads;
        this.dockerSyncRateSec = dockerSyncRateSec;
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
    @NotNull
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

    public int getDockerSyncRateSec() {
        return dockerSyncRateSec;
    }

    /**
     * Load the configuration from the Teamcity properties map.
     *
     * @param properties the properties map
     *
     * @return the loaded configuration
     *
     * @throws DockerCloudClientConfigException if no valid configuration could be build from the properties map
     */
    @NotNull
    public static DockerCloudClientConfig processParams(@NotNull Map<String, String> properties,
                                                        @NotNull DockerClientFactory dockerClientFactory) {
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
                    DockerClientConfig dockerConfig = new DockerClientConfig(instanceURI).usingTls(usingTls);
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

        if (!invalidProperties.isEmpty()) {
            throw new DockerCloudClientConfigException(invalidProperties);
        }

        assert clientUuid != null && instanceURI != null;

        DockerClientConfig dockerClientConfig = new DockerClientConfig(instanceURI).usingTls(usingTls);

        return new DockerCloudClientConfig(clientUuid, dockerClientConfig, true);
    }

    /**
     * Generic validation method for a required field.
     *
     * @param msg the error message to be used if the condition is not met
     * @param key the property key
     * @param properties the properties map
     * @param invalidProperties the collection of invalid properties to be used
     *
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
