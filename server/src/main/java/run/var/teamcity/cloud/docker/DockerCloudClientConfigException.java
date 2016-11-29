package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown if the no valid {@link DockerCloudClientConfig} can be build.
 */
public class DockerCloudClientConfigException extends RuntimeException {

    private final List<InvalidProperty> invalidProperties;

    /**
     * Creates a new exception instance with the specified list of invalid properties.
     *
     * @param invalidProperties the properties list
     *
     * @throws NullPointerException if {@code invalidProperties} is {@code null}
     * @throws IllegalArgumentException if {@code invalidProperties} is empty
     */
    public DockerCloudClientConfigException(@NotNull List<InvalidProperty> invalidProperties) {
        super(invalidProperties != null ? invalidProperties.toString() : null);
        DockerCloudUtils.requireNonNull(invalidProperties, "List of invalid properties must be non-null.");
        if (invalidProperties.isEmpty()) {
            throw new IllegalArgumentException("Invalid properties list cannot be empty.");
        }
        this.invalidProperties = Collections.unmodifiableList(new ArrayList<>(invalidProperties));
    }

    /**
     * Gets the list of invalid properties.
     *
     * @return the list of invalid properties, guaranteed to be non null and non empty.
     */
    @NotNull
    public List<InvalidProperty> getInvalidProperties() {
        return invalidProperties;
    }
}
