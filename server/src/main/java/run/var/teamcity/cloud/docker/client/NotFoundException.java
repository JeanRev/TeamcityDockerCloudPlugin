package run.var.teamcity.cloud.docker.client;

/**
 * Exception thrown when a Docker resource is not found.
 */
public class NotFoundException extends InvocationFailedException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    NotFoundException(String msg) {
        super(msg);
    }
}
