package run.var.teamcity.cloud.docker.client.npipe;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.tempFile;

/**
 * {@link DefaultPipeChannel} test suite.
 */
public class DefaultPipeChannelTest {

    @Test
    public void invalidPath() throws IOException {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DefaultPipeChannel.open(null));
    }

    @Test(timeout = 10000)
    public void isOpen() throws IOException {
        PipeChannel channel = DefaultPipeChannel.open(tempFile());

        assertThat(channel.isOpen()).isTrue().as("channel is initially open");

        channel.close();

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    public void close() throws IOException {
        PipeChannel channel = DefaultPipeChannel.open(tempFile());

        channel.close();

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> channel.write(ByteBuffer.allocate(1)).get())
                .withCauseInstanceOf(ClosedChannelException.class);

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> channel.read(ByteBuffer.allocate(1)).get())
                .withCauseInstanceOf(ClosedChannelException.class);
        channel.close(); // Idempotent.
    }

    @Test(timeout = 10000)
    public void read() throws Exception {
        Path tmpFile = tempFile();
        Files.write(tmpFile, new byte[]{1, 2, 3});
        PipeChannel channel = DefaultPipeChannel.open(tmpFile);
        ByteBuffer bb = ByteBuffer.allocate(5);
        int n = channel.read(bb).get();

        assertThat(n).isEqualTo(3);

        assertThat(bb.array()).as("simple read operation").containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 0, (byte) 0);

        bb = ByteBuffer.allocate(5);
        bb.limit(2);
        // Note: we test here implicitly that the targeted file is always read from position 0.
        n = channel.read(bb).get();

        assertThat(n).isEqualTo(2);

        assertThat(bb.array()).as("read with limit set").containsExactly((byte) 1, (byte) 2, (byte) 0, (byte) 0, (byte) 0);

        bb = ByteBuffer.allocate(5);
        bb.position(2);
        bb.limit(4);

        n = channel.read(bb).get();

        assertThat(n).isEqualTo(2);

        assertThat(bb.array()).containsExactly((byte) 0, (byte) 0, (byte) 1, (byte) 2, (byte) 0)
                .as("read with position and limit set");
    }

    @Test(timeout = 10000)
    public void write() throws Exception {
        Path tmpFile = tempFile();

        PipeChannel channel = DefaultPipeChannel.open(tmpFile);
        FileChannel ctrlChannel = FileChannel.open(tmpFile, StandardOpenOption.READ, StandardOpenOption.WRITE);

        ByteBuffer bb = ByteBuffer.wrap(new byte[]{1, 2, 3});

        int n = channel.write(bb).get();
        assertThat(n).isEqualTo(3);

        ByteBuffer resultBuffer = ByteBuffer.allocate(5);
        ctrlChannel.read(resultBuffer);
        assertThat(resultBuffer.limit(n)).as("simple write operation").isEqualTo(bb);

        channel.write(ByteBuffer.wrap(new byte[]{4})).get();
        resultBuffer = ByteBuffer.allocate(5);
        n = ctrlChannel.read(resultBuffer, 0);
        assertThat(n).isEqualTo(3);
        assertThat(resultBuffer.array()).as("writing must always occurs at position 0")
                .containsExactly((byte) 4, (byte) 2, (byte) 3, (byte) 0, (byte) 0);

        ctrlChannel.truncate(0);
        bb.position(1);
        n = channel.write(bb).get();
        assertThat(n).isEqualTo(2);
        resultBuffer = ByteBuffer.allocate(5);
        ctrlChannel.read(resultBuffer, 0);
        assertThat(resultBuffer.array()).as("writing with buffer position set")
                .containsExactly((byte) 2, (byte) 3, (byte) 0, (byte) 0, (byte) 0);

        ctrlChannel.truncate(0);

        bb.position(1);
        bb.limit(2);
        n = channel.write(bb).get();
        assertThat(n).isEqualTo(1);
        resultBuffer = ByteBuffer.allocate(5);
        ctrlChannel.read(resultBuffer, 0);
        assertThat(resultBuffer.array()).as("writing with buffer position and limit set")
                .containsExactly((byte) 2, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }
}