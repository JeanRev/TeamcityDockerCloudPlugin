package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * A type of standard input/output.
 */
public enum StdioType {
    /**
     * Standard input.
     */
    STDIN,
    /**
     * Standard output.
     */
    STDOUT,
    /**
     * Standard error.
     */
    STDERR;

    public static Set<StdioType> all() {
        return EnumSet.allOf(StdioType.class);
    }

    /**
     * Resolves a stream type as defined in the Docker remote API.
     *
     * @param streamType the numerical stream type
     *
     * @return the resolved type
     *
     * @throws IllegalArgumentException if the given type cannot be resolved
     */
    @NotNull
    static StdioType fromStreamType(long streamType) {
        if (streamType == 0L) {
            return STDIN;
        } else if (streamType == 1L) {
            return STDOUT;
        } else if (streamType == 2L) {
            return STDERR;
        } else {
            throw new IllegalArgumentException("Invalid stream type: " + streamType);
        }
    }
}
