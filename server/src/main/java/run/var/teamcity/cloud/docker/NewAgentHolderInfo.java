package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Information about a newly created agent holder.
 */
public class NewAgentHolderInfo {

    private final String id;
    private final String name;
    private final String resolvedImage;
    private final List<String> warnings;

    /**
     * Creates a new agent holder creation summary.
     *
     * @param id the agent holder id
     * @param name the agent holder name name
     * @param resolvedImage the resolved image name
     * @param warnings the encountered warnings if any
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public NewAgentHolderInfo(@Nonnull String id, @Nonnull String name, @Nonnull String resolvedImage,
            @Nonnull List<String> warnings) {

        this.id = DockerCloudUtils.requireNonNull(id, "Id cannot be null.");
        this.name = DockerCloudUtils.requireNonNull(name, "Name cannot be null.");
        this.resolvedImage = DockerCloudUtils.requireNonNull(resolvedImage, "Resolved image name cannot be null.");
        DockerCloudUtils.requireNonNull(warnings, "Warnings list cannot be null.");
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /**
     * Gets the new agent holder id.
     *
     * @return the new agent holder id
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Gets the new agent holder name.
     *
     * @return the new agent holder name
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Gets the resolved image name. This is the image name as returned upon inspection of the agent holder, and may
     * slightly differs from the image name available if the agent holder specification.
     *
     * @return the resolved image name
     */
    @Nonnull
    public String getResolvedImage() {
        return resolvedImage;
    }

    /**
     * Gets the warnings list related to the agent holder creation..
     *
     * @return the list of encountered warnings
     */
    @Nonnull
    public List<String> getWarnings() {
        return warnings;
    }
}
