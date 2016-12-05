package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.serverSide.InvalidProperty;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

public class DockerImageConfigTest {

    @Test
    public void getters() {
        DockerImageConfig config = new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, 42);

        assertThat(config.getProfileName()).isEqualTo("test");
        assertThat(config.getContainerSpec()).isSameAs(Node.EMPTY_OBJECT);
        assertThat(config.isRmOnExit()).isTrue();
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        assertThat(config.getMaxInstanceCount()).isEqualTo(42);

        config = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, true, 42);

        assertThat(config.isRmOnExit()).isFalse();
        assertThat(config.isUseOfficialTCAgentImage()).isTrue();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidConstructorsInput() {
        new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, 1);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig(null, Node.EMPTY_OBJECT, true, false, 1));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig("test", null, true, false, 1));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, 0));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, -1));
    }

    @Test
    public void fromValidConfigMap() {
        Map<String, String> params = new HashMap<>();

        EditableNode imagesNode = Node.EMPTY_ARRAY.editNode();

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        List<DockerImageConfig> images = DockerImageConfig.processParams(params);

        assertThat(images).isEmpty();

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject());

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        images = DockerImageConfig.processParams(params);

        assertThat(images).hasSize(1);

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject());

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        images = DockerImageConfig.processParams(params);

        assertThat(images).hasSize(2);
    }

    @Test
    public void fromInvalidConfigMap() {
        Map<String, String> params = new HashMap<>();

        assertInvalidProperty(params, DockerCloudUtils.IMAGES_PARAM);

        params.put(DockerCloudUtils.IMAGES_PARAM, Node.EMPTY_OBJECT.toString());

        assertInvalidProperty(params, DockerCloudUtils.IMAGES_PARAM);
    }

    private void assertInvalidProperty(Map<String, String> params, String name) {
        Throwable throwable = catchThrowable(() -> DockerImageConfig.processParams(params));
        assertThat(throwable).isInstanceOf(DockerCloudClientConfigException.class);

        List<InvalidProperty> invalidProperties = ((DockerCloudClientConfigException) throwable).getInvalidProperties();
        assertThat(invalidProperties).hasSize(1);

        InvalidProperty property = invalidProperties.get(0);

        assertThat(property.getPropertyName()).isEqualTo(name);
    }
}