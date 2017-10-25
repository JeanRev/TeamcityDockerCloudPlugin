package run.var.teamcity.cloud.docker.web;

import org.junit.Test;
import run.var.teamcity.cloud.docker.TestDockerCloudSupport;
import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.test.TestHttpSession;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link ContainerTestReference} test suite.
 */
public class ContainerTestReferenceTest {

    @Test
    public void newTestReference() {
        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        TestDockerCloudSupport testCloudSupport = new TestDockerCloudSupport();
        ContainerTestReference testRef = ContainerTestReference.newTestReference(testCloudSupport, TestUtils
                        .TEST_UUID, clientConfig);

        assertThat(testRef.getCloudSupport()).isEqualTo(testCloudSupport);
        assertThat(testRef.getTestUuid()).isEqualTo(TestUtils.TEST_UUID);
        assertThat(testRef.getClientConfig()).isSameAs(clientConfig);
        assertThat(testRef.getContainerId().isPresent()).isFalse();
    }

    @Test
    public void newTestReferenceInvalidInput() {
        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ContainerTestReference.newTestReference(null,TestUtils
                                .TEST_UUID, clientConfig));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ContainerTestReference.newTestReference(new TestDockerCloudSupport(),null,
                        clientConfig));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ContainerTestReference.newTestReference(new TestDockerCloudSupport(), TestUtils
                        .TEST_UUID, null));
    }

    @Test
    public void registerContainer() {
        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        ContainerTestReference newTestRef = testRef.registerContainer("container_id");

        assertThat(testRef.getContainerId().isPresent()).isFalse();
        assertThat(newTestRef.getContainerId().get()).isEqualTo("container_id");
    }

    @Test
    public void registerContainerAlreadyRegistered() {
        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        ContainerTestReference newTestRef = testRef.registerContainer("container_id_1");

        testRef.registerContainer("container_id_2");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                newTestRef.registerContainer("container_id_2"));
    }

    @Test
    public void registerContainerInvalidInput() {
        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> testRef.registerContainer(null));
    }

    @Test
    public void persistAndRetrieveFromHttpSession() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef1 = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        ContainerTestReference testRef2 = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID_2, clientConfig);

        testRef1.persistInHttpSession(httpSession);

        testRef2.persistInHttpSession(httpSession);

        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, TestUtils.TEST_UUID).get())
                .isSameAs(testRef1);
        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, TestUtils.TEST_UUID_2).get())
                .isSameAs(testRef2);

        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, UUID.randomUUID()).isPresent())
                .isFalse();
    }

    @Test
    public void persistWhenHttpSessionInvalidated() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        httpSession.invalidate();

        assertThatExceptionOfType(IllegalStateException.class).
                isThrownBy(() -> testRef.persistInHttpSession(httpSession));
    }

    @Test
    public void attemptToRetrieveUnknownTestReference() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);


        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        testRef.persistInHttpSession(httpSession);

        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, TestUtils.TEST_UUID_2).isPresent())
                .isFalse();
    }

    @Test
    public void retrieveWhenHttpSessionInvalidated() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        testRef.persistInHttpSession(httpSession);

        httpSession.invalidate();

        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, TestUtils.TEST_UUID).isPresent())
                .isFalse();
    }

    @Test
    public void persistInvalidInput() {
        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> testRef.persistInHttpSession(null));
    }


    @Test
    public void retrieveInvalidInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> ContainerTestReference
                .retrieveFromHttpSession(null, TestUtils.TEST_UUID));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> ContainerTestReference
                .retrieveFromHttpSession(new TestHttpSession(), null));
    }

    @Test
    public void clearFromHttpSession() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        testRef.persistInHttpSession(httpSession);

        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, TestUtils.TEST_UUID).get())
                .isSameAs(testRef);

        testRef.clearFromHttpSession(httpSession);

        assertThat(ContainerTestReference.retrieveFromHttpSession(httpSession, TestUtils.TEST_UUID).isPresent())
                .isFalse();

    }

    @Test
    public void clearUnknownTestReferenceFromHttpSession() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        testRef.clearFromHttpSession(httpSession);
    }

    @Test
    public void clearFromInvalidatedHttpSession() {
        TestHttpSession httpSession = new TestHttpSession();

        DockerClientConfig clientConfig = new DockerClientConfig(DockerCloudUtils.DOCKER_DEFAULT_SOCKET_URI,
                DockerCloudUtils.DOCKER_API_TARGET_VERSION);

        ContainerTestReference testRef = ContainerTestReference.newTestReference(new TestDockerCloudSupport(),
                TestUtils.TEST_UUID, clientConfig);

        testRef.persistInHttpSession(httpSession);

        httpSession.invalidate();

        testRef.clearFromHttpSession(httpSession);
    }

}