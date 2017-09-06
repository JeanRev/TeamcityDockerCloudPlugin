package run.var.teamcity.cloud.docker.client.npipe;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test {@link PipeChannel}.
 */
public class TestPipeChannel implements PipeChannel {

    /**
     * Internal executor to provide async behaviour.
     */
    private ExecutorService executor = Executors.newCachedThreadPool();

    private final int bufferSize;

    private final PipedInputStream readBufferInput;
    private final PipedOutputStream readBufferOutput;

    private final PipedInputStream writeBufferInput;
    private final PipedOutputStream writeBufferOutput;

    private volatile boolean open = true;

    public TestPipeChannel() throws IOException {
        this(4096);
    }

    public TestPipeChannel(int bufferSize) throws IOException {
        this.bufferSize = bufferSize;

        readBufferInput = new PipedInputStream(bufferSize);
        readBufferOutput = new PipedOutputStream(readBufferInput);

        writeBufferInput = new PipedInputStream(bufferSize);
        writeBufferOutput = new PipedOutputStream(writeBufferInput);
    }

    /**
     * Put the given bytes into the read buffer.
     *
     * @param data the data
     *
     * @throws NullPointerException if {@code data} is {@code null}
     * @throws IOException if any
     */
    public void putInReadBuffer(byte[] data) throws IOException {
        readBufferOutput.write(data);
    }

    /**
     * Print the given string in the read buffer with UTF-8 encoding.
     *
     * @param txt the string
     *
     * @throws NullPointerException if {@code txt} is {@code null}
     * @throws IOException if any
     */
    public void printToReadBuffer(String txt) throws IOException {
        readBufferOutput.write(txt.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the whole content of the write buffer.
     *
     * @return a new byte array containing the write buffer content, truncated to the number of valid bytes available.
     *
     * @throws IOException if any
     */
    @Nonnull
    public byte[] getWriteBufferContent() throws IOException {
        byte[] buffer = new byte[bufferSize];
        int n = writeBufferInput.read(buffer);
        return Arrays.copyOf(buffer, n);
    }

    /**
     * Read the whole content of the write buffer as an UTF-8 String.
     *
     * @return the write buffer content as an UTF-8 string
     *
     * @throws IOException if any
     */
    @Nonnull
    public String readWriteBufferContent() throws IOException {
        return new String(getWriteBufferContent(), StandardCharsets.UTF_8);
    }

    @Override
    public Future<Integer> read(ByteBuffer bb) {
        return executor.submit(() -> {
            checkOpen();
            int n;
            try {
                n = readBufferInput.read(bb.array(), bb.position(), bb.limit() - bb.position());
            } catch (InterruptedIOException e) {
                throw new AsynchronousCloseException();
            }
            return n;
        });
    }

    @Override
    public Future<Integer> write(ByteBuffer bb) {
        return executor.submit(() -> {
            checkOpen();
            int len = bb.limit() - bb.position();
            try {
                writeBufferOutput.write(bb.array(), bb.position(), len);
            } catch (InterruptedIOException e) {
                throw new AsynchronousCloseException();
            }
            bb.position(bb.position() + len);
            return len;
        });
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Shutdown read and write buffer output streams. This will effectively causes an end-of-stream to be notified to
     * any reader waiting on the corresponding input streams.
     *
     * @throws IOException if any
     */
    public void shutdownOutputStreams() throws IOException {
        readBufferOutput.close();
        writeBufferOutput.close();
    }

    @Override
    public void close() throws IOException {
        open = false;

        shutdownOutputStreams();

        writeBufferInput.close();
        readBufferInput.close();

        executor.shutdownNow();
    }

    @Override
    public int available() throws IOException {
        return readBufferInput.available();
    }

    /**
     * Discard the read buffer content.
     *
     * @throws IOException if any
     */
    public void exhaustReadBuffer() throws IOException {
        readBufferInput.read(new byte[readBufferInput.available()]);
    }

    private void checkOpen() throws ClosedChannelException {
        if (!open) {
            throw new ClosedChannelException();
        }
    }
}
