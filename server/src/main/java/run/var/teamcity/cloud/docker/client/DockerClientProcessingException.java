package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.Nullable;

/**
 * Processing exception when interacting with the Docker client. Indicates that no communication can be established
 * with the Docker instance (eg. no network connectivity, cannot unmarshall messages, etc).
 */
public class DockerClientProcessingException extends DockerClientException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public DockerClientProcessingException(@Nullable String msg) {
        super(msg);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param msg the message (may be null)
     * @param cause the exception cause (may be null)
     */
    public DockerClientProcessingException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
