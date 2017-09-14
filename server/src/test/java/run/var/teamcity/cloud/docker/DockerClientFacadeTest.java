package run.var.teamcity.cloud.docker;

import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.listOf;

public abstract class DockerClientFacadeTest {

    TestDockerClient dockerClient;

    @Before
    public void init() {
        dockerClient = new TestDockerClient(new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI, DockerAPIVersion
                .DEFAULT), DockerRegistryCredentials.ANONYMOUS);
    }

    @Test
    public void createImageConfigInvalidArguments() {
        DockerClientFacade facade = createFacade(dockerClient);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> facade.createAgent(null));
    }

    @Test
    public void close() {
        DockerClientFacade facade = createFacade(dockerClient);

        assertThat(dockerClient.isClosed()).isFalse();

        facade.close();

        assertThat(dockerClient.isClosed()).isTrue();

        facade.close();
    }

    protected abstract DockerClientFacade createFacade(TestDockerClient dockerClient);

    class ListenerInvocation {
        final String status;
        final String layer;
        final int percent;


        ListenerInvocation(String status, String layer, int percent) {
            this.status = status;
            this.layer = layer;
            this.percent = percent;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ListenerInvocation)) {
                return false;
            }

            ListenerInvocation invocation = (ListenerInvocation) obj;
            return Objects.equals(status, invocation.status) &&
                    Objects.equals(layer, invocation.layer) &&
                    Objects.equals(percent, invocation.percent);
        }

        @Override
        public String toString() {
            return "status=" + status + ", layer=" + layer + ", percent=" + percent;
        }
    }

}
