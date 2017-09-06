package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a container creation.
 */
public class NewContainerInfo {

    private final String id;
    private final List<String> warnings;

    /**
     * Creates a new container creation summary.
     *
     * @param id the created container id
     * @param warnings the encountered warnings if any
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public NewContainerInfo(@Nonnull String id, @Nonnull List<String> warnings) {
        DockerCloudUtils.requireNonNull(id, "Id cannot be null.");
        DockerCloudUtils.requireNonNull(warnings, "Warnings list cannot be null.");
        this.id = id;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /**
     * Gets the new container id.
     *
     * @return the new container id
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Gets the warnings list.
     *
     * @return the list of encountered warnings
     */
    @Nonnull
    public List<String> getWarnings() {
        return warnings;
    }
}
