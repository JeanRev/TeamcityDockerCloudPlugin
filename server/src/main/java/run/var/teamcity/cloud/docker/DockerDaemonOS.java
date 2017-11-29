package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Docker daemon operating system.
 */
public enum DockerDaemonOS {

    /**
     * Linux.
     */
    LINUX("linux"),
    /**
     * Windows.
     */
    WINDOWS("windows");

    private final String attribute;

    DockerDaemonOS(String attribute) {
        assert attribute != null;
        this.attribute = attribute;
    }

    @Nonnull
    public String getAttribute() {
        return attribute;
    }

    /**
     * Translate an OS string representation as delivered from the Docker API.
     *
     * @param attribute the string attribute
     *
     * @throws NullPointerException if {@code attribute} is {@code null}
     *
     * @return the corresponding enum member or an empty result if the operating system cannot be determined
     */
    @Nonnull
    public static Optional<DockerDaemonOS> fromString(@Nonnull String attribute) {
        DockerCloudUtils.requireNonNull(attribute, "Attribute cannot be null.");
        for (DockerDaemonOS os : values()) {
            if (os.attribute.equals(attribute)) {
                return Optional.of(os);
            }
        }
        return Optional.empty();
    }
}
