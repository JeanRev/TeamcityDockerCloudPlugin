package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClientConfig;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.test.TestDockerClient;
import run.var.teamcity.cloud.docker.util.Resources;

import javax.annotation.Nonnull;
import java.util.Optional;

public class TestDockerCloudSupport implements DockerCloudSupport {

    public final static String CODE = "TEST";

    private final TestDockerClientFacade clientFacade = new TestDockerClientFacade();
    private final TestDockerImageConfigParser imageParser = new TestDockerImageConfigParser();

    private final TestResourceBundle resourceBundle = new TestResourceBundle(true);
    private final Resources resources = new Resources(resourceBundle);
    private DockerClientFacade facadeWrapper;
    private DockerClientException facadeCreationFailure;

    @Nonnull
    @Override
    public String name() {
        return "Test";
    }

    @Nonnull
    @Override
    public String code() {
        return CODE;
    }

    @Nonnull
    @Override
    public Resources resources() {
        return resources;
    }

    @Nonnull
    @Override
    public DockerClientFacade createClientFacade(DockerClientConfig dockerClientConfig) {
        if (!dockerClientConfig.getInstanceURI().equals(TestDockerClient.TEST_CLIENT_URI)) {
            throw new IllegalArgumentException("Unsupported URI: " + dockerClientConfig.getInstanceURI());
        }
        if (facadeCreationFailure != null) {
            throw facadeCreationFailure;
        }

        return facadeWrapper != null ? facadeWrapper : clientFacade;
    }

    @Nonnull
    @Override
    public DockerImageConfigParser createImageConfigParser() {
        return imageParser;
    }


    public void setFacadeCreationFailure(DockerClientException facadeCreationFailure) {
        this.facadeCreationFailure = facadeCreationFailure;
    }

    public TestDockerImageConfigParser getImageParser() {
        return imageParser;
    }

    public TestDockerClientFacade getClientFacade() {
        return clientFacade;
    }

    public void setFacadeWrapper(DockerClientFacade facadeWrapper) {
        this.facadeWrapper = facadeWrapper;
    }
}
