package run.var.teamcity.cloud.docker.client;

import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestInputStream;
import run.var.teamcity.cloud.docker.test.TestOutputStream;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MultiplexedStreamHandler} test suite.
 */
public class MultiplexedStreamHandlerTest extends StreamHandlerTest {

    private final static String STDIN_MSG = "echo hello world!";
    private final static String STDOUT_MSG = "hello world!";
    private final static String STDERR_MSG = "some error message";

    private InputStream multiplexedStream;

    @Before
    public void init() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt((int) StdioType.STDIN.streamType());
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byte[] txt = STDIN_MSG.getBytes(StandardCharsets.UTF_8);
        byteBuffer.putInt(txt.length);
        byteBuffer.put(txt);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt((int) StdioType.STDOUT.streamType());
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        txt = STDOUT_MSG.getBytes(StandardCharsets.UTF_8);
        byteBuffer.putInt(txt.length);
        byteBuffer.put(txt);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt((int) StdioType.STDERR.streamType());
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        txt = STDERR_MSG.getBytes(StandardCharsets.UTF_8);
        byteBuffer.putInt(txt.length);
        byteBuffer.put(txt);
        byteBuffer.rewind();
        byte[] content = new byte[byteBuffer.remaining()];
        byteBuffer.get(content);

        multiplexedStream = new ByteArrayInputStream(content);
    }

    @Test
    public void demultiplexing() throws IOException {
        inputStream = new TestInputStream(multiplexedStream);
        StreamHandler handler = createHandler();
        StdioInputStream streamFragment = handler.getNextStreamFragment();
        assertThat(streamFragment).isNotNull();
        assertThat(streamFragment.getType()).isSameAs(StdioType.STDIN);
        assertThat(DockerCloudUtils.readUTF8String(streamFragment)).isEqualTo(STDIN_MSG);

        streamFragment = handler.getNextStreamFragment();
        assertThat(streamFragment).isNotNull();
        assertThat(streamFragment.getType()).isSameAs(StdioType.STDOUT);
        assertThat(DockerCloudUtils.readUTF8String(streamFragment)).isEqualTo(STDOUT_MSG);

        streamFragment = handler.getNextStreamFragment();
        assertThat(streamFragment).isNotNull();
        assertThat(streamFragment.getType()).isSameAs(StdioType.STDERR);
        assertThat(DockerCloudUtils.readUTF8String(streamFragment)).isEqualTo(STDERR_MSG);
    }

    @Override
    protected StreamHandler createHandler(TestInputStream closeHandle, TestInputStream inputStream, TestOutputStream outputStream) {
        return new MultiplexedStreamHandler(closeHandle, inputStream, outputStream);
    }
}
