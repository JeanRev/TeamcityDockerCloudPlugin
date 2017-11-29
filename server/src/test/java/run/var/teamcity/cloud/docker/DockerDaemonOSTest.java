package run.var.teamcity.cloud.docker;

import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link DockerDaemonOS} test suite.
 */
public class DockerDaemonOSTest {

    @Test
    public void invalidInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                DockerDaemonOS.fromString(null));
    }

    @Test
    public void translate() {
        for (DockerDaemonOS os : DockerDaemonOS.values()) {
            assertThat(DockerDaemonOS.fromString(os.getAttribute())).isEqualTo(Optional.of(os));
        }
    }

    @Test
    public void unknownOS() {
        assertThat(DockerDaemonOS.fromString("NotARealOS")).isEmpty();
    }

}