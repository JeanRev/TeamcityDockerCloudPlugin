package run.var.teamcity.cloud.docker.client;

import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.io.Closeable;

public interface DockerRegistryClient extends Closeable {

    @Nonnull
    Node anonymousLogin(@Nonnull String scope);

    @Nonnull
    Node listTags(@Nonnull String loginToken, @Nonnull String repo);

    @Override
    void close();
}
