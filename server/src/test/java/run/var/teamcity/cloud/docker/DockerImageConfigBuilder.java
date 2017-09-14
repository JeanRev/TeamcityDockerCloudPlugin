package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;

public class DockerImageConfigBuilder {

    private final String profileName;
    private final Node containerSpec;
    private boolean pullOnCreate;
    private boolean useOfficialTCAgentImage;

    private DockerRegistryCredentials registryCredentials = DockerRegistryCredentials.ANONYMOUS;

    private DockerImageConfigBuilder(String profileName, Node containerSpec) {
        assert profileName != null && containerSpec != null;
        this.profileName = profileName;
        this.containerSpec = containerSpec;
    }


    public DockerImageConfigBuilder pullOnCreate(boolean pullOnCreate) {
        this.pullOnCreate = pullOnCreate;
        return this;
    }

    public DockerImageConfigBuilder useOfficialTCAgentImage(boolean useOfficialTCAgentImage) {
        this.useOfficialTCAgentImage = useOfficialTCAgentImage;
        return this;
    }

    public DockerImageConfigBuilder registryCredentials(DockerRegistryCredentials registryCredentials) {
        this.registryCredentials = registryCredentials;
        return this;
    }

    public static DockerImageConfigBuilder newBuilder(String profileName, Node containerSpec) {
        return new DockerImageConfigBuilder(profileName, containerSpec);
    }

    public DockerImageConfig build() {
        return new DockerImageConfig(profileName, containerSpec, pullOnCreate, false, useOfficialTCAgentImage,
                                     registryCredentials, 1, null);
    }
}
