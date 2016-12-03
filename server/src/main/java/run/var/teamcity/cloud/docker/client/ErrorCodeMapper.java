package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.Nullable;

/**
 * Maps an HTTP status code to an {@link InvocationFailedException}.
 */
public interface ErrorCodeMapper {

    /**
     * Gets the mapping. The error message is expected to be used "as-is" as the message of the returned exception.
     * <p>
     * The implemented method must assume that the provided status code is always an error code. If no error
     * mapping could be established, this method may return {@code null}, a generic error type will then be used.
     * </p>
     *
     * @param errorCode the status code
     * @param msg       the exception message
     *
     * @return the mapped exception or {@code null} to use a default exception type
     */
    @Nullable
    InvocationFailedException mapToException(int errorCode, String msg);
}
