package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

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

    private String name  = "<Unknown>";
    private String containerId;
    private String networkIdentity;
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
    DockerInstance(@NotNull DockerImage img) {
        DockerCloudUtils.requireNonNull(img, "Docker image cannot be null.");

        this.img = img;
    }

    /**
     * The instance UUID.
     *
     * @return the instance UUID.
     */
    @NotNull
    UUID getUuid() {
        return uuid;
    }

    @NotNull
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
     *
     * @throws NullPointerException if {@code containerId} is {@code null}
     */
    void setContainerId(@NotNull String containerId) {
        DockerCloudUtils.requireNonNull("Container ID cannot be null.", containerId);
        try {
            lock.lock();
            if (this.containerId != null) {
                throw new IllegalStateException("Container ID can be set only once.");
            }
            this.containerId = containerId;
        } finally {
            lock.unlock();
        }

    }

    @NotNull
    @Override
    public String getName() {
        try {
           lock.lock();
            return name;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the instance name.
     *
     * @param name the instance name
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    void setName(@NotNull String name) {
        DockerCloudUtils.requireNonNull(name, "Name cannot be null.");
        try {
            lock.lock();
            this.name = name;
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    @Override
    public String getImageId() {
        return img.getId();
    }

    @NotNull
    @Override
    public DockerImage getImage() {
        return img;
    }

    @NotNull
    @Override
    public Date getStartedTime() {
        return new Date(startedTimeMillis);
    }

    @Nullable
    @Override
    public String getNetworkIdentity() {
        try {
            lock.lock();
            return networkIdentity;
        } finally {
            lock.unlock();
        }
    }

    @NotNull
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
    void setStatus(@NotNull InstanceStatus status) {
        DockerCloudUtils.requireNonNull(status, "Instance status cannot be null.");
        try {
            lock.lock();
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
        try {
            lock.lock();
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
        try {
            lock.lock();
            this.containerInfo = containerInfo;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyFailure(@NotNull String msg, @Nullable Throwable throwable) {
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
    public boolean containsAgent(@NotNull AgentDescription agent) {
        return uuid.equals(DockerCloudUtils.getInstanceId(agent));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        String networkIdentity = this.networkIdentity;
        if (networkIdentity != null) {
            sb.append(" (").append(networkIdentity).append(")");
        }
        return sb.toString();
    }
}
