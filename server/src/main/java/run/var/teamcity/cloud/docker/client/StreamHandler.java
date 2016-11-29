package run.var.teamcity.cloud.docker.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Gives access to a process streams. The {@code STDIN} to the process is available as an {@code OutputStream}. The
 * process output may then be read as a sequence of one or several {@link StdioInputStream}.
 * <p>
 *     Closing a stream does not necessarily close the others, but closing this handlers will close all of the wrapped
 *     streams.
 * </p>
 */
public abstract class StreamHandler implements AutoCloseable {

    private final Closeable closeHandle;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Creates a new handler instance.
     *
     * @param closeHandle the source entity that will need to be closed along the wrapped streams
     * @param inputStream the process input that will be demultiplexed
     * @param outputStream the process output stream
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    StreamHandler(@NotNull Closeable closeHandle, @NotNull InputStream inputStream, @NotNull OutputStream outputStream) {
        DockerCloudUtils.requireNonNull(closeHandle, "Close handle cannot be null.");
        DockerCloudUtils.requireNonNull(inputStream, "Source input stream cannot be null.");
        DockerCloudUtils.requireNonNull(outputStream, "Source output stream cannot be null.");
        this.closeHandle = closeHandle;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * Gets the next demultiplexed input stream fragment.
     *
     * @return the stream fragment, or {@code null} if no more fragments are available.
     *
     * @throws IOException if an error occurred while accessing the wrapped stream
     */
    @Nullable
    public abstract StdioInputStream getNextStreamFragment() throws IOException;

    /**
     * Gets the process output stream ({@code STDIN} from the process perspective).
     *
     * @return the output stream
     */
    @NotNull
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        outputStream.close();
        closeHandle.close();
    }
}
;