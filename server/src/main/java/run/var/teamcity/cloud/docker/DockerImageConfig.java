package run.var.teamcity.cloud.docker;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * A {@link DockerImage} configuration.
 */
public class DockerImageConfig {

    private static final Logger LOG = DockerCloudUtils.getLogger(DockerImageConfig.class);

    private final String profileName;
    private final Node containerSpec;
    private final boolean pullOnCreate;
    private final boolean rmOnExit;
    private final boolean useOfficialTCAgentImage;
    private final int maxInstanceCount;
    private final Integer agentPoolId;
    private final DockerRegistryCredentials registryCredentials;

    public DockerImageConfig(@Nonnull String profileName, @Nonnull Node containerSpec, boolean pullOnCreate,
                             boolean rmOnExit, boolean useOfficialTCAgentImage,
                             @Nonnull DockerRegistryCredentials registryCredentials, int maxInstanceCount,
                             @Nullable Integer agentPoolId) {
        DockerCloudUtils.requireNonNull(profileName, "Profile name cannot be null.");
        DockerCloudUtils.requireNonNull(registryCredentials, "Registry credentials cannot be null.");
        DockerCloudUtils.requireNonNull(containerSpec, "Container specification cannot be null.");
        if (maxInstanceCount < 1) {
            throw new IllegalArgumentException("At least 1 instance must be allowed.");
        }
        this.profileName = profileName;
        this.containerSpec = containerSpec;
        this.pullOnCreate = pullOnCreate;
        this.rmOnExit = rmOnExit;
        this.useOfficialTCAgentImage = useOfficialTCAgentImage;
        this.maxInstanceCount = maxInstanceCount;
        this.agentPoolId = agentPoolId;
        this.registryCredentials = registryCredentials;
    }

    /**
     * Gets this image profile name.
     *
     * @return the profile name
     */
    @Nonnull
    public String getProfileName() {
        return profileName;
    }

    /**
     * Gets the image container specification.
     *
     * @return the container specification
     */
    @Nonnull
    public Node getContainerSpec() {
        return containerSpec;
    }

    /**
     * Pull-on-create flag. When {@code true}, the image will be pulled before the container creation.
     *
     * @return {@code true} if the container image must be pulled before creation.
     */
    public boolean isPullOnCreate() {
        return pullOnCreate;
    }

    /**
     * Rm-on-exit flag. When {@code true}, the container will be discarded as soon as it is stopped and not be reused.
     *
     * @return {@code true} if the container must be discarded when stopped, {@code false} otherwise
     */
    boolean isRmOnExit() {
        return rmOnExit;
    }

    /**
     * Use official TeamCity agent image.
     *
     * @return {@code true} when the official TeamCity agent image should be used.
     */
    public boolean isUseOfficialTCAgentImage() {
        return useOfficialTCAgentImage;
    }

    /**
     * Gets the maximal number of instance associated with this image.
     *
     * @return the maximal number of instance associated with this image
     */
    public int getMaxInstanceCount() {
        return maxInstanceCount;
    }

    /**
     * Gets the agent pool ID associated with this cloud image (if any).
     *
     * @return the agent pool id or {@code null}
     */
    @Nullable
    public Integer getAgentPoolId() {
        return agentPoolId;
    }

    /**
     * Gets the credentials to retrieve the Docker image.
     *
     * @return the credentials
     */
    @Nonnull
    public DockerRegistryCredentials getRegistryCredentials() {
        return registryCredentials;
    }

    /**
     * Load a list of cloud images from a configuration properties map. The ordering of the images will be the same
     * than the one specified in the underlying JSON definition.
     *
     * @param properties the map of properties
     *
     * @return the loaded list of images
     *
     * @throws NullPointerException             if the properties map is {@code null}
     * @throws DockerCloudClientConfigException if the image configuration is not valid
     */
    @Nonnull
    public static List<DockerImageConfig> processParams(@Nonnull Map<String, String> properties,
            @Nonnull Collection<CloudImageParameters> imagesParameters) {
        DockerCloudUtils.requireNonNull(properties, "Properties map cannot be null.");

        List<InvalidProperty> invalidProperties = new ArrayList<>();

        String imagesJSon = properties.get(DockerCloudUtils.IMAGES_PARAM);

        Set<String> profileNames = new HashSet<>();

        List<DockerImageConfig> images = Collections.emptyList();
        if (imagesJSon != null && !imagesJSon.isEmpty()) {
            try {
                Node imagesNode = Node.parse(imagesJSon);
                images = new ArrayList<>(imagesNode.getArrayValues().size());
                for (Node imageNode : imagesNode.getArrayValues()) {
                    DockerImageConfig imageConfig = DockerImageConfig.fromJSon(imageNode, imagesParameters);
                    boolean duplicateProfileName = !profileNames.add(imageConfig.getProfileName());
                    if (duplicateProfileName) {
                        invalidProperties.add(new InvalidProperty(DockerCloudUtils.IMAGES_PARAM, "Duplicate profile name: " + imageConfig.getProfileName()));
                    } else {
                        images.add(imageConfig);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to parse image configuration.", e);
                invalidProperties.add(new InvalidProperty(DockerCloudUtils.IMAGES_PARAM, "Cannot parse image data."));
            }
        }

        if (images.isEmpty()){
            invalidProperties.add(new InvalidProperty(DockerCloudUtils.IMAGES_PARAM, "No image provided."));
        }

        if (!invalidProperties.isEmpty()) {
            throw new DockerCloudClientConfigException(invalidProperties);
        }

        assert images != null;

        return images;
    }

    /**
     * Load an image configuration from a JSON node.
     *
     * @param node the JSON node
     * @param imagesParameters images parameters provided from the Cloud API if any
     *
     * @return the loaded configuration
     *
     * @throws NullPointerException     if {@code node} is {@code null}
     * @throws IllegalArgumentException if no valid configuration could be build from the provided JSON node
     */
    @Nonnull
    public static DockerImageConfig fromJSon(@Nonnull Node node, @Nonnull Collection<CloudImageParameters>
            imagesParameters) {
        DockerCloudUtils.requireNonNull(node, "JSON node cannot be null.");
        DockerCloudUtils.requireNonNull(imagesParameters, "Image parameters list cannot be null.");
        try {
            Node admin = node.getObject("Administration");
            LOG.info("Loading cloud profile configuration version " + admin.getAsInt("Version") + ".");

            Node container = node.getObject("Container");

            Node env = container.getArray("Env", Node.EMPTY_ARRAY);
            for (Node value : env.getArrayValues()) {
                String envValue = value.getAsString();
                if (envValue.startsWith(DockerCloudUtils.ENV_PREFIX)) {
                    throw new IllegalArgumentException("Variable start with reserved prefix: " + envValue);
                }
            }

            Node labels = container.getObject("Labels", Node.EMPTY_OBJECT);
            assert labels != null;
            for (String key : labels.getObjectValues().keySet()) {
                if (key.startsWith(DockerCloudUtils.NS_PREFIX)) {
                    throw new IllegalArgumentException("Label key start with reserved prefix: " + key);
                }
            }

            String profileName = admin.getAsString("Profile");
            boolean pullOnCreate = admin.getAsBoolean("PullOnCreate", true);
            boolean deleteOnExit = admin.getAsBoolean("RmOnExit");
            boolean useOfficialTCAgentImage = admin.getAsBoolean("UseOfficialTCAgentImage");

            Integer agentPoolId = null;
            for (CloudImageParameters imageParameter : imagesParameters) {
                if (profileName.equals(imageParameter.getId())) {
                    agentPoolId = imageParameter.getAgentPoolId();
                    break;
                }
            }

            DockerRegistryCredentials dockerRegistryCredentials =  registryAuthentication(admin);

            return new DockerImageConfig(profileName, node.getObject("Container"), pullOnCreate, deleteOnExit,
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
    private static DockerRegistryCredentials registryAuthentication(Node admin)
    {
        String registryUser = admin.getAsString("RegistryUser", null);
        String registryPassword = admin.getAsString("RegistryPassword", null);
        DockerRegistryCredentials dockerRegistryCredentials = DockerRegistryCredentials.ANONYMOUS;
        if (isNotEmpty(registryUser) && isNotEmpty(registryPassword)){
            String decodedPassword = new String(Base64.getDecoder().decode(registryPassword), StandardCharsets.UTF_16BE);
            dockerRegistryCredentials = DockerRegistryCredentials.from(registryUser, decodedPassword);
        }
        return dockerRegistryCredentials;
    }
}
