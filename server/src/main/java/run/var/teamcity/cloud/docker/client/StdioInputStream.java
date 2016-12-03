package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * A simple input stream filter adding a {@link StdioType} to an input stream.
 * <p>
 * This stream filter will typically be used to read the output of a running process. If no type is specified
 * the stream is of a unknown type, possibly combining multiple types of stream.
 * </p>
 * <p>
 * Note that {@link StdioType#STDIN} is considered to be a valid type of process output, as the process may echo
 * our commands back to us.
 * </p>
 */
public class StdioInputStream extends FilterInputStream {

    private final StdioType type;

    /**
     * Creates a new stream filter of the specified type.
     *
     * @param stream the source input stream
     * @param type   the type of stream, or {@code null} if unknown
     */
    StdioInputStream(@NotNull InputStream stream, @Nullable StdioType type) {
        super(stream);
        DockerCloudUtils.requireNonNull(stream, "Input stream cannot be null.");
        this.type = type;
    }

    /**
     * Gets the stream type.
     *
     * @return the stream type, possibly {@code null} if unknown
     */
    @Nullable
    public StdioType getType() {
        return type;
    }
}
