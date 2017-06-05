package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.StdioInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Gives access to a process streams. The {@code STDIN} to the process is available as an {@code OutputStream}. The
 * process output may then be read as a sequence of one or several {@link StdioInputStream}.
 * <p>
 * Closing a stream does not necessarily close the others, but closing this handlers will close all of the wrapped
 * streams.
 * </p>
 */
public interface StreamHandler extends AutoCloseable {

    /**
     * Gets the next demultiplexed input stream fragment.
     *
     * @return the stream fragment, or {@code null} if no more fragments are available.
     *
     * @throws IOException if an error occurred while accessing the wrapped stream
     */
    @Nullable
    StdioInputStream getNextStreamFragment() throws IOException;

    /**
     * Gets the process output stream ({@code STDIN} from the process perspective).
     *
     * @return the output stream
     */
    @Nonnull
    OutputStream getOutputStream();

    @Override
    void close() throws IOException;
}
