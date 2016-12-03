package run.var.teamcity.cloud.docker;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

/**
 * Configuration of a {@link DefaultDockerCloudClient}.
 *
 * <p>Instances of this class are immutable.</p>
 */
public class DockerCloudClientConfig {

    private static final int DEFAULT_DOCKER_SYNC_RATE_SEC = 30;

    private final UUID uuid;
    private final DockerClientConfig dockerClientConfig;
    private final boolean usingDaemonThreads;
    private final int dockerSyncRateSec;
    private final URL serverURL;

    /**
     * Creates a new configuration instance.
     *
     * @param uuid the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     * @param usingDaemonThreads {@code true} if the client must use daemon threads to manage containers
     * @param serverURL the server URL to be configured on the agents
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public DockerCloudClientConfig(@NotNull UUID uuid, @NotNull DockerClientConfig dockerClientConfig,
                                   boolean usingDaemonThreads, @Nullable URL serverURL) {
        this(uuid, dockerClientConfig, usingDaemonThreads, DEFAULT_DOCKER_SYNC_RATE_SEC, serverURL);
    }

    /**
     * Creates a new configuration instance.
     *
     * @param uuid the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     * @param usingDaemonThreads {@code true} if the client must use daemon threads to manage containers
     * @param dockerSyncRateSec the rate at which the client is synchronized with the Docker daemon, in seconds
     * @param serverURL the server URL to be configured on the agents
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the Docker sync rate is below 2 seconds
     */
    public DockerCloudClientConfig(@NotNull UUID uuid, @NotNull DockerClientConfig dockerClientConfig,
                                   boolean usingDaemonThreads, int dockerSyncRateSec, @Nullable URL serverURL) {
        DockerCloudUtils.requireNonNull(uuid, "Client UUID cannot be null.");
        DockerCloudUtils.requireNonNull(dockerClientConfig, "Docker client configuration cannot be null.");
        if (dockerSyncRateSec < 2) {
            throw new IllegalArgumentException("Docker sync rate must be of at least 2 second.");
        }
        this.uuid = uuid;
        this.dockerClientConfig = dockerClientConfig;
        this.usingDaemonThreads = usingDaemonThreads;
        this.dockerSyncRateSec = dockerSyncRateSec;
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

        DockerClientConfig dockerClientConfig = new DockerClientConfig(instanceURI).usingTls(usingTls);

        return new DockerCloudClientConfig(clientUuid, dockerClientConfig, true, serverURL);
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
