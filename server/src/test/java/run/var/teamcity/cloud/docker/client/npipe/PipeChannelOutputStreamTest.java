package run.var.teamcity.cloud.docker.client.npipe;

import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Test;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static run.var.teamcity.cloud.docker.test.TestUtils.runAsync;

/**
 * {@link PipeChannelOutputStream} test suite.
 */
public class PipeChannelOutputStreamTest {

    private TestPipeChannel testChannel;

    @Test(timeout = 10000)
    public void simpleWrite() throws IOException {
        testChannel = new TestPipeChannel(4096);

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(2));

        String msg = "Hello world!";

        output.write(msg.getBytes(StandardCharsets.UTF_8));

        assertThat(testChannel.readWriteBufferContent()).isEqualTo(msg);
    }

    @Test(timeout = 10000)
    public void writeSingleByte() throws IOException {
        testChannel = new TestPipeChannel(4096);

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(2));

        String msg = "Hello world!";

        for (byte b : msg.getBytes(StandardCharsets.UTF_8)) {
            output.write(Byte.toUnsignedInt(b));
        }

        assertThat(testChannel.readWriteBufferContent()).isEqualTo(msg);
    }

    @Test(timeout = 10000)
    public void writeWithOffsetUndLength() throws IOException {
        testChannel = new TestPipeChannel(4096);

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(2));

        byte[] msgBytes = new byte[]{1, 2, 3};

        output.write(msgBytes, 0, msgBytes.length - 1);

        assertThat(testChannel.getWriteBufferContent()).containsExactly((byte) 1, (byte) 2);

        output.write(msgBytes, 2, msgBytes.length - 2);

        assertThat(testChannel.getWriteBufferContent()).containsExactly((byte) 3);

        output.write(msgBytes, 1, 1);

        assertThat(testChannel.getWriteBufferContent()).containsExactly((byte) 2);
    }

    @Test(timeout = 10000)
    public void writeTimeout() throws IOException {
        int writeBufferSize = 4;
        byte[] msgBytes = "Hello world!".getBytes(StandardCharsets.UTF_8);

        assertThat(msgBytes.length).isGreaterThan(writeBufferSize);

        testChannel = new TestPipeChannel(writeBufferSize);

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(1));

        assertThat(Stopwatch.measure(() -> {
            try {
                output.write(msgBytes);
                fail("Write operation must not complete.");
            } catch (WriteTimeoutIOException e) {
                // OK
            } catch (IOException e) {
                fail("Unexpected exception.", e);
            }
        }).toMillis()).isCloseTo(1000, Offset.offset(150L));
    }

    @Test(timeout = 10000)
    public void infiniteWriteTimeout() throws IOException {
        int writeBufferSize = 4;
        byte[] msgBytes = "Hello world!".getBytes(StandardCharsets.UTF_8);

        assertThat(msgBytes.length).isGreaterThan(writeBufferSize);

        testChannel = new TestPipeChannel(writeBufferSize);

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(0));

        Future<Void> future = runAsync(() -> {
            try {
                output.write(msgBytes);
                fail("Write operation must not complete.");
            } catch (SocketTimeoutException e) {
                fail("Unexpected timeout.", e);
            } catch (IOException e) {
                fail("Read failed.", e);
            }
        });

        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            fail("Unexpected failure.", e);
        } catch (TimeoutException e) {
            // OK
        }
    }

    @Test(timeout = 10000)
    public void targetChannelClosed() throws Exception {
        int writeBufferSize = 4;
        byte[] msgBytes = "Hello world!".getBytes(StandardCharsets.UTF_8);

        assertThat(msgBytes.length).isGreaterThan(writeBufferSize);

        testChannel = new TestPipeChannel(writeBufferSize);

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(1));

        CompletableFuture<Void> futur = runAsync(() -> {
            try {
                output.write(msgBytes);
            } catch (IOException e) {
                // OK
            }
        });

        testChannel.shutdownOutputStreams();

        futur.get(500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void invalidConstructorParameter() throws IOException {
        testChannel = new TestPipeChannel();

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new PipeChannelOutputStream(null, Duration.ofSeconds(1)));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PipeChannelOutputStream(testChannel, Duration.ofMillis(-1)));
    }

    @Test(timeout = 10000)
    public void invalidWriteParameter() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelOutputStream output = new PipeChannelOutputStream(testChannel, Duration.ofSeconds(1));

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> output.write(new byte[10], -1, 5));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> output.write(new byte[10], 0, -1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> output.write(new byte[10], 9, 2));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> output.write(new byte[10], 11, 0));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> output.write(null, 0, 0));
    }

    @After
    public void cleanup() throws IOException {
        if (testChannel != null) {
            testChannel.close();
        }
    }
}