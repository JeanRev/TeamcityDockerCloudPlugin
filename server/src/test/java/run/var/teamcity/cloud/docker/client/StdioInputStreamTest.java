package run.var.teamcity.cloud.docker.client;

import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestInputStream;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link StdioInputStream} test suite.
 */
@Test
public class StdioInputStreamTest {

    public void stdioType() {
        StdioType type = StdioType.STDOUT;

        StdioInputStream stream = new StdioInputStream(TestInputStream.empty(), type);

        assertThat(stream.getType()).isSameAs(type);
    }

    public void readingStream() throws IOException {
        StdioInputStream stream = new StdioInputStream(TestInputStream.withUTF8String("hello world"), StdioType.STDOUT);

        assertThat(DockerCloudUtils.readUTF8String(stream)).isEqualTo("hello world");
    }

    public void nullStream() {
        //noinspection ConstantConditions
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new StdioInputStream(null, null));
    }

    public void nullType() {
        StdioInputStream stream = new StdioInputStream(TestInputStream.empty(), null);
        assertThat(stream.getType()).isNull();
    }
}