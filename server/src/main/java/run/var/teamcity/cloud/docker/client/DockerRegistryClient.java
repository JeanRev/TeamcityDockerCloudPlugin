package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.util.Node;

import java.io.Closeable;
import java.io.IOException;

public interface DockerRegistryClient extends Closeable {

    @NotNull
    Node anonymousLogin(@NotNull String scope);

    @NotNull
    Node listTags(@NotNull String loginToken, @NotNull String repo);

    @Override
    void close();
}
