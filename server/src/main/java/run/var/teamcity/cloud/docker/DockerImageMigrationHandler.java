package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;

/**
 * Migrates an cloud image configuration node to its latest version.
 */
public interface DockerImageMigrationHandler {

    /**
     * Perform the migration.
     *
     * @param imageData the image configuration node
     *
     * @return the upgraded image configuration node
     *
     * @throws NullPointerException if {@code imageData} is {@code null}
     * @throws IllegalArgumentException if the image node cannot be upgraded
     */
    @Nonnull
    Node performMigration(@Nonnull Node imageData);
}
