package run.var.teamcity.cloud.docker;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudImageParameters;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public abstract class AbstractDockerImageConfigParser implements DockerImageConfigParser {

    private final static Logger LOG = DockerCloudUtils.getLogger(SwarmDockerImageConfigParser.class);

    @Nonnull
    @Override
    public DockerImageConfig fromJSon(@Nonnull Node node, @Nonnull Collection<CloudImageParameters> imagesParameters) {
        DockerCloudUtils.requireNonNull(node, "JSON node cannot be null.");
        try {
            Node admin = node.getObject("Administration");
            LOG.info("Loading cloud profile configuration version " + admin.getAsInt("Version") + ".");

            Node agentHolderSpec = retrieveAgentHolderSpec(node);

            Node env = retrieveEnvArray(agentHolderSpec);
            for (Node value : env.getArrayValues()) {
                String envValue = value.getAsString();
                if (envValue.startsWith(DockerCloudUtils.ENV_PREFIX)) {
                    throw new IllegalArgumentException("Variable start with reserved prefix: " + envValue);
                }
            }

            Node labels = retrieveLabelsMap(agentHolderSpec);
            assert labels != null;
            for (String key : labels.getObjectValues().keySet()) {
                if (key.startsWith(DockerCloudUtils.NS_PREFIX)) {
                    throw new IllegalArgumentException("Label key start with reserved prefix: " + key);
                }
            }

            String profileName = admin.getAsString("Profile");
            boolean pullOnCreate = admin.getAsBoolean("PullOnCreate", false);
            boolean deleteOnExit = admin.getAsBoolean("RmOnExit", false);
            boolean useOfficialTCAgentImage = admin.getAsBoolean("UseOfficialTCAgentImage", false);

            Integer agentPoolId = null;
            for (CloudImageParameters imageParameter : imagesParameters) {
                if (profileName.equals(imageParameter.getId())) {
                    agentPoolId = imageParameter.getAgentPoolId();
                    break;
                }
            }

            DockerRegistryCredentials dockerRegistryCredentials =  registryAuthentication(admin);

            return new DockerImageConfig(profileName, agentHolderSpec, pullOnCreate, deleteOnExit,
                    useOfficialTCAgentImage, dockerRegistryCredentials, admin.getAsInt("MaxInstanceCount", -1), agentPoolId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse image JSON definition:\n" + node, e);
        }
    }

    /**
     * Extract Registry user and password required to pull image.
     *
     * @param admin the docker instance for which the container will be created
     * @return authentication details or anonymous
     */
    private DockerRegistryCredentials registryAuthentication(Node admin) {
        String registryUser = admin.getAsString("RegistryUser", null);
        String registryPassword = admin.getAsString("RegistryPassword", null);
        DockerRegistryCredentials dockerRegistryCredentials = DockerRegistryCredentials.ANONYMOUS;
        if (isNotEmpty(registryUser) && isNotEmpty(registryPassword)){
            String decodedPassword = new String(Base64.getDecoder().decode(registryPassword), StandardCharsets.UTF_16BE);
            dockerRegistryCredentials = DockerRegistryCredentials.from(registryUser, decodedPassword);
        }
        return dockerRegistryCredentials;
    }

    @Nonnull
    protected abstract Node retrieveAgentHolderSpec(@Nonnull Node node);

    @Nonnull
    protected abstract Node retrieveEnvArray(@Nonnull Node agentHolderSpec);

    @Nonnull
    protected abstract Node retrieveLabelsMap(@Nonnull Node agentHolderSpec);
}
