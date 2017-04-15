package run.var.teamcity.cloud.docker.client;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DockerClientCredentials} test suite.
 */
public class DockerClientCredentialsTest {

    @Test
    public void getters() {
        DockerClientCredentials credentials = DockerClientCredentials.from("usr", "pwd");

        assertThat(credentials.getUsername()).isEqualTo("usr");
        assertThat(credentials.getPassword()).isEqualTo("pwd");

        credentials = DockerClientCredentials.from("usr", "");

        assertThat(credentials.getPassword()).isEqualTo("");
    }

    @Test
    public void anonymousFlag() {
        DockerClientCredentials credentials = DockerClientCredentials.from("usr", "pwd");

        assertThat(credentials.isAnonymous()).isFalse();
        assertThat(DockerClientCredentials.ANONYMOUS.isAnonymous()).isTrue();
    }

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> DockerClientCredentials.from(null, "pwd"));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> DockerClientCredentials.from("usr", null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> DockerClientCredentials.from("", "pwd"));
    }

    @Test
    public void usingGettersWithAnonymousLogin() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(
                DockerClientCredentials.ANONYMOUS::getUsername);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(
                DockerClientCredentials.ANONYMOUS::getPassword);
    }
}