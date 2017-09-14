package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import static org.assertj.core.api.Assertions.*;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.immutableMapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.mapOf;
import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.pair;

/**
 * {@link CreateAgentParameters} test suite.
 */
public class CreateAgentParametersTest {

    @Test
    public void getAgentHolderSpec() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> CreateAgentParameters.from(null));
        Node spec = Node.EMPTY_OBJECT.editNode().put("foo", "bar").saveNode();

        assertThat(CreateAgentParameters.from(spec).getAgentHolderSpec()).isSameAs(spec);
    }

    @Test
    public void getLabels() {
        CreateAgentParameters params = CreateAgentParameters.from(Node.EMPTY_OBJECT);

        assertThat(params.getLabels()).isEmpty();

        params.label("key1", "value1").label("key2", "value2");

        assertThat(params.getLabels()).isEqualTo(mapOf(pair("key1", "value1"), pair("key2", "value2")));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.label("foo", null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.label(null, "bar"));
    }

    @Test
    public void getEnv() {
        CreateAgentParameters params = CreateAgentParameters.from(Node.EMPTY_OBJECT);

        assertThat(params.getEnv()).isEmpty();

        params.env("key1", "value1").env("key2", "value2");

        assertThat(params.getEnv()).isEqualTo(mapOf(pair("key1", "value1"), pair("key2", "value2")));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.env("foo", null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.env(null, "bar"));
    }

    @Test
    public void getImageName() {
        CreateAgentParameters params = CreateAgentParameters.from(Node.EMPTY_OBJECT);

        assertThat(params.getImageName()).isEmpty();

        assertThat(params.imageName("test_image_name").getImageName().get()).isEqualTo("test_image_name");

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.imageName(null));
    }

    @Test
    public void getPullStrategy() {
        CreateAgentParameters params = CreateAgentParameters.from(Node.EMPTY_OBJECT);

        assertThat(params.getPullStrategy()).isSameAs(PullStrategy.NO_PULL);

        assertThat(params.pullStrategy(PullStrategy.PULL).getPullStrategy()).isEqualTo(PullStrategy.PULL);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.pullStrategy(null));
    }

    @Test
    public void getRegistryCredentials() {
        CreateAgentParameters params = CreateAgentParameters.from(Node.EMPTY_OBJECT);

        assertThat(params.getRegistryCredentials()).isEqualTo(DockerRegistryCredentials.ANONYMOUS);

        DockerRegistryCredentials credentials = DockerRegistryCredentials.from("foo", "bar");

        assertThat(params.registryCredentials(credentials).getRegistryCredentials()).isEqualTo(credentials);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> params.pullStrategy(null));
    }

    @Test
    public void getPullStatusListener() {
        CreateAgentParameters params = CreateAgentParameters.from(Node.EMPTY_OBJECT);

        assertThat(params.getPullStatusListener()).isEqualTo(PullStatusListener.NOOP);

        PullStatusListener listener = (status, layer, percent) -> {};

        assertThat(params.pullStatusListener(listener).getPullStatusListener()).isEqualTo(listener);
    }

    @Test
    public void fromImageConfigMustCopyRegistryCredendtials() {
        DockerImageConfigBuilder builder = DockerImageConfigBuilder.newBuilder("test", Node.EMPTY_OBJECT);

        DockerRegistryCredentials credentials = DockerRegistryCredentials.from("foo", "bar");

        builder.registryCredentials(credentials);

        CreateAgentParameters params = CreateAgentParameters.fromImageConfig(builder.build(), () -> "", false);

        assertThat(params.getRegistryCredentials()).isEqualTo(credentials);
    }

    @Test
    public void fromImageConfigMustDefinePullStrategy() {
        DockerImageConfigBuilder builder = DockerImageConfigBuilder.newBuilder("test", Node.EMPTY_OBJECT);

        builder.pullOnCreate(false);

        CreateAgentParameters params = CreateAgentParameters.fromImageConfig(builder.build(), () -> "", false);

        assertThat(params.getPullStrategy()).isEqualTo(PullStrategy.NO_PULL);

        params = CreateAgentParameters.fromImageConfig(builder.build(), () -> "", true);

        assertThat(params.getPullStrategy()).isEqualTo(PullStrategy.NO_PULL);

        builder.pullOnCreate(true);

        params = CreateAgentParameters.fromImageConfig(builder.build(), () -> "", false);

        assertThat(params.getPullStrategy()).isEqualTo(PullStrategy.PULL);

        params = CreateAgentParameters.fromImageConfig(builder.build(), () -> "", true);

        assertThat(params.getPullStrategy()).isEqualTo(PullStrategy.PULL_IGNORE_FAILURE);
    }

    @Test
    public void fromImageConfigMustResolveImage() {
        DockerImageConfigBuilder builder = DockerImageConfigBuilder.newBuilder("test", Node.EMPTY_OBJECT);

        builder.useOfficialTCAgentImage(false);

        CreateAgentParameters params = CreateAgentParameters.fromImageConfig(builder.build(),
                () -> "resolved_image:1.0", false);

        assertThat(params.getImageName()).isEmpty();

        builder.useOfficialTCAgentImage(true);

        params = CreateAgentParameters.fromImageConfig(builder.build(),
                () -> "resolved_image:1.0", false);

        assertThat(params.getImageName().get()).isEqualTo("resolved_image:1.0");

    }
}