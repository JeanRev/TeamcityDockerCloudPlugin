package run.var.teamcity.cloud.docker.client;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DockerRegistryCredentials} test suite.
 */
public class DockerRegistryCredentialsTest {

    @Test
    public void getters() {
        DockerRegistryCredentials credentials = DockerRegistryCredentials.from("usr", "pwd");

        assertThat(credentials.getUsername()).isEqualTo("usr");
        assertThat(credentials.getPassword()).isEqualTo("pwd");

        credentials = DockerRegistryCredentials.from("usr", "");

        assertThat(credentials.getPassword()).isEqualTo("");
    }

    @Test
    public void anonymousFlag() {
        DockerRegistryCredentials credentials = DockerRegistryCredentials.from("usr", "pwd");

        assertThat(credentials.isAnonymous()).isFalse();
        assertThat(DockerRegistryCredentials.ANONYMOUS.isAnonymous()).isTrue();
    }

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> DockerRegistryCredentials.from(null, "pwd"));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> DockerRegistryCredentials.from("usr", null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> DockerRegistryCredentials.from("", "pwd"));
    }

    @Test
    public void usingGettersWithAnonymousLogin() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(
                DockerRegistryCredentials.ANONYMOUS::getUsername);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(
                DockerRegistryCredentials.ANONYMOUS::getPassword);
    }
}