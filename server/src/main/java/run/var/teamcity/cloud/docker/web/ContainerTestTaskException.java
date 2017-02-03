package run.var.teamcity.cloud.docker.web;

/**
 * {@link RuntimeException} to notify test failures.
 */
public class ContainerTestTaskException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param msg the message (may be null)
     */
    public ContainerTestTaskException(String msg) {
        super(msg);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param msg   the message (may be null)
     * @param cause the exception cause (may be null)
     */
    public ContainerTestTaskException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
