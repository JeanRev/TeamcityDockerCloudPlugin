package run.var.teamcity.cloud.docker.web;

import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.DockerImageConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TestContainerTestManager extends ContainerTestManager {

    private Action action;
    private UUID testUuid;
    private DockerCloudClientConfig clientConfig;
    private DockerImageConfig imageConfig;

    private TestContainerStatusMsg statusMsg;

    private boolean disposed = false;

    private final Map<UUID, ContainerTestStatusListener> statusListeners = new HashMap<>();
    private final Set<UUID> knownTestUuids = new HashSet<>();

    @Override
    TestContainerStatusMsg doAction(Action action, UUID testUuid, DockerCloudClientConfig clientConfig, DockerImageConfig imageConfig) {
        this.action = action;
        this.testUuid = testUuid;
        this.clientConfig = clientConfig;
        this.imageConfig = imageConfig;
        return statusMsg;
    }

    @Override
    void dispose() {
        disposed = true;
    }

    @Override
    void setStatusListener(UUID testUuid, ContainerTestStatusListener listener) {
        if (!knownTestUuids.contains(testUuid)) {
            throw new IllegalArgumentException("Unknown test UUID: " + testUuid);
        }
        statusListeners.put(testUuid, listener);
    }

    public Action getAction() {
        return action;
    }

    public UUID getTestUuid() {
        return testUuid;
    }

    public DockerCloudClientConfig getClientConfig() {
        return clientConfig;
    }

    public DockerImageConfig getImageConfig() {
        return imageConfig;
    }

    public void setStatusMsg(TestContainerStatusMsg statusMsg) {
        this.statusMsg = statusMsg;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public TestContainerTestManager knownTestUuid(UUID uuid) {
        knownTestUuids.add(uuid);
        return this;
    }
}
