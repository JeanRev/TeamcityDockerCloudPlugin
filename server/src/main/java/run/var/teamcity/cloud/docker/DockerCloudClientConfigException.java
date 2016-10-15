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
class DockerCloudClientConfigException extends RuntimeException {

    private final List<InvalidProperty> invalidProperties;

    /**
     * Creates a new exception instance with the specified list of invalid properties.
     *
     * @param invalidProperties the properties list
     *
     * @throws NullPointerException if {@code invalidProperties} is {@code null}
     */
    DockerCloudClientConfigException(@NotNull List<InvalidProperty> invalidProperties) {
        DockerCloudUtils.requireNonNull(invalidProperties, "List of invalid properties must be non-null.");
        this.invalidProperties = Collections.unmodifiableList(new ArrayList<>(invalidProperties));
    }

    @NotNull
    List<InvalidProperty> getInvalidProperties() {
        return invalidProperties;
    }
}
