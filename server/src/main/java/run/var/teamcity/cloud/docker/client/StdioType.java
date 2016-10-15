package run.var.teamcity.cloud.docker.client;

import java.util.EnumSet;
import java.util.Set;

/**
 * Created by jr on 04.09.16.
 */
public enum StdioType {
    STDIN,
    STDOUT,
    STDERR;

    public static Set<StdioType> all() {
        return EnumSet.allOf(StdioType.class);
    }

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
