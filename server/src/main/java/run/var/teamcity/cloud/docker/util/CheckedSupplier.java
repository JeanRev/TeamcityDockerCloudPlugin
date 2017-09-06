package run.var.teamcity.cloud.docker.util;

/**
 * A value supplier throwing a checked exception.
 *
 * @param <V> the callable return type
 * @param <E> the exception type
 */
public interface CheckedSupplier<V, E extends Exception> {

    /**
     * Gets a result.
     *
     * @return a result
     *
     * @throws E if any
     */
    V get() throws E;
}
