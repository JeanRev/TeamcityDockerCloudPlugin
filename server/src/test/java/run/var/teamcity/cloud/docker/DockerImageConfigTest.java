package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        assertThat(config.getAgentHolderSpec()).isSameAs(Node.EMPTY_OBJECT);
        assertThat(config.isPullOnCreate()).isTrue();
        assertThat(config.isRmOnExit()).isFalse();
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        assertThat(config.getMaxInstanceCount()).isEqualTo(42);
        assertThat(config.getAgentPoolId()).isEqualTo(Optional.of(111));

        config = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, true, false, DockerRegistryCredentials.ANONYMOUS, 42, null);
        assertThat(config.isPullOnCreate()).isFalse();
        assertThat(config.isRmOnExit()).isTrue();
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        assertThat(config.getAgentPoolId()).isEmpty();

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

        CloudImageParameters imageParameters = new CloudImageParameters();
        imageParameters.setParameter(CloudImageParameters.SOURCE_ID_FIELD, "TestProfile");
        imageParameters.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "42");

        Collection<CloudImageParameters> cloudImageParameters = Collections.singleton(imageParameters);

        params.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                CloudImageParameters.collectionToJson(cloudImageParameters));

        DockerImageConfig imageConfig = DockerImageConfigBuilder.
                newBuilder("TestProfile", Node.EMPTY_OBJECT).
                build();

        TestDockerImageConfigParser imageParser = new TestDockerImageConfigParser();
        imageParser.addConfig(imageConfig);

        params.put(DockerCloudUtils.IMAGES_PARAM, imageParser.getImagesParams().toString());

        List<DockerImageConfig> images = DockerImageConfig.processParams(imageParser, params, cloudImageParameters);
        assertThat(images).containsExactly(imageConfig);

        List<Collection<CloudImageParameters>> imagesParametersList = imageParser.getImagesParametersList();
        assertThat(imagesParametersList).hasSize(1);
        assertThat(imagesParametersList.get(0)).isSameAs(cloudImageParameters);
    }

    @Test
    public void duplicateProfileName() {
        Map<String, String> params = new HashMap<>();

        DockerImageConfig imageConfig1 = DockerImageConfigBuilder.
                newBuilder("TestProfile", Node.EMPTY_OBJECT).
                build();

        DockerImageConfig imageConfig2 = DockerImageConfigBuilder.
                newBuilder("TestProfile2", Node.EMPTY_OBJECT).
                build();

        TestDockerImageConfigParser parser = new TestDockerImageConfigParser();
        parser.addConfig(imageConfig1).addConfig(imageConfig2);

        params.put(DockerCloudUtils.IMAGES_PARAM, parser.getImagesParams().toString());

        // OK
        DockerImageConfig.processParams(parser, params, Collections.emptySet());

        imageConfig2 = DockerImageConfigBuilder.
                newBuilder("TestProfile", Node.EMPTY_OBJECT).
                build();

        parser = new TestDockerImageConfigParser();
        parser.addConfig(imageConfig1).addConfig(imageConfig2);

        params.put(DockerCloudUtils.IMAGES_PARAM, parser.getImagesParams().toString());

        assertInvalidProperty(parser, params, DockerCloudUtils.IMAGES_PARAM);
    }

    @Test
    public void noImageProvided() {
        Map<String, String> params = new HashMap<>();
        TestDockerImageConfigParser parser = new TestDockerImageConfigParser();

        // Empty image definition.
        assertInvalidProperty(parser, params, DockerCloudUtils.IMAGES_PARAM);

        // Empty image list
        params.put(DockerCloudUtils.IMAGES_PARAM, parser.getImagesParams().toString());
        assertInvalidProperty(parser, params, DockerCloudUtils.IMAGES_PARAM);
    }


    private void assertInvalidProperty(TestDockerImageConfigParser parser, Map<String, String> params, String name) {

        Throwable throwable = catchThrowable(() -> DockerImageConfig.processParams(parser,
                params, Collections.emptySet()));
        assertThat(throwable).isInstanceOf(DockerCloudClientConfigException.class);

        List<InvalidProperty> invalidProperties = ((DockerCloudClientConfigException) throwable).getInvalidProperties();
        assertThat(invalidProperties).hasSize(1);

        InvalidProperty property = invalidProperties.get(0);

        assertThat(property.getPropertyName()).isEqualTo(name);
    }
}