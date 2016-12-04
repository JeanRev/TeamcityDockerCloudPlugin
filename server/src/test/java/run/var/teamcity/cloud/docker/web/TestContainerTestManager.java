package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TestContainerTestManager extends ContainerTestManager {

    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;
    private ContainerTestListener listener;
    private Phase involvedPhase = null;

    private boolean disposed = false;

    @Override
    UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig, @Nonnull DockerImageConfig imageConfig,
                                @Nonnull ContainerTestListener listener) {
        this.clientConfig = clientConfig;
        this.imageConfig = imageConfig;
        this.listener = listener;

        this.involvedPhase = Phase.CREATE;
        return TestUtils.TEST_UUID;
    }

    @Override
    void startTestContainer(@Nonnull UUID testUuid) {
        checkUuid(testUuid);
        this.involvedPhase = Phase.START;
    }

    @Override
    public String getLogs(@Nonnull UUID testUuid) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    void dispose(@Nonnull UUID testUuid) {
        checkUuid(testUuid);
        this.involvedPhase = null;
    }

    @Override
    void notifyInteraction(@Nonnull UUID testUuid) {
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
