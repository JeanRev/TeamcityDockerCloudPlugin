package run.var.teamcity.cloud.docker.client;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.Set;

/**
 * A type of standard input/output.
 */
public enum StdioType {
    /**
     * Standard input.
     */
    STDIN(0),
    /**
     * Standard output.
     */
    STDOUT(1),
    /**
     * Standard error.
     */
    STDERR(2);

    private final long streamType;

    StdioType(long streamType) {
        this.streamType = streamType;
    }

    /**
     * Gets the stream type as defined in the Docker remote API.
     *
     * @return the stream type
     */
    public long streamType() {
        return streamType;
    }

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
    @Nonnull
    static StdioType fromStreamType(long streamType) {
        for (StdioType type : values()) {
            if (type.streamType == streamType) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid stream type: " + streamType);
    }
}
