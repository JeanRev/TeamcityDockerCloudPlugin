package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Docker {@link CloudInstance}.
 */
public class DockerInstance implements CloudInstance, DockerCloudErrorHandler {

    private final UUID uuid = UUID.randomUUID();
    private final DockerImage img;

    private final long startedTimeMillis = System.currentTimeMillis();

    // This lock ensure a thread-safe usage of all the variables below.
    private final Lock lock = new ReentrantLock();

    private String containerName = null;
    private String containerId;
    private Node containerInfo;
    private InstanceStatus status = InstanceStatus.UNKNOWN;
    private CloudErrorInfo errorInfo;

    /**
     * Creates a new Docker cloud instance.
     *
     * @param img the source image
     *
     * @throws NullPointerException if {@code img} is {@code null}
     */
    DockerInstance(@Nonnull DockerImage img) {
        DockerCloudUtils.requireNonNull(img, "Docker image cannot be null.");

        this.img = img;
    }

    /**
     * The instance UUID.
     *
     * @return the instance UUID.
     */
    @Nonnull
    UUID getUuid() {
        return uuid;
    }

    @Nonnull
    @Override
    public String getInstanceId() {
        return uuid.toString();
    }

    /**
     * Gets the Docker container ID associated with this cloud instance. It could be {@code null} if the container is
     * not known yet or is not available anymore.
     *
     * @return the container ID or {@code null}
     */
    @Nullable
    String getContainerId() {
        return containerId;
    }

    /**
     * Sets the Docker container ID.
     *
     * @param containerId the container ID
     *                    S
     *
     * @throws NullPointerException if {@code containerId} is {@code null}
     */
    void setContainerId(@Nonnull String containerId) {
        DockerCloudUtils.requireNonNull("Container ID cannot be null.", containerId);
        lock.lock();
        try {
            this.containerId = containerId;
        } finally {
            lock.unlock();
        }

    }

    @Nonnull
    @Override
    public String getName() {
        lock.lock();
        try {
            return containerName == null ? "<Unknown>" : containerName;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public String getContainerName() {
        lock.lock();
        try {
            return containerName;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the instance name.
     *
     * @param containerName the instance name
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    void setContainerName(@Nonnull String containerName) {
        DockerCloudUtils.requireNonNull(containerName, "Container name cannot be null.");
        lock.lock();
        try {
            this.containerName = containerName;
        } finally {
            lock.unlock();
        }
    }

    @Nonnull
    @Override
    public String getImageId() {
        return img.getId();
    }

    @Nonnull
    @Override
    public DockerImage getImage() {
        return img;
    }

    @Nonnull
    @Override
    public Date getStartedTime() {
        return new Date(startedTimeMillis);
    }

    @Nullable
    @Override
    public String getNetworkIdentity() {
        // Not too sure what we should do here. Obviously, the TC server knows the agent IP address, and it would not
        // makes much sense to retrieve it ourselves from the container configuration. It would be also difficult to
        // return an usable hostname.
        return null;
    }

    @Nonnull
    @Override
    public InstanceStatus getStatus() {
        return status;
    }

    /**
     * Set this instance status.
     *
     * @param status the instance status
     *
     * @throws NullPointerException if {@code status} is {@code null}
     */
    void setStatus(@Nonnull InstanceStatus status) {
        DockerCloudUtils.requireNonNull(status, "Instance status cannot be null.");
        lock.lock();
        try {
            this.status = status;
        } finally {
            lock.unlock();
        }

    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    /**
     * Gets the JSON node describing the associated container if available. The node structure is described in the
     * <a href="https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/list-containers"><i>list
     * containers</i></a> command of the remote API documentation.
     *
     * @return the JSON node or {@code null} if not available
     */
    @Nullable
    public Node getContainerInfo() {
        lock.lock();
        try {
            return containerInfo;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the JSON node holding the container description.
     *
     * @param containerInfo the JSON node or {@code null} if not available
     */
    void setContainerInfo(@Nullable Node containerInfo) {
        lock.lock();
        try {
            this.containerInfo = containerInfo;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyFailure(@Nonnull String msg, @Nullable Throwable throwable) {
        DockerCloudUtils.requireNonNull(msg, "Message cannot be null.");

        try {
            lock.lock();
            if (throwable != null) {
                this.errorInfo = new CloudErrorInfo(msg, msg, throwable);
            } else {
                this.errorInfo = new CloudErrorInfo(msg, msg);
            }

            setStatus(InstanceStatus.ERROR);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public boolean containsAgent(@Nonnull AgentDescription agent) {
        return uuid.equals(DockerCloudUtils.getInstanceId(agent));
    }

    @Override
    public String toString() {
        return getName();
    }
}
