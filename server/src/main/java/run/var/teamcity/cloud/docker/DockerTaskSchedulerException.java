package run.var.teamcity.cloud.docker;

/**
 * Exception for task-scheduling related exceptions.
 */
public class DockerTaskSchedulerException extends RuntimeException {

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param msg the message (may be null)
     * @param cause the exception cause (may be null)
     */
    public DockerTaskSchedulerException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
