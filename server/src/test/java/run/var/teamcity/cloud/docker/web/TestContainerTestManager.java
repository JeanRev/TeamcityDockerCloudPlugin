package run.var.teamcity.cloud.docker.web;

import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestContainerTestManager extends ContainerTestManager {

    public enum TestStatus {

    }

    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;
    private ContainerTestListener listener;
    private Phase involvedPhase = null;

    private boolean disposed = false;

    @Override
    UUID createNewTestContainer(@NotNull DockerCloudClientConfig clientConfig, @NotNull DockerImageConfig imageConfig,
                                @NotNull ContainerTestListener listener) {
        this.clientConfig = clientConfig;
        this.imageConfig = imageConfig;
        this.listener = listener;

        this.involvedPhase = Phase.CREATE;
        return TestUtils.TEST_UUID;
    }

    @Override
    void startTestContainer(@NotNull UUID testUuid) {
        checkUuid(testUuid);
        this.involvedPhase = Phase.START;
    }

    @Override
    void dispose(@NotNull UUID testUuid) {
        checkUuid(testUuid);
        this.involvedPhase = null;
    }

    @Override
    void notifyInteraction(@NotNull UUID testUuid) {
        checkUuid(testUuid);
    }

    private void checkUuid(UUID uuid) {
        if (!TestUtils.TEST_UUID.equals(uuid)) {
            throw new IllegalArgumentException("Unknown UUID: " + uuid);
        }
    }

    @Override
    void dispose() {
        disposed = true;
    }

    public DockerCloudClientConfig getClientConfig() {
        return clientConfig;
    }

    public DockerImageConfig getImageConfig() {
        return imageConfig;
    }

    public ContainerTestListener getListener() {
        return listener;
    }

    public Phase getInvolvedPhase() {
        return involvedPhase;
    }

    public boolean isDisposed() {
        return disposed;
    }


    public class ContainerTest {

    }
}
