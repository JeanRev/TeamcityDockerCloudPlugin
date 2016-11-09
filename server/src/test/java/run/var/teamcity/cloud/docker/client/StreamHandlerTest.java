package run.var.teamcity.cloud.docker.client;


import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestInputStream;
import run.var.teamcity.cloud.docker.test.TestOutputStream;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;

@Test
public abstract class StreamHandlerTest {

    protected TestInputStream closeHandle = TestInputStream.dummy();
    protected TestInputStream inputStream = TestInputStream.dummy();
    protected TestOutputStream outputStream = TestOutputStream.dummy();

    public void closeHandler() throws IOException {
        StreamHandler handler = createHandler();
        handler.close();
        assertThat(closeHandle.isClosed()).isTrue();
        assertThat(inputStream.isClosed()).isTrue();
        assertThat(outputStream.isClosed()).isTrue();
    }

    public void getOutputStream() {
        assertThat(createHandler().getOutputStream()).isSameAs(outputStream);
    }

    protected abstract StreamHandler createHandler();
}