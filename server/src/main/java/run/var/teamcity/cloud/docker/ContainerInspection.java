package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;

/**
 * Outcome of a container inspection.
 * <p>
 * Contains only a subset of the fields that are effectively used.
 */
public class ContainerInspection {

    private final String name;

    /**
     * Creates a new container inspection summary.
     *
     * @param name the container name
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public ContainerInspection(@Nonnull String name) {
        DockerCloudUtils.requireNonNull(name, "Container name cannot be null.");
        this.name = name;
    }

    /**
     * Gets the container name.
     *
     * @return the container name
     */
    @Nonnull
    public String getName() {
        return name;
    }
}
