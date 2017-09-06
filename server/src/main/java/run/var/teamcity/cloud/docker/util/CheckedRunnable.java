package run.var.teamcity.cloud.docker.util;

/**
 * A runnable throwing a checked exception.
 *
 * @param <E> the exception type
 */
public interface CheckedRunnable<E extends Exception> {

    /**
     * Executes the runnable.
     *
     * @throws E if any
     */
    void run() throws E;
}
