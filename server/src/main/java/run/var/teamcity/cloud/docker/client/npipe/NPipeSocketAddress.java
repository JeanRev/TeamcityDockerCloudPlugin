package run.var.teamcity.cloud.docker.client.npipe;

import javax.annotation.Nonnull;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Windows named pipe address.
 */
public class NPipeSocketAddress extends SocketAddress {

    // Pattern for a named pipe location.
    // It must basically starts with a double back-slash, followed by a server name (or a dot for a local pipe),
    // then a directory named 'pipe', and finally the pipe name. The pipe name itself can be divided into several
    // path fragment with backslashes.
    // See:
    // https://msdn.microsoft.com/en-us/library/windows/desktop/aa365783(v=vs.85).aspx
    private final static Pattern PIPE_NAME_PTN = Pattern.compile("\\\\\\\\[^\\\\]+\\\\pipe(?:\\\\[^\\\\]+)+");

    private final Path pipe;

    NPipeSocketAddress(Path pipe) {
        assert pipe != null;
        this.pipe = pipe;
    }

    /**
     * Create a named pipe address from the given file system location. Sanity check will be performed on the given
     * path to ensure that we are actually accessing a pipe and not a regular file (this would cause some weird
     * dysfunction since this implementation assumes that the targeted "file" will be truncated on each read).
     *
     * @param pipe the pipe location
     *
     * @return the created pipe address
     *
     * @throws NullPointerException if {@code pipe} is {@code null}
     */
    @Nonnull
    public static NPipeSocketAddress fromPath(@Nonnull Path pipe) {
        if (!PIPE_NAME_PTN.matcher(pipe.toString()).matches()) {
            throw new IllegalArgumentException("The provided path does not looks like a pipe name: " + pipe);
        }
        return new NPipeSocketAddress(pipe);
    }

    /**
     * Gets the pipe location.
     *
     * @return the pipe location
     */
    @Nonnull
    public Path getPipe() {
        return pipe;
    }
}
