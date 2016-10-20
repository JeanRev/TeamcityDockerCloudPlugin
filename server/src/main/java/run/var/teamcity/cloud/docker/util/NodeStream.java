package run.var.teamcity.cloud.docker.util;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * A stream of {@link Node}.
 */
public interface NodeStream extends Closeable {
    /**
     * Gets the next node on the stream, or {@code null} if the end has been reached.
     *
     * @return the next node available or {@code null}
     *
     * @throws IOException if the next node cannot be fetched from the stream
     */
    @Nullable
    Node next() throws IOException;
}
