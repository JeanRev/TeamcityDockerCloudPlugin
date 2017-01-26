package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudClientParameters;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.test.TestCloudRegistrar;
import run.var.teamcity.cloud.docker.test.TestCloudState;
import run.var.teamcity.cloud.docker.test.TestDockerClientFactory;
import run.var.teamcity.cloud.docker.test.TestPluginDescriptor;
import run.var.teamcity.cloud.docker.test.TestSBuildAgent;
import run.var.teamcity.cloud.docker.test.TestSBuildServer;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class DockerCloudClientFactoryTest {

    public void getters() {
        DockerCloudClientFactory fty = createFactory();

        assertThat(fty.getCloudCode()).hasSize(4);
        assertThat(fty.getDisplayName()).isNotEmpty();
        assertThat(fty.getEditProfileUrl()).isNotEmpty();
        assertThat(fty.getInitialParameterValues()).isNotEmpty();
        assertThat(fty.getPropertiesProcessor()).isNotNull();
    }


    public void createClient() {
        DockerCloudClientFactory fty = createFactory();

        CloudClientParameters params = new CloudClientParameters();

        TestUtils.getSampleDockerConfigParams(false).entrySet().forEach(
                entry -> params.setParameter(entry.getKey(), entry.getValue())
        );

        TestUtils.getSampleImagesConfigParams(false).entrySet().forEach(
                entry -> params.setParameter(entry.getKey(), entry.getValue())
        );


        fty.createNewClient(new TestCloudState(), params);
    }

    public void canBeAgentOfType() {
        DockerCloudClientFactory fty = createFactory();

        assertThat(fty.canBeAgentOfType(new TestSBuildAgent())).isFalse();
        assertThat(fty.canBeAgentOfType(new TestSBuildAgent().environmentVariable(DockerCloudUtils.ENV_CLIENT_ID,
                TestUtils.TEST_UUID.toString())));
    }

    private DockerCloudClientFactory createFactory() {
        return new DockerCloudClientFactory(new TestSBuildServer(), new TestCloudRegistrar(),
                new TestPluginDescriptor(), new TestDockerClientFactory());
    }
}
