package run.var.teamcity.cloud.docker.client;

import org.junit.Test;
import run.var.teamcity.cloud.docker.StreamHandler;
import run.var.teamcity.cloud.docker.test.TestInputStream;
import run.var.teamcity.cloud.docker.test.TestOutputStreamFilter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CompositeStreamHandler} test suite.
 */
public class CompositeStreamHandlerTest extends StreamHandlerTest {

    @Test
    public void singleStreamFragment() throws IOException {
        StreamHandler handler = createHandler();
        assertThat(handler.getNextStreamFragment()).isNotNull();
        assertThat(handler.getNextStreamFragment()).isNull();
    }

    @Test
    public void noStdioType() throws IOException {
        //noinspection ConstantConditions
        assertThat(createHandler().getNextStreamFragment().getType()).isNull();
    }

    @Test
    public void closeFragment() throws IOException {
        //noinspection ConstantConditions
        createHandler().getNextStreamFragment().close();
        assertThat(inputStream.isClosed()).isTrue();
    }

    @Override
    protected AbstractStreamHandler createHandler(TestInputStream closeHandle, TestInputStream inputStream, TestOutputStreamFilter outputStream) {
        return new CompositeStreamHandler(closeHandle, inputStream, outputStream);
    }
}
