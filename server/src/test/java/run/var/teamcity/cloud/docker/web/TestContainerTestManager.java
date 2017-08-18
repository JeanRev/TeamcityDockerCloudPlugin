package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public class TestContainerTestManager implements ContainerTestManager {

    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;
    private ContainerTestListener listener;
    private Phase involvedPhase = null;

    @Nonnull
    @Override
    public UUID createNewTestContainer(@Nonnull DockerCloudClientConfig clientConfig, @Nonnull DockerImageConfig
            imageConfig) {
        this.clientConfig = clientConfig;
        this.imageConfig = imageConfig;
        this.involvedPhase = Phase.CREATE;
        return TestUtils.TEST_UUID;
    }

    @Override
    public void startTestContainer(@Nonnull UUID testUuid) {
        checkUuid(testUuid);
        this.involvedPhase = Phase.START;
    }

    @Nonnull
    @Override
    public String getLogs(@Nonnull UUID testUuid) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void dispose(@Nonnull UUID testUuid) {
        checkUuid(testUuid);
        this.involvedPhase = null;
    }

    @Override
    public void setListener(@Nonnull UUID testUuid, @Nonnull ContainerTestListener listener) {
        checkUuid(testUuid);
        this.listener = listener;
    }

    @Nonnull
    @Override
    public Optional<TestContainerStatusMsg> retrieveStatus(UUID testUuid) {
        checkUuid(testUuid);
        return Optional.of(new TestContainerStatusMsg(testUuid, Phase.CREATE, TestContainerStatusMsg.Status.PENDING,
                "Dummy status", "dummy_container_id", null, null,
                Collections.emptyList()));
    }

    private void checkUuid(UUID uuid) {
        if (!TestUtils.TEST_UUID.equals(uuid)) {
            throw new IllegalArgumentException("Unknown UUID: " + uuid);
        }
    }

    @Override
    public void dispose() {
        // Nothing to do.
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
}
