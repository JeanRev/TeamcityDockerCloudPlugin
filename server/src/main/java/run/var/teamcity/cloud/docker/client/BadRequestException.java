package run.var.teamcity.cloud.docker.client;

import javax.annotation.Nullable;

/**
 * Exception thrown when a Docker client invocation failed on the server side.
 */
public class BadRequestException extends InvocationFailedException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public BadRequestException(@Nullable String msg) {
        super(msg);
    }
}
