package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;

/**
 * A Docker client.
 */
public interface DockerClient extends Closeable {

    @Nonnull
    Node getVersion();

    @Nonnull
    Node createContainer(@Nonnull Node containerSpec, @Nullable String name);

    void startContainer(@Nonnull String containerId);

    void restartContainer(@Nonnull String containerId);

    @Nonnull
    Node inspectContainer(@Nonnull String containerId);

    @Nonnull
    NodeStream createImage(@Nonnull String from, @Nullable String tag);

    void stopContainer(@Nonnull String containerId, long timeoutSec);

    void removeContainer(@Nonnull String containerId, boolean removeVolumes, boolean force);

    @Nonnull
    Node listContainersWithLabel(@Nonnull String key, @Nonnull String value);

    @Override
    void close();
}
