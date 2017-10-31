package run.var.teamcity.cloud.docker.web;


import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.DockerCloudClientConfig;
import run.var.teamcity.cloud.docker.TestDockerCloudSupport;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.TestAgentHolderTestStatusListener;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.test.TestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitMillis;

/**
 * {@link DefaultAgentHolderTestHandler} test suite.
 */
public class DefaultAgentHolderTestHandlerTest {

    private TestDockerCloudSupport testCloudSupport;

    @Before
    public void setup() {
        this.testCloudSupport = new TestDockerCloudSupport();
    }

    @Test
    public void randomUuid() {
        UUID uuid1 = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig()).getUuid();
        UUID uuid2 = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig()).getUuid();

        assertThat(uuid1).isNotEqualTo(uuid2);
    }

    @Test
    public void newTestInstanceInvalidInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> DefaultAgentHolderTestHandler
                .newTestInstance(null));
    }


    @Test
    public void getAgentHolderId() {
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());
        assertThat(handler.getAgentHolderId()).isEmpty();

        handler.notifyAgentHolderId("agent_id");

        assertThat(handler.getAgentHolderId()).isEqualTo(Optional.of("agent_id"));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> handler.notifyAgentHolderId(null));
    }


    @Test
    public void getTestListener() {
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());
        assertThat(handler.getTestListener()).isEmpty();

        TestAgentHolderTestStatusListener noop = new TestAgentHolderTestStatusListener();
        handler.setListener(noop);

        assertThat(handler.getTestListener()).isEqualTo(Optional.of(noop));
    }

    @Test
    public void lastInteractionInitiallySet() {
        Instant now = Instant.now();
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());

        Instant lastInteraction = handler.getLastInteraction();

        assertThat(Duration.between(now, lastInteraction).toMillis()).isBetween(0L, 250L);
    }

    @Test
    public void notifyLastInteraction() {
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());

        Instant lastInteraction = handler.getLastInteraction();

        waitMillis(100);

        Instant now = Instant.now();

        handler.notifyInteraction();

        Instant lastInteractionAfterWait = handler.getLastInteraction();

        assertThat(Duration.between(now, lastInteractionAfterWait).toMillis()).isBetween(0L, 250L);
        assertThat(lastInteractionAfterWait.isAfter(lastInteraction)).isTrue();
    }

    @Test
    public void notifyAgentHolderStarted() {
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());

        assertThat(handler.getAgentHolderStartTime()).isEmpty();
        assertThat(handler.isLogsAvailable()).isFalse();

        handler.notifyAgentHolderStarted(TestUtils.TEST_INSTANT, false);
    }

    @Test
    public void getDockerClientFacade() {
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());

        assertThat(handler.getDockerClientFacade()).isSameAs(testCloudSupport.getClientFacade());
    }

    @Test
    public void getResources() {
        DefaultAgentHolderTestHandler handler = DefaultAgentHolderTestHandler.newTestInstance(createClientConfig());

        assertThat(handler.getResources()).isSameAs(testCloudSupport.resources());
    }


    private DockerCloudClientConfig createClientConfig() {
        return new DockerCloudClientConfig(testCloudSupport, TestUtils.TEST_UUID,
                new DockerClientConfig(TestDockerClient.TEST_CLIENT_URI, DockerAPIVersion.DEFAULT), false, null);
    }
}