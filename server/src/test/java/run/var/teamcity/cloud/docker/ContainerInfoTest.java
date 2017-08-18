package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static run.var.teamcity.cloud.docker.test.TestUtils.listOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * {@link ContainerInfo} test suite.
 */
public class ContainerInfoTest {

    @Test
    public void getId() {
        ContainerInfo info = new ContainerInfo("42", emptyMap(), ContainerInfo.RUNNING_STATE, emptyList(),
                Instant.MIN);

        assertThat(info.getId()).isEqualTo("42");
    }

    @Test
    public void getLabels() {
        Map<String, String> labels = mapOf(pair("A", "1"), pair("B", "2"));

        ContainerInfo info = new ContainerInfo("42", labels, ContainerInfo.RUNNING_STATE, emptyList(), Instant.MIN);

        assertThat(info.getLabels()).isEqualTo(mapOf(pair("A", "1"), pair("B", "2")));

        labels.put("C", "3");

        assertThat(info.getLabels()).isEqualTo(mapOf(pair("A", "1"), pair("B", "2")));

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
                info.getLabels().put("C", "3"));
    }

    @Test
    public void getState() {
        ContainerInfo info = new ContainerInfo("42", emptyMap(), ContainerInfo.RUNNING_STATE, emptyList(),
                Instant.MIN);

        assertThat(info.getState()).isEqualTo(ContainerInfo.RUNNING_STATE);

        info = new ContainerInfo("42", emptyMap(), "foo", emptyList(),
                Instant.MIN);

        assertThat(info.getState()).isEqualTo("foo");
    }

    @Test
    public void getNames() {

        List<String> names = listOf("A", "B", "C");
        ContainerInfo info = new ContainerInfo("42", emptyMap(), ContainerInfo.RUNNING_STATE,
                names, Instant.MIN);

        assertThat(info.getNames()).isEqualTo(listOf("A", "B", "C"));

        names.add("D");

        assertThat(info.getNames()).isEqualTo(listOf("A", "B", "C"));

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> info.getNames().add("D"));
    }

    @Test
    public void getCreationTimestamp() {

        ContainerInfo info = new ContainerInfo("42", emptyMap(), ContainerInfo.RUNNING_STATE,
                emptyList(), Instant.MIN);

        assertThat(info.getCreationTimestamp()).isEqualTo(Instant.MIN);
    }

    @Test
    public void invalidConstructorArgument() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new ContainerInfo(null, emptyMap(), ContainerInfo.RUNNING_STATE, emptyList(), Instant.MIN));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new ContainerInfo("42", null, ContainerInfo.RUNNING_STATE, emptyList(), Instant.MIN));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new ContainerInfo("42", emptyMap(), null, emptyList(), Instant.MIN));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new ContainerInfo("42", emptyMap(), ContainerInfo.RUNNING_STATE, null, Instant.MIN));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new ContainerInfo("42", emptyMap(), ContainerInfo.RUNNING_STATE, emptyList(), null));
    }
}