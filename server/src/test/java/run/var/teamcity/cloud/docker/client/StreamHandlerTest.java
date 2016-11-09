package run.var.teamcity.cloud.docker.client;


import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestInputStream;
import run.var.teamcity.cloud.docker.test.TestOutputStream;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Test
public abstract class StreamHandlerTest {

    protected TestInputStream closeHandle = TestInputStream.empty();
    protected TestInputStream inputStream = TestInputStream.empty();
    protected TestOutputStream outputStream = TestOutputStream.dummy();

    public void closeHandler() throws IOException {
        StreamHandler handler = createHandler();
        handler.close();
        assertThat(closeHandle.isClosed()).isTrue();
        assertThat(inputStream.isClosed()).isTrue();
        assertThat(outputStream.isClosed()).isTrue();
    }

    public void nullArguments() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> createHandler(null, inputStream,
                outputStream));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> createHandler(closeHandle, null,
                outputStream));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> createHandler(closeHandle, inputStream,
                null));
    }

    public void getOutputStream() {
        assertThat(createHandler().getOutputStream()).isSameAs(outputStream);
    }

    protected StreamHandler createHandler() {
        return createHandler(closeHandle, inputStream, outputStream);
    }
    protected abstract StreamHandler createHandler(TestInputStream closeHandle, TestInputStream inputStream,
                                                   TestOutputStream outputStream);
}