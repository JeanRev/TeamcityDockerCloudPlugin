package run.var.teamcity.cloud.docker.client;


import com.intellij.openapi.diagnostic.Logger;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link AbstractStreamHandler} supporting multiplexed {@link StdioInputStream}s.
 */
public class MultiplexedStreamHandler extends AbstractStreamHandler {

    private static final Logger LOG = DockerCloudUtils.getLogger(MultiplexedStreamHandler.class);

    private final static int STREAM_HEADER_SIZE = 8;

    private final byte[] headerBuffer = new byte[STREAM_HEADER_SIZE];

    private final InputStream inputStream;

    private CappedInputStream previousStreamFragment;

    MultiplexedStreamHandler(Closeable closeHandle, InputStream inputStream, OutputStream outputStream) {
        super(closeHandle, inputStream, outputStream);

        this.inputStream = inputStream;
    }

    @Nullable
    @Override
    public StdioInputStream getNextStreamFragment() throws IOException {

        if (previousStreamFragment != null) {
            previousStreamFragment.exhaustAndClose();
        }

        // Demultiplexes the streams using the instructions from the Docker remote API.
        int n;
        int headerOffset = 0;
        while ((n = inputStream.read(headerBuffer, headerOffset, headerBuffer.length - headerOffset)) != -1) {
            headerOffset += n;
            assert headerOffset <= headerBuffer.length;
            if (headerOffset == headerBuffer.length) {
                // Header buffer is full.
                ByteBuffer bb = ByteBuffer.wrap(headerBuffer);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                StdioType type;
                try {
                    type = StdioType.fromStreamType(DockerCloudUtils.toUnsignedLong(bb.getInt()));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid stream content.", e);
                }

                bb.order(ByteOrder.BIG_ENDIAN);
                long fragmentLength = DockerCloudUtils.toUnsignedLong(bb.getInt());
                assert !bb.hasRemaining() : "Header not fully parsed.";

                CappedInputStream streamFragment = new CappedInputStream(inputStream,
                        fragmentLength);

                previousStreamFragment = streamFragment;

                return new StdioInputStream(streamFragment, type);
            }
        }
        if (headerOffset != 0) {
            LOG.warn("Underflow while reading stream header.");
        }
        return null;
    }
}
