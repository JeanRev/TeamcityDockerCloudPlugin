package run.var.teamcity.cloud.docker;

import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * {@link AgentHolderInfo} test suite.
 */
public class AgentHolderInfoTest {

    @Test
    public void getId() {
        AgentHolderInfo info = new AgentHolderInfo("42", "42", emptyMap(), "", "", Instant.MIN, false);

        assertThat(info.getId()).isEqualTo("42");
    }

    @Test
    public void getLabels() {
        Map<String, String> labels = mapOf(pair("A", "1"), pair("B", "2"));

        AgentHolderInfo info = new AgentHolderInfo("42", "42", labels, "", "", Instant.MIN, false);

        assertThat(info.getLabels()).isEqualTo(mapOf(pair("A", "1"), pair("B", "2")));

        labels.put("C", "3");

        assertThat(info.getLabels()).isEqualTo(mapOf(pair("A", "1"), pair("B", "2")));

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> info.getLabels().put("C", "3"));
    }

    @Test
    public void getState() {
        AgentHolderInfo info = new AgentHolderInfo("42", "42", emptyMap() , "", "", Instant.MIN, true);

        assertThat(info.isRunning()).isTrue();

        info = new AgentHolderInfo("42", "42", emptyMap(), "", "", Instant.MIN, false);

        assertThat(info.isRunning()).isFalse();

        info = new AgentHolderInfo("42", "42", emptyMap(), "", "", Instant.MIN, false);

        assertThat(info.isRunning()).isFalse();
    }

    @Test
    public void getName() {
        AgentHolderInfo info = new AgentHolderInfo("42", "42", emptyMap(), "", "A", Instant.MIN, false);

        assertThat(info.getName()).isEqualTo("A");
    }

    @Test
    public void getCreationTimestamp() {
        AgentHolderInfo info = new AgentHolderInfo("42", "42", emptyMap(), "", "", Instant.MIN, false);

        assertThat(info.getCreationTimestamp()).isEqualTo(Instant.MIN);
    }

    @Test
    public void invalidConstructorArgument() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AgentHolderInfo(null, "42", emptyMap(), "", "", Instant.MIN, false));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AgentHolderInfo("42", null, null, "", "", Instant.MIN, false));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AgentHolderInfo("42", "42", emptyMap(), null, "", Instant.MIN, false));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AgentHolderInfo("42", "42", emptyMap(), "", null, Instant.MIN, false));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AgentHolderInfo("42", "42", emptyMap(), "", "", null, false));
    }
}