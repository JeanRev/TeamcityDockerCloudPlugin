package run.var.teamcity.cloud.docker;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * A {@link DockerTask} associated with a {@link DefaultDockerCloudClient}.
 */
abstract class DockerClientTask extends DockerTask {

    private final DockerCloudClient client;

    /**
     * Creates a one-shot task.
     *
     * @param operationName the operation name
     * @param client        the cloud client
     * @throws NullPointerException if any argument is {@code null}
     */
    DockerClientTask(@Nonnull String operationName, @Nonnull DockerCloudClient client) {
        super(operationName, client);
        this.client = client;
    }

    /**
     * Creates a repeatable task.
     *
     * @param operationName the operation name
     * @param client        the cloud client
     * @param initialDelay  the delay preceding the initial scheduling of the task
     * @param delay         the delay preceding the subsequent scheduling of the task
     * @param timeUnit      the time unit used with
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if a delay is negative
     */
    DockerClientTask(@Nonnull String operationName, @Nonnull DockerCloudClient client, long initialDelay, long delay,
                     @Nonnull TimeUnit
                             timeUnit) {
        super(operationName, client, initialDelay, delay, timeUnit);
        this.client = client;
    }

    @Override
    public String toString() {
        return "DockerInstanceTask[operationName: " + getOperationName() + ", instance: " + client.getUuid() + "]";
    }
}
