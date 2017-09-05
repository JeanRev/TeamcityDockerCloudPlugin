package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface DockerClientFacade extends AutoCloseable {

    PullStatusListener NOOP_PULL_LISTENER = (layer, current, total) -> {};

    @Nonnull
    NewContainerInfo createAgentContainer(@Nonnull Node containerSpec, @Nonnull String image, @Nonnull Map<String,
            String> labels, @Nonnull Map<String, String> env);

    void startAgentContainer(@Nonnull String containerId);

    void restartAgentContainer(@Nonnull String containerId);

    @Nonnull
    ContainerInspection inspectAgentContainer(@Nonnull String containerId);

    @Nonnull
    List<ContainerInfo> listActiveAgentContainers(@Nonnull String labelFilter, @Nonnull String valueFilter);

    boolean terminateAgentContainer(@Nonnull String containerId, Duration timeout, boolean removeContainer);


    default void pull(String image, DockerRegistryCredentials credentials) {
        pull(image, credentials, NOOP_PULL_LISTENER);
    }

    void pull(String image, DockerRegistryCredentials credentials, PullStatusListener
            statusListener);

    CharSequence getLogs(String containerId);

    @Nonnull
    StreamHandler streamLogs(String containerId);

    @Override
    void close();
}
