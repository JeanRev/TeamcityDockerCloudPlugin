package run.var.teamcity.cloud.docker.client;

import javax.annotation.Nullable;

/**
 * Exception thrown when access to a resource is forbidden.
 */
public class UnauthorizedException extends InvocationFailedException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public UnauthorizedException(@Nullable String msg) {
        super(msg);
    }
}
