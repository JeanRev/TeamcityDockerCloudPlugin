package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link NewContainerInfo} test suite.
 */
public class NewContainerInfoTest {

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NewContainerInfo(null, Collections.emptyList()));
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NewContainerInfo(TestUtils.createRandomSha256(), null));
    }

    @Test
    public void getId() {
        String id = TestUtils.createRandomSha256();
        NewContainerInfo containerInfo = new NewContainerInfo(id, Collections.emptyList());
        assertThat(containerInfo.getId()).isEqualTo(id);
    }

    @Test
    public void getWarnings() {
        assertThat(new NewContainerInfo(TestUtils.createRandomSha256(), Collections.emptyList()).getWarnings()).
                isEmpty();
        List<String> warnings = Arrays.asList("warning1", "warning2", "warning3");
        assertThat(new NewContainerInfo(TestUtils.createRandomSha256(), warnings).getWarnings()).
                containsExactlyElementsOf(warnings).
                isNotSameAs(warnings);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> warnings.add("warning4"));
    }
}