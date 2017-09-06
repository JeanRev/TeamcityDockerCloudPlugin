package run.var.teamcity.cloud.docker.client.npipe;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NPipeSocketAddress} test suite.
 */
public class NPipeSocketAddressTest {

    @Test
    public void illegalConstructorArg() {
        Arrays.asList(
                "C:\\docker_engine",
                "\\tmp\\docker_engine",
                "\\docker_engine",
                "\\\\host\\share\\docker_engine",
                "\\.\\pipe\\docker_engine",
                "\\\\.\\pipe\\docker_engine\\"
        ).forEach(location -> assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> NPipeSocketAddress.fromPath(Paths.get(location)))
        );
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> NPipeSocketAddress.fromPath(null));
    }

    @Test
    public void legalConstructorArg() {
        NPipeSocketAddress.fromPath(Paths.get("\\\\.\\pipe\\docker_engine"));
        NPipeSocketAddress.fromPath(Paths.get("\\\\.\\pipe\\docker_engine\\subdir"));
        NPipeSocketAddress.fromPath(Paths.get("\\\\.\\pipe\\docker_engine\\subdir1\\subdir2"));
    }

    @Test
    public void getter() {
        Path path = Paths.get("\\\\.\\pipe\\docker_engine");
        NPipeSocketAddress addr = NPipeSocketAddress.fromPath(path);
        assertThat(addr.getPipe()).isEqualTo(path);
    }
}