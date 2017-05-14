package run.var.teamcity.cloud.docker.util;

import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerAPIVersion;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DockerAPIVersion} test suite.
 */
public class DockerAPIVersionTest {

    @Test
    public void parsing() {
        assertThat(v("").getVersionString()).isEqualTo("");
        assertThat(v("1.0").getVersionString()).isEqualTo("1.0");
        assertThat(v("abc").getVersionString()).isEqualTo("abc");
        assertThat(v("...-10...").getVersionString()).isEqualTo("...-10...");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void noVersionStringForDefaultVersion() {
        DockerAPIVersion.DEFAULT.getVersionString();
    }

    @Test
    public void defaultVersionFlag() {
        assertThat(DockerAPIVersion.DEFAULT.isDefaultVersion()).isTrue();
        assertThat(v("1.0").isDefaultVersion()).isFalse();
        assertThat(v("").isDefaultVersion()).isFalse();
    }

    @Test
    public void nullInput() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> v(null));
    }

    @Test
    public void sorting() {
        List<DockerAPIVersion> actual = Arrays.asList(
                v("3.0"),
                v("2.5"),
                v("3.0"),
                v("3.0.0"),
                DockerAPIVersion.DEFAULT,
                v("2.05"),
                v("2.00"),
                v("3.abc.0"),
                v("2.0.1"),
                v("2.0")
        );
        List<DockerAPIVersion> expected = Arrays.asList(
                DockerAPIVersion.DEFAULT,
                v("2.00"),
                v("2.0"),
                v("2.0.1"),
                v("2.5"),
                v("2.05"),
                v("3.0"),
                v("3.0"),
                v("3.0.0"),
                v("3.abc.0")
        );

        Collections.sort(actual);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void isInRange() {
        assertThat(v("2.5").isInRange(v("2.0"), v("3.0"))).isTrue();
        assertThat(v("2.5").isInRange(v("2.5"), v("3.0"))).isTrue();
        assertThat(v("2.5").isInRange(v("2.0"), v("2.5"))).isTrue();
        assertThat(v("2.5").isInRange(v("2.5"), v("2.5"))).isTrue();

        assertThat(v("2.5").isInRange(v("2.0"), v("2.4"))).isFalse();
    }

    private DockerAPIVersion v(String version) {
        return DockerAPIVersion.parse(version);
    }
}
