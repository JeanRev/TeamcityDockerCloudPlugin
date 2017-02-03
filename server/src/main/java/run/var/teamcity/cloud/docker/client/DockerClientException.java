package run.var.teamcity.cloud.docker.client;

import javax.annotation.Nullable;

/**
 * Base class for all exceptions related to the {@link DefaultDockerClient}.
 */
public class DockerClientException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    DockerClientException(@Nullable String msg) {
        super(msg);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param msg   the message (may be null)
     * @param cause the exception cause (may be null)
     */
    DockerClientException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
