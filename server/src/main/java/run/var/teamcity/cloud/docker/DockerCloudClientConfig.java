package run.var.teamcity.cloud.docker;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Configuration of a {@link DockerCloudClient}.
 *
 * <p>Instances of this class are immutable.</p>
 */
public class DockerCloudClientConfig {

    private final URI instanceURI;
    private final boolean useTLS;

    private DockerCloudClientConfig(URI instanceURI, boolean useTLS) {
        this.instanceURI = instanceURI;
        this.useTLS = useTLS;
    }

    /**
     * Gets the URI to the Docker daemon.
     *
     * @return the URI to the Docker daemon
     */
    @NotNull
    public URI getInstanceURI() {
        return instanceURI;
    }

    /**
     * Gets the TLS support flag value.
     *
     * @return {@code true} if Transport Layer Security is enabled
     */
    public boolean isUseTLS() {
        return useTLS;
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

        boolean useTLS = Boolean.valueOf(properties.get(DockerCloudUtils.USE_TLS));

        String useDefaultInstanceStr = notEmpty("Select an instance type", DockerCloudUtils.USE_DEFAULT_UNIX_SOCKET_PARAM, properties,
                invalidProperties);
        boolean useDefaultUnixSocket = Boolean.parseBoolean(useDefaultInstanceStr);
        URI instanceURL = null;
        if (useDefaultUnixSocket) {
            instanceURL = DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI;
        } else  {
            String instanceURLStr = notEmpty("Instance URL ist not set", DockerCloudUtils.INSTANCE_URI, properties, invalidProperties);
            if (instanceURLStr != null) {

                boolean valid = false;
                try {
                    instanceURL = new URI(instanceURLStr);
                    if (!instanceURL.isOpaque() && instanceURL.isAbsolute()) {
                        valid = true;
                        DockerClient.SupportedScheme scheme = null;
                        String schemeStr = instanceURL.getScheme().toUpperCase();
                        try {
                            scheme = DockerClient.SupportedScheme.valueOf(schemeStr);
                        } catch (IllegalArgumentException e) {
                            invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Not a supported scheme: " + schemeStr));
                        }

                        if (scheme == DockerClient.SupportedScheme.UNIX) {
                            if (instanceURL.getHost() != null || instanceURL.getPort() != -1 || instanceURL.getUserInfo() != null || instanceURL.getQuery() != null || instanceURL.getFragment() != null ) {
                                invalidProperties.add(new InvalidProperty(DockerCloudUtils.INSTANCE_URI, "Only path can be provided for tcp scheme."));
                            }
                        } else if (scheme == DockerClient.SupportedScheme.TCP) {
                            if (instanceURL.getPath() != null || instanceURL.getUserInfo() != null || instanceURL.getQuery() != null || instanceURL.getFragment() != null ) {
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

        return new DockerCloudClientConfig(instanceURL, useTLS);
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
