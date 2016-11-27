package run.var.teamcity.cloud.docker.web;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.ScheduledFutureWithRunnable;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Phase;
import run.var.teamcity.cloud.docker.web.TestContainerStatusMsg.Status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;


public class ContainerSpecTest implements ContainerTestTaskHandler{

    private final static Logger LOG = DockerCloudUtils.getLogger(ContainerSpecTest.class);

    private final UUID uuid = UUID.randomUUID();
    private final ReentrantLock lock = new ReentrantLock();
    private final DockerClient client;
    private final DockerClientConfig clientConfig;
    private final ContainerTestListener statusListener;
    private final StreamingController streamingController;

    private long lastInteraction;
    private String containerId;
    private boolean buildAgentDetected = false;

    private ScheduledFutureWithRunnable<? extends ContainerTestTask> currentTaskFuture = null;

    private ContainerSpecTest(DockerClientConfig clientConfig, DockerClient client,
                              ContainerTestListener statusListener, StreamingController streamingController) {
        assert client != null  && statusListener != null;

        this.clientConfig = clientConfig;
        this.client = client;
        this.statusListener = statusListener;
        this.streamingController = streamingController;

        notifyInteraction();
    }

    public static ContainerSpecTest newTestInstance(@NotNull DockerCloudClientConfig clientConfig,
                                                    @NotNull DockerClientFactory dockerClientFactory,
                                                    @NotNull ContainerTestListener statusListener,
                                                    @Nullable StreamingController streamingController) {
        DockerCloudUtils.requireNonNull(clientConfig, "Client config cannot be null.");
        DockerCloudUtils.requireNonNull(dockerClientFactory, "Docker client factory cannot be null.");
        DockerCloudUtils.requireNonNull(statusListener, "Status listener cannot be null.");
        DockerClient client = dockerClientFactory.createClient(clientConfig.getDockerClientConfig()
                .threadPoolSize(1));
        return new ContainerSpecTest(clientConfig.getDockerClientConfig(), client, statusListener,
                streamingController);
    }

    /**
     * Gets the test UUID.
     *
     * @return the test UUID
     *
     */
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the container ID associated with this test. May be {@code null} if the container creation did not succeed
     * yet.
     *
     * @return the container ID or {@code null}
     */
    @Nullable
    public String getContainerId() {
        lock.lock();
        try {
            return containerId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the atmosphere resource associated with this test if any.
     *
     * @return the atmosphere resource or {@code null}
     */
    @Nullable
    public ContainerTestListener getStatusListener() {
        lock.lock();
        try {
            return statusListener;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the task currently associated with this test. May be {@code null} if no task was associated yet.
     *
     * @return the task
     */
    @Nullable
    public ScheduledFutureWithRunnable<? extends ContainerTestTask> getCurrentTaskFuture() {
        lock.lock();
        try {
            return currentTaskFuture;
        }finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public DockerClient getDockerClient() {
        return client;
    }

    /**
     * Notify a user interaction for this test.
     */
    public void notifyInteraction() {
        lastInteraction = System.nanoTime();
    }

    /**
     * Gets the last user interaction for this test as a nano timestamp.
     *
     * @return a nano timestamp
     */
    public long getLastInteraction() {
        return lastInteraction;
    }

    /**
     * Sets the current task associated with this test.
     *
     * @param currentTask the test task
     *
     * @throws NullPointerException if {@code currentTask} is {@code null}
     */
    public void setCurrentTask(@NotNull ScheduledFutureWithRunnable<? extends ContainerTestTask>
            currentTask) {
        DockerCloudUtils.requireNonNull(currentTask, "Current task cannot be null.");
        lock.lock();
        try {
            this.currentTaskFuture = currentTask;
            statusListener.notifyStatus(null);
            notifyInteraction();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyContainerId(@NotNull String containerId) {
        DockerCloudUtils.requireNonNull(containerId, "Container ID cannot be null.");
        lock.lock();
        try {
            this.containerId = containerId;
        } finally {
            lock.unlock();
        }

        if (streamingController != null) {
            streamingController.registerContainer(uuid, new ContainerCoordinates(containerId, clientConfig));
        }
    }

    @Override
    public void notifyStatus(@NotNull Phase phase, @NotNull Status status, @Nullable String msg,
                             @Nullable Throwable failure, @NotNull List<String> warnings) {
        DockerCloudUtils.requireNonNull(phase, "Test phase cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Test status cannot be null.");
        DockerCloudUtils.requireNonNull(status, "Warnings list cannot be null.");

        statusListener.notifyStatus(new TestContainerStatusMsg(uuid, phase, status, msg, failure, warnings));
    }

    @Override
    public boolean isBuildAgentDetected() {
        lock.lock();
        try {
            return buildAgentDetected;
        } finally {
            lock.unlock();
        }

    }

    public void setBuildAgentDetected(boolean buildAgentDetected) {
        lock.lock();
        try {
            this.buildAgentDetected = buildAgentDetected;
        } finally {
            lock.unlock();
        }
    }
}
