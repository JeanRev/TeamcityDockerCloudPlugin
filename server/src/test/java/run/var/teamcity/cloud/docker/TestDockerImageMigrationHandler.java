package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;

public class TestDockerImageMigrationHandler implements DockerImageMigrationHandler {

    private Node migratedData = null;
    private Node imageData;

    @Nonnull
    @Override
    public Node performMigration(@Nonnull Node imageData) {
        this.imageData = imageData;

        return migratedData != null ? migratedData : imageData;
    }

    public void setMigratedData(Node migratedData) {
        this.migratedData = migratedData;
    }

    public Node getImageData() {
        return imageData;
    }
}
