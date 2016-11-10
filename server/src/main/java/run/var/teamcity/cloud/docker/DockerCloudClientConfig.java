package run.var.teamcity.cloud.docker;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DefaultDockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
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
 *
 * <p>Instances of this class are immutable.</p>
 */
public class DockerCloudClientConfig {

    private final UUID uuid;
    private final DockerClientConfig dockerClientConfig;

    /**
     * Creates a new configuration instance.
     *
     * @param uuid the cloud client UUID
     * @param dockerClientConfig the Docker client configuration
     */
    public DockerCloudClientConfig(@NotNull UUID uuid, @NotNull DockerClientConfig dockerClientConfig) {
        DockerCloudUtils.requireNonNull(uuid, "Client UUID cannot be null.");
        DockerCloudUtils.requireNonNull(dockerClientConfig, "Docker client configuration cannot be null.");
        this.uuid = uuid;
        this.dockerClientConfig = dockerClientConfig;
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
     * Load the configuration from the Teamcity properties map.
     *
     * @param properties the properties map
     *
     * @return the loaded configuration
     *
     * @throws DockerCloudClientConfigException if no valid configuration could be build from the properties map
     */
    @NotNull
    public static DockerCloudClientConfig processParams(@NotNull Map<String, String> properties) {
        DockerCloudUtils.requireNonNull(properties, "Properties map cannot be null.");

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

        boolean useTLS = Boolean.valueOf(properties.get(DockerCloudUtils.USE_TLS));

        String useDefaultInstanceStr = notEmpty("Select an instance type", DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, properties,
                invalidProperties);
        boolean useDefaultUnixSocket = Boolean.parseBoolean(useDefaultInstanceStr);
        URI instanceURI = null;
        if (useDefaultUnixSocket) {
            instanceURI = DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI;
        } else  {
            String instanceURLStr = notEmpty("Instance URL ist not set", DockerCloudUtils.INSTANCE_URI, properties, invalidProperties);
            if (instanceURLStr != null) {

                boolean valid = false;
                try {
                    instanceURI = new URI(instanceURLStr);
                    if (!instanceURI.isOpaque() && instanceURI.isAbsolute()) {
                        valid = true;
                        DefaultDockerClient.SupportedScheme scheme = null;
                        String schemeStr = instanceURI.getScheme().toUpperCase();
                        try {
                            scheme = DefaultDockerClient.SupportedScheme.valueOf(schemeStr);
                        } catch (IllegalArgumentException e) {
                            invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Not a supported scheme: " + schemeStr));
                        }

                        if (scheme == DefaultDockerClient.SupportedScheme.UNIX) {
                            if (instanceURI.getHost() != null || instanceURI.getPort() != -1 || instanceURI.getUserInfo() != null || instanceURI.getQuery() != null || instanceURI.getFragment() != null ) {
                                invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Only path can be provided for tcp scheme."));
                            }
                        } else if (scheme == DefaultDockerClient.SupportedScheme.TCP) {
                            if (instanceURI.getPath() != null || instanceURI.getUserInfo() != null || instanceURI.getQuery() != null || instanceURI.getFragment() != null ) {
                                invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Only host ip/name and port can be provided for tcp scheme."));
                            }
                        }
                    }
                } catch (URISyntaxException e) {
                    // Ignore.
                }
                if (!valid) {
                    invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Not a valid URI"));
                }
            }
        }

        if (!invalidProperties.isEmpty()) {
            throw new DockerCloudClientConfigException(invalidProperties);
        }

        assert clientUuid != null && instanceURI != null;

        DockerClientConfig dockerClientConfig = new DockerClientConfig(instanceURI).withTLS(useTLS);

        return new DockerCloudClientConfig(clientUuid, dockerClientConfig);
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
