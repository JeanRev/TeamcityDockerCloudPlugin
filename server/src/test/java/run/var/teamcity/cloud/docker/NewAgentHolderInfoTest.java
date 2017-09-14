package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link NewAgentHolderInfo} test suite.
 */
public class NewAgentHolderInfoTest {

    @Test
    public void invalidConstructorInput() {
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NewAgentHolderInfo(null, "", "", Collections.emptyList()));
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NewAgentHolderInfo(TestUtils.createRandomSha256(), null, "",
                                                        Collections.emptyList()));
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NewAgentHolderInfo(TestUtils.createRandomSha256(), "", null,Collections.emptyList()));
        assertThatExceptionOfType(NullPointerException.class).
                isThrownBy(() -> new NewAgentHolderInfo(TestUtils.createRandomSha256(), "", "",null));
    }

    @Test
    public void getId() {
        String id = TestUtils.createRandomSha256();
        NewAgentHolderInfo containerInfo = new NewAgentHolderInfo(id, "", "", Collections.emptyList());
        assertThat(containerInfo.getId()).isEqualTo(id);
    }

    @Test
    public void getName() {
        NewAgentHolderInfo containerInfo = new NewAgentHolderInfo("", "test_image_name", "",
                                                                  Collections.emptyList());
        assertThat(containerInfo.getName()).isEqualTo("test_image_name");
    }

    @Test
    public void getResolvedImage() {
        String id = TestUtils.createRandomSha256();
        NewAgentHolderInfo containerInfo = new NewAgentHolderInfo(id, "", "test_resolved_image",
                                                                  Collections.emptyList());
        assertThat(containerInfo.getResolvedImage()).isEqualTo(containerInfo.getResolvedImage());
    }

    @Test
    public void getWarnings() {
        assertThat(new NewAgentHolderInfo(TestUtils.createRandomSha256(), "", "", Collections.emptyList())
                           .getWarnings()).
                isEmpty();
        List<String> warnings = Arrays.asList("warning1", "warning2", "warning3");
        assertThat(new NewAgentHolderInfo(TestUtils.createRandomSha256(), "", "", warnings).getWarnings()).
                containsExactlyElementsOf(warnings).
                isNotSameAs(warnings);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> warnings.add("warning4"));
    }
}