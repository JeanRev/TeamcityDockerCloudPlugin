package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * {@link DockerImageConfig} test suite.
 */
public class DockerImageConfigTest {

    @Test
    public void getters() {
        DockerImageConfig config = new DockerImageConfig("test", Node.EMPTY_OBJECT,  true,false, false, DockerRegistryCredentials.ANONYMOUS, 42, 111);

        assertThat(config.getProfileName()).isEqualTo("test");
        assertThat(config.getContainerSpec()).isSameAs(Node.EMPTY_OBJECT);
        assertThat(config.isPullOnCreate()).isTrue();
        assertThat(config.isRmOnExit()).isFalse();
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        assertThat(config.getMaxInstanceCount()).isEqualTo(42);
        assertThat(config.getAgentPoolId()).isEqualTo(111);

        config = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, true, false, DockerRegistryCredentials.ANONYMOUS, 42, null);
        assertThat(config.isPullOnCreate()).isFalse();
        assertThat(config.isRmOnExit()).isTrue();
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        assertThat(config.getAgentPoolId()).isNull();

        config = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, false, true, DockerRegistryCredentials.ANONYMOUS, 42, null);
        assertThat(config.isPullOnCreate()).isFalse();
        assertThat(config.isRmOnExit()).isFalse();
        assertThat(config.isUseOfficialTCAgentImage()).isTrue();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void invalidConstructorsInput() {
        new DockerImageConfig("test", Node.EMPTY_OBJECT, true,true, false, DockerRegistryCredentials.ANONYMOUS, 1, 111);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig(null, Node.EMPTY_OBJECT, true,true, false, DockerRegistryCredentials.ANONYMOUS, 1, 111));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true,true, false, null, 1, 111));

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DockerImageConfig("test", null, true,true, false, DockerRegistryCredentials.ANONYMOUS, 1, 111));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true,true, false, DockerRegistryCredentials.ANONYMOUS, 0, 111));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true,true, false, DockerRegistryCredentials.ANONYMOUS, -1, 111));
    }

    @Test
    public void fromValidConfigMap() {
        Map<String, String> params = new HashMap<>();

        EditableNode imagesNode = Node.EMPTY_ARRAY.editNode();

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        CloudImageParameters imageParameters = new CloudImageParameters();
        imageParameters.setParameter(CloudImageParameters.SOURCE_ID_FIELD, "TestProfile");
        imageParameters.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "42");

        EditableNode imageNode = imagesNode.addObject();
        imageNode.getOrCreateObject("Administration").
                put("Version", 42).
                put("Profile", "TestProfile").
                put("RmOnExit", true).
                put("MaxInstanceCount", 2).
                put("UseOfficialTCAgentImage", false);

        imageNode.getOrCreateObject("Container").put("Image", "test-image");

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());
        params.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                CloudImageParameters.collectionToJson(Collections.singleton(imageParameters)));

        List<DockerImageConfig> images = DockerImageConfig.processParams(params);

        assertThat(images).hasSize(1);

        DockerImageConfig imageConfig = images.get(0);
        assertThat(imageConfig.getProfileName()).isEqualTo("TestProfile");
        assertThat(imageConfig.getAgentPoolId()).isEqualTo(42);
        assertThat(imageConfig.isUseOfficialTCAgentImage()).isFalse();
        assertThat(imageConfig.isPullOnCreate()).isTrue(); // Default value

        imageNode = imagesNode.addObject();
        imageNode.getOrCreateObject("Administration").
                put("Version", 42).
                put("Profile", "TestProfile2").
                put("RmOnExit", true).
                put("MaxInstanceCount", 2).
                put("UseOfficialTCAgentImage", true).
                put("PullOnCreate", false);

        imageNode.getOrCreateObject("Container").put("Image", "test-image");

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        images = DockerImageConfig.processParams(params);
        assertThat(images).hasSize(2);

        imageConfig = images.get(1);
        assertThat(imageConfig.getProfileName()).isEqualTo("TestProfile2");
        assertThat(imageConfig.isUseOfficialTCAgentImage()).isTrue();
        assertThat(imageConfig.isPullOnCreate()).isFalse();
        assertThat(imageConfig.getAgentPoolId()).isNull();

    }

    @Test
    public void duplicateProfileName() {
        Map<String, String> params = new HashMap<>();
        EditableNode imagesNode = Node.EMPTY_ARRAY.editNode();

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(),"TestProfile1");
        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(),"TestProfile2");
        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        // OK
        DockerImageConfig.processParams(params);

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(),"TestProfile2");
        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        assertInvalidProperty(params, DockerCloudUtils.IMAGES_PARAM);
    }

    @Test
    public void noImageProvided() {
        Map<String, String> params = new HashMap<>();

        // Empty image definition.
        assertInvalidProperty(params, DockerCloudUtils.IMAGES_PARAM);

        EditableNode imagesNode = Node.EMPTY_ARRAY.editNode();

        // Empty image list
        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        assertInvalidProperty(params, DockerCloudUtils.IMAGES_PARAM);

        TestUtils.getSampleImageConfigSpec(imagesNode.addObject(),"TestProfile");

        params.put(DockerCloudUtils.IMAGES_PARAM, imagesNode.toString());

        // OK
        DockerImageConfig.processParams(params);
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