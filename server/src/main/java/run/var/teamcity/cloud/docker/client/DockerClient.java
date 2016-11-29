package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.NodeStream;

import java.io.Closeable;

/**
 * A Docker client.
 */
public interface DockerClient extends Closeable {

    @NotNull
    Node getVersion();

    @NotNull
    Node createContainer(@NotNull Node containerSpec, @Nullable String name);

    void startContainer(@NotNull String containerId);

    void restartContainer(@NotNull String containerId);

    @NotNull
    Node inspectContainer(@NotNull String containerId);

    @NotNull
    NodeStream createImage(@NotNull String from, @Nullable String tag);

    void stopContainer(@NotNull String containerId, long timeoutSec);

    void removeContainer(@NotNull String containerId, boolean removeVolumes, boolean force);

    @NotNull
    Node listContainersWithLabel(@NotNull String key, @NotNull String value);

    @Override
    void close();
}
