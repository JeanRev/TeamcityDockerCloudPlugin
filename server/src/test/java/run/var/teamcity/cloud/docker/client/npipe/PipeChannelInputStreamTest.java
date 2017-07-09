package run.var.teamcity.cloud.docker.client.npipe;

import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Test;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static run.var.teamcity.cloud.docker.test.TestUtils.mustBlock;
import static run.var.teamcity.cloud.docker.test.TestUtils.runAsync;

/**
 * {@link PipeChannelInputStream} test suite.
 */
public class PipeChannelInputStreamTest {

    private TestPipeChannel testChannel;

    @Test(timeout = 10000)
    public void available() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 2000);

        assertThat(input.available()).isZero();

        byte[] data = new byte[42];

        testChannel.putInReadBuffer(data);

        assertThat(input.available()).isEqualTo(data.length);

        input.read(data);

        assertThat(input.available()).isZero();
    }

    @Test(timeout = 10000)
    public void simpleRead() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 2000);

        String msg = "Hello world!";

        testChannel.printToReadBuffer(msg);

        byte[] buffer = new byte[4096];

        int n = input.read(buffer);

        assertThat(new String(buffer, 0, n, StandardCharsets.UTF_8)).isEqualTo(msg);

        mustBlock(input::read);
    }

    @Test(timeout = 10000)
    public void readSmallBuffer() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 2000);

        String msg = "Hello world!";
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[5];

        assertThat(buffer.length).isLessThan(msgBytes.length);
        assertThat(msgBytes.length % buffer.length).isNotZero();

        testChannel.putInReadBuffer(msgBytes);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int n;
        while (baos.size() < msgBytes.length && (n = input.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }

        assertThat(baos.toByteArray()).isEqualTo(msgBytes);

        mustBlock(input::read);
    }

    @Test(timeout = 10000)
    public void readSingleByte() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 2000);

        String msg = "Hello world!";
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);

        testChannel.putInReadBuffer(msgBytes);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int b;
        while (baos.size() < msgBytes.length && (b = input.read()) != -1) {
            baos.write(b);
        }

        assertThat(baos.toByteArray()).isEqualTo(msgBytes);

        mustBlock(input::read);
    }

    @Test(timeout = 10000)
    public void readWithOffsetUndLength() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 2000);

        byte[] msgBytes = new byte[]{1, 2, 3};

        byte[] buffer = new byte[msgBytes.length + 4];

        testChannel.putInReadBuffer(msgBytes);
        int n = input.read(buffer, 2, buffer.length - 2);

        assertThat(n).isEqualTo(msgBytes.length);

        assertThat(buffer).containsExactly((byte) 0, (byte) 0, (byte) 1, (byte) 2, (byte) 3, (byte) 0, (byte) 0);

        Arrays.fill(buffer, (byte) 0);

        testChannel.exhaustReadBuffer();
        testChannel.putInReadBuffer(msgBytes);
        n = input.read(buffer, 0, 2);

        assertThat(n).isEqualTo(2);

        assertThat(buffer).containsExactly((byte) 1, (byte) 2, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);

        Arrays.fill(buffer, (byte) 0);

        testChannel.exhaustReadBuffer();
        testChannel.putInReadBuffer(msgBytes);
        n = input.read(buffer, 2, 2);

        assertThat(n).isEqualTo(2);

        assertThat(buffer).containsExactly((byte) 0, (byte) 0, (byte) 1, (byte) 2, (byte) 0, (byte) 0, (byte) 0);
    }

    @Test(timeout = 10000)
    public void timeoutRead() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 1000);

        assertThat(Stopwatch.measureMillis(() -> {
            try {
                input.read();
                fail("Read operation must not complete.");
            } catch (SocketTimeoutException e) {
                // OK
            } catch (IOException e) {
                fail("Read failed.", e);
            }
        })).isCloseTo(1000, Offset.offset(150L));
    }

    @Test(timeout = 10000)
    public void targetChannelClosed() throws Exception {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 1000);

        CompletableFuture<Void> futur = runAsync(() -> {
            int n = input.read();
            assertThat(n).isEqualTo(-1);
        });

        testChannel.shutdownOutputStreams();

        futur.get(500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void invalidConstructorOption() throws IOException {
        testChannel = new TestPipeChannel();

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new PipeChannelInputStream(null, 1000));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PipeChannelInputStream(testChannel, -1));
    }

    @Test(timeout = 10000)
    public void invalidReadParameter() throws IOException {
        testChannel = new TestPipeChannel();

        PipeChannelInputStream input = new PipeChannelInputStream(testChannel, 1000);

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> input.read(new byte[10], -1, 5));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> input.read(new byte[10], 0, -1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> input.read(new byte[10], 9, 2));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> input.read(new byte[10], 11, 0));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> input.read(null, 0, 0));
    }

    @After
    public void cleanup() throws IOException {
        if (testChannel != null) {
            testChannel.close();
        }
    }
}