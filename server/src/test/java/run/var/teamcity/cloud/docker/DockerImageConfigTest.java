package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class DockerImageConfigTest {

    @Test
    public void getters() {
        DockerImageConfig config = new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, 42, 111);

        assertThat(config.getProfileName()).isEqualTo("test");
        assertThat(config.getContainerSpec()).isSameAs(Node.EMPTY_OBJECT);
        assertThat(config.isRmOnExit()).isTrue();
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        assertThat(config.getMaxInstanceCount()).isEqualTo(42);
        assertThat(config.getAgentPoolId()).isEqualTo(111);

        config = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, true, 42, null);

        assertThat(config.isRmOnExit()).isFalse();
        assertThat(config.isUseOfficialTCAgentImage()).isTrue();
        assertThat(config.getAgentPoolId()).isNull();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidConstructorsInput() {
        new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, 1, 111);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig(null, Node.EMPTY_OBJECT, true, false, 1, 111));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig("test", null, true, false, 1, 111));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, 0, 111));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true, false, -1, 111));
    }

    @Test
    public void fromValidConfigMap() {
        Map<String, String> params = new HashMap<>();

        EditableNode imagesNode = Node.EMPTY_ARRAY.editNode();

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        List<DockerImageConfig> images = DockerImageConfig.processParams(params);

        assertThat(images).isEmpty();

        CloudImageParameters imageParameters = new CloudImageParameters();
        imageParameters.setParameter(CloudImageParameters.SOURCE_ID_FIELD, "TestProfile");
        imageParameters.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "42");

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(), "TestProfile");

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());
        params.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                CloudImageParameters.collectionToJson(Collections.singleton(imageParameters)));

        images = DockerImageConfig.processParams(params);

        assertThat(images).hasSize(1);

        DockerImageConfig imageConfig = images.get(0);
        assertThat(imageConfig.getProfileName()).isEqualTo("TestProfile");
        assertThat(imageConfig.getAgentPoolId()).isEqualTo(42);

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(), "TestProfile2");

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        images = DockerImageConfig.processParams(params);
        assertThat(images).hasSize(2);

        imageConfig = images.get(1);
        assertThat(imageConfig.getProfileName()).isEqualTo("TestProfile2");
        assertThat(imageConfig.getAgentPoolId()).isNull();

    }

    @Test
    public void fromInvalidConfigMap() {
        Map<String, String> params = new HashMap<>();

        // Empty list of images.
        assertInvalidProperty(params, DockerCloudUtils.IMAGES_PARAM);

        // Empty image definition.
        params.put(DockerCloudUtils.IMAGES_PARAM, Node.EMPTY_OBJECT.toString());

        EditableNode imagesNode = Node.EMPTY_ARRAY.editNode();

        // Duplicate profile name.
        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(),"TestProfile");
        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());
        DockerImageConfig.processParams(params);

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(),"TestProfile");

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());
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