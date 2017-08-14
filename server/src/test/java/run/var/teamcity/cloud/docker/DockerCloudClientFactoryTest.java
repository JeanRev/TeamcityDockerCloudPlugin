package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudImageParameters;
import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestCloudRegistrar;
import run.var.teamcity.cloud.docker.test.TestCloudState;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DockerCloudClientFactory} test suite.
 */
public class DockerCloudClientFactoryTest {

    @Test
    public void getters() {
        DockerCloudClientFactory fty = createFactory();

        assertThat(fty.getCloudCode()).hasSize(4);
        assertThat(fty.getDisplayName()).isNotEmpty();
        assertThat(fty.getEditProfileUrl()).isNotEmpty();
        assertThat(fty.getInitialParameterValues()).isNotEmpty();
        assertThat(fty.getPropertiesProcessor()).isNotNull();
    }

    @Test
    public void createClient() {
        DockerCloudClientFactory fty = createFactory();

        CloudClientParameters clientParams = new CloudClientParameters();

        // 1. Populate the client parameters with our own configuration fields.
        TestUtils.getSampleDockerConfigParams(false).forEach(clientParams::setParameter);
        TestUtils.getSampleImagesConfigParams("Test", false).forEach(clientParams::setParameter);

        // 2. Add a set of TC cloud image parameters in the cloud client parameter map.
        CloudImageParameters imageParams = new CloudImageParameters();
        imageParams.setParameter(CloudImageParameters.SOURCE_ID_FIELD, "Test");
        imageParams.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "0");
        clientParams.setParameter(CloudImageParameters.SOURCE_IMAGES_JSON,
                CloudImageParameters.collectionToJson(Collections.singleton(imageParams)));

        // 3. Register another set of cloud image parameters, as Java instance, in the cloud client parameters.
        imageParams.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "42");
        clientParams.setCloudImages(Collections.singleton(imageParams));

        CloudClientEx cloudclient = fty.createNewClient(new TestCloudState(), clientParams);

        Collection<? extends CloudImage> images = cloudclient.getImages();
        // 4. Checks that the cloud image parameters instance from 3) have been imported in the image configuration,
        // overriding the parameters from the client map.
        assertThat(images).hasSize(1).first().matches(img -> img.getAgentPoolId() != null && img.getAgentPoolId()== 42);
    }

    @Test
    public void canBeAgentOfType() {
        DockerCloudClientFactory fty = createFactory();

        assertThat(fty.canBeAgentOfType(new TestSBuildAgent())).isFalse();
        assertThat(fty.canBeAgentOfType(new TestSBuildAgent().environmentVariable(DockerCloudUtils.ENV_CLIENT_ID,
                TestUtils.TEST_UUID.toString())));
    }

    private DockerCloudClientFactory createFactory() {
        return new DockerCloudClientFactory(new TestSBuildServer(), new TestCloudRegistrar(),
                new TestPluginDescriptor(), new TestDockerClientFacadeFactory());
    }
}
