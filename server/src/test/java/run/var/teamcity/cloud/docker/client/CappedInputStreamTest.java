package run.var.teamcity.cloud.docker.client;


import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link CappedInputStream} test suite.
 */
public class CappedInputStreamTest {

    private byte[] buffer = new byte[100];
    private ByteArrayInputStream testStream;

    @Before
    public void init() {
        buffer = new byte[100];
        for (byte b = 0; b < buffer.length; b++) {
            buffer[b] = b;
        }
        testStream = new ByteArrayInputStream(buffer);
    }

    @Test
    public void uncappedRead() throws IOException {
        CappedInputStream cappedStream = new CappedInputStream(testStream, Long.MAX_VALUE);

        byte[] read = toByteArray(cappedStream);

        assertThat(read).containsExactly(buffer);
    }

    @Test
    public void cappedRead() throws IOException {
        CappedInputStream cappedStream = new CappedInputStream(testStream, 50);

        byte[] read = toByteArray(cappedStream);

        assertThat(read).containsExactly(Arrays.copyOf(buffer, 50));
    }

    @Test
    public void cappedReadExactStreamLength() throws IOException {
        CappedInputStream cappedStream = new CappedInputStream(testStream, buffer.length);

        byte[] read = toByteArray(cappedStream);

        assertThat(read).containsExactly(buffer);
    }

    @Test
    public void zeroCap() throws IOException {
        CappedInputStream cappedStream = new CappedInputStream(testStream, 0);

        byte[] read = toByteArray(cappedStream);

        assertThat(read).isEmpty();
    }

    @Test
    public void invalidCap() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new CappedInputStream(testStream,
                -1));
    }

    @Test
    public void closedStream() {
        CappedInputStream cappedStream = new CappedInputStream(testStream, Long.MAX_VALUE);

        cappedStream.close();

        assertThatExceptionOfType(IOException.class).isThrownBy(cappedStream::read);
        assertThat(cappedStream.available()).isEqualTo(0);
    }

    @Test
    public void availableBytes() throws IOException {
        CappedInputStream cappedStream = new CappedInputStream(testStream, 3);

        assertThat(cappedStream.available()).isEqualTo(3);
        cappedStream.read();
        assertThat(cappedStream.available()).isEqualTo(2);
        cappedStream.read();
        assertThat(cappedStream.available()).isEqualTo(1);
        cappedStream.read();
        assertThat(cappedStream.available()).isEqualTo(0);
        // Stream is now exhausted.
        cappedStream.read();
        assertThat(cappedStream.available()).isEqualTo(0);
    }

    @Test
    public void markNotSupported() {
        CappedInputStream cappedStream = new CappedInputStream(testStream, Long.MAX_VALUE);
        assertThat(cappedStream.markSupported()).isFalse();
        cappedStream.mark(5);
        assertThatExceptionOfType(IOException.class).isThrownBy(cappedStream::reset);
    }

    @Test
    public void exhaustAndClose() throws IOException {
        TestInputStream countingStream = new TestInputStream(testStream);
        CappedInputStream cappedStream = new CappedInputStream(countingStream, 50);

        cappedStream.exhaustAndClose();

        assertThat(countingStream.getSkipCount()).isEqualTo(50);
        assertThat(countingStream.getReadCount()).isEqualTo(0);
        assertThat(cappedStream.available()).isEqualTo(0);
        assertThatExceptionOfType(IOException.class).isThrownBy(cappedStream::read);
    }

    private byte[] toByteArray(InputStream stream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int c;
        while ((c = stream.read(buffer)) != -1) {
            baos.write(buffer, 0, c);
        }

        return baos.toByteArray();
    }
}