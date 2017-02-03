package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestInputStream;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link StdioInputStream} test suite.
 */
public class StdioInputStreamTest {

    @Test
    public void stdioType() {
        StdioType type = StdioType.STDOUT;

        StdioInputStream stream = new StdioInputStream(TestInputStream.empty(), type);

        assertThat(stream.getType()).isSameAs(type);
    }

    @Test
    public void readingStream() throws IOException {
        StdioInputStream stream = new StdioInputStream(TestInputStream.withUTF8String("hello world"), StdioType.STDOUT);

        assertThat(DockerCloudUtils.readUTF8String(stream)).isEqualTo("hello world");
    }

    @Test
    public void nullStream() {
        //noinspection ConstantConditions
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new StdioInputStream(null, null));
    }

    @Test
    public void nullType() {
        StdioInputStream stream = new StdioInputStream(TestInputStream.empty(), null);
        assertThat(stream.getType()).isNull();
    }
}