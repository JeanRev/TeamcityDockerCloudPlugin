package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.InstanceStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link DockerTask} associated with a {@link DockerInstance}.
 *
 * <p>This </p>
 */
abstract class DockerInstanceTask extends DockerTask {

    private final DockerInstance instance;
    private final InstanceStatus scheduledStatus;

    /**
     * Creates a one-shot task.
     *
     * @param operationName   the operation name
     * @param instance        the cloud instance
     * @param scheduledStatus the instance status to be set when the task is scheduled for execution or {@code null} if
     *                        none
     *
     * @throws NullPointerException if {@code operationName} or {@code instance} is {@code null}
     */
    DockerInstanceTask(@Nonnull String operationName, @Nonnull DockerInstance instance, @Nullable InstanceStatus
            scheduledStatus) {
        super(operationName, instance);
        this.instance = instance;
        this.scheduledStatus = scheduledStatus;
    }

    /**
     * Gets the docker instance.
     *
     * @return the docker instance
     */
    @Nonnull
    DockerInstance getInstance() {
        return instance;
    }

    /**
     * Gets the instance status to be set when scheduled for execution.
     *
     * @return the instance status to be set when scheduled for execution
     */
    @Nullable
    InstanceStatus getScheduledStatus() {
        return scheduledStatus;
    }

    @Override
    public String toString() {
        return "DockerInstanceTask[operationName: " + getOperationName() + ", instance: " + instance.getUuid() + "]";
    }
}
