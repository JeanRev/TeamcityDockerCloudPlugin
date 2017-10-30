package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.serverSide.AgentDescription;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.LockHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * A Docker {@link CloudInstance}.
 */
public class DockerInstance implements CloudInstance, DockerCloudErrorHandler {

    private final UUID uuid = UUID.randomUUID();
    private final DockerImage img;

    private Instant startedTime;

    // This lock ensure a thread-safe usage of all the variables below.
    private final LockHandler lock = LockHandler.newReentrantLock();

    private String agentHolderName = null;
    private String resolvedImageName = null;
    private String agentHolderId;
    private String taskId;
    private AgentHolderInfo agentHolderInfo;
    private InstanceStatus status = InstanceStatus.UNKNOWN;
    private CloudErrorInfo errorInfo;
    private Integer agentId;
    private UUID agentRuntimeUuid;

    /**
     * Creates a new Docker cloud instance.
     *
     * @param img the source image
     *
     * @throws NullPointerException if {@code img} is {@code null}
     */
    DockerInstance(@Nonnull DockerImage img) {
        this.img = DockerCloudUtils.requireNonNull(img, "Docker image cannot be null.");

        // The instance is expected to be started immediately (we must do this to ensure that getStartedTime() always
        // return some meaningful value).
        updateStartedTime();
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
     * Gets the Docker agent holder ID associated with this cloud instance.  Only available once the agent holder has
     * been bound with this instance.
     *
     * @return the agent holder ID or {@code null}
     */
    @Nonnull
    Optional<String> getAgentHolderId() {
        return Optional.ofNullable(lock.call(() -> this.agentHolderId));
    }

    /**
     * Gets the task ID associated with this instance, if any.
     *
     * @return the task ID if any
     */
    @Nonnull
    public Optional<String> getTaskId() {
        return Optional.ofNullable(taskId);
    }

    void setTaskId(@Nonnull String taskId) {
        DockerCloudUtils.requireNonNull(taskId, "Task id cannot be null.");

        lock.run(() -> this.taskId = taskId);
    }

    @Nonnull
    @Override
    public String getName() {
        return lock.call(() -> agentHolderName == null ? "<Unknown>" : agentHolderName);
    }

    /**
     * Gets the agent holder name associated with this instance. Only available once the agent holder has been bound
     * with this instance.
     *
     * @return the agent holder name
     *
     * @see #bindWithAgentHolder(NewAgentHolderInfo)
     */
    @Nonnull
    public Optional<String> getAgentHolderName() {
        return Optional.ofNullable(lock.call(() -> agentHolderName));
    }

    /**
     * Gets the instance resolved image name.  Only available once the agent holder has been bound
     * with this instance.
     *
     * @return agent holder resolved image name
     *
     * @see #bindWithAgentHolder(NewAgentHolderInfo)
     */
    @Nonnull
    public Optional<String> getResolvedImageName() {
        return Optional.ofNullable(lock.call(() -> resolvedImageName));
    }

    /**
     * Bind a docker instance with a newly created agent holder.
     *
     * @param agentHolderInfo the new agent holder information set
     *
     * @throws NullPointerException if {@code agentHolderInfo} is {@code null}
     * @throws IllegalArgumentException if this instance is already bound with an agent holder
     */
    public void bindWithAgentHolder(@Nonnull NewAgentHolderInfo agentHolderInfo) {
        DockerCloudUtils.requireNonNull(agentHolderInfo, "Agent holder info cannot be null.");

        lock.run(() -> {
            if (this.agentHolderId != null) {
                assert agentHolderName != null && resolvedImageName != null;
                throw new IllegalStateException("Docker instance already bound with agent holder.");
            }
            this.agentHolderId = agentHolderInfo.getId();
            this.agentHolderName = agentHolderInfo.getName();
            this.resolvedImageName = agentHolderInfo.getResolvedImage();
        });
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
        return Date.from(startedTime);
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

        lock.run(() -> this.status = status);

    }

    final void updateStartedTime() {
        lock.run(() -> startedTime = Instant.now());
    }

    @Nullable
    @Override
    public CloudErrorInfo getErrorInfo() {
        return errorInfo;
    }

    /**
     * Gets the additional container meta-data retrieved from the Docker daemon.
     *
     * @return the additional container meta-data or {@code null} if not available
     */
    @Nonnull
    public Optional<AgentHolderInfo> getAgentHolderInfo() {
        return Optional.ofNullable(lock.call(() -> agentHolderInfo));
    }

    /**
     * Registers or verifies the given agent ID against this instance. If this instance is already registered to an
     * agent, this method has no effect.
     *
     * @param agentId the agent id to be registered
     *
     * @return {@code true} if {@code agentId} matches the currently registered agent, {@code false} otherwise
     */
    public boolean registerOrCompareAgentId(int agentId) {
        return lock.call(() -> {
            if (this.agentId != null) {
                 return this.agentId == agentId;
            }
            this.agentId = agentId;
            return true;
        });
    }

    /**
     * Gets the agent runtime UUID associated with this instance, if any.
     *
     * @return the agent runtime UUID if any
     */
    @Nonnull
    public Optional<UUID> getAgentRuntimeUuid() {
        return Optional.ofNullable(lock.call(() -> agentRuntimeUuid));
    }

    /**
     * Registers the agent runtime UUID against this instance. This method has no effect if another agent has already
     * registered itself.
     *
     * @param agentRuntimeUuid the new agent runtime UUID
     *
     * @throws NullPointerException if {@code agentRuntimeUuid} is {@code null}
     */
    public void registerAgentRuntimeUuid(@Nonnull UUID agentRuntimeUuid) {
        DockerCloudUtils.requireNonNull(agentRuntimeUuid, "Agent runtime UUID cannot be null.");
        lock.run(() -> {
           if (this.agentRuntimeUuid == null) {
               this.agentRuntimeUuid = agentRuntimeUuid;
           }
        });
    }

    /**
     * Unregisters the agent runtime UUID associated with this instance. This method has no effect if no agent are
     * currently registered, or if the provided UUID does not match the UUID registered with this instance.
     *
     * @param agentRuntimeUuid the agent runtime UUID to be unregistered
     *
     * @throws NullPointerException if {@code agentRuntimeUuid} is {@code null}
     */
    public void unregisterAgentRuntimeUUid(@Nonnull UUID agentRuntimeUuid) {
        DockerCloudUtils.requireNonNull(agentRuntimeUuid, "Agent runtime UUID cannot be null.");
        lock.run(() -> {
           if (this.agentRuntimeUuid == null) {
               return;
           }
           if (agentRuntimeUuid.equals(this.agentRuntimeUuid)) {
               this.agentRuntimeUuid = null;
           }
        });
    }

    /**
     * Sets the additional container meta-data.
     *
     * @param agentHolderInfo the container meta-data or {@code null} if not available
     */
    void setAgentHolderInfo(@Nullable AgentHolderInfo agentHolderInfo) {
        lock.run(() -> this.agentHolderInfo = agentHolderInfo);
    }

    @Override
    public void notifyFailure(@Nonnull String msg, @Nullable Throwable throwable) {
        DockerCloudUtils.requireNonNull(msg, "Message cannot be null.");

        lock.run(() -> {
            if (throwable != null) {
                this.errorInfo = new CloudErrorInfo(msg, msg, throwable);
            } else {
                this.errorInfo = new CloudErrorInfo(msg, msg);
            }

            setStatus(InstanceStatus.ERROR);
        });

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
