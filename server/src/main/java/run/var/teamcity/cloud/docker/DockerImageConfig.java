package run.var.teamcity.cloud.docker;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link DockerImage} configuration.
 */
public class DockerImageConfig {

    private static final Logger LOG = DockerCloudUtils.getLogger(DockerImageConfig.class);

    private final String profileName;
    private final Node agentHolderSpec;
    private final boolean pullOnCreate;
    private final boolean rmOnExit;
    private final boolean useOfficialTCAgentImage;
    private final int maxInstanceCount;
    private final Integer agentPoolId;
    private final DockerRegistryCredentials registryCredentials;

    public DockerImageConfig(@Nonnull String profileName, @Nonnull Node agentHolderSpec, boolean pullOnCreate,
                             boolean rmOnExit, boolean useOfficialTCAgentImage,
                             @Nonnull DockerRegistryCredentials registryCredentials, int maxInstanceCount,
                             @Nullable Integer agentPoolId) {
        DockerCloudUtils.requireNonNull(profileName, "Profile name cannot be null.");
        DockerCloudUtils.requireNonNull(registryCredentials, "Registry credentials cannot be null.");
        DockerCloudUtils.requireNonNull(agentHolderSpec, "Agent holder specification cannot be null.");
        if (maxInstanceCount < 1) {
            throw new IllegalArgumentException("At least 1 instance must be allowed.");
        }
        this.profileName = profileName;
        this.agentHolderSpec = agentHolderSpec;
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
     * Gets the image agent holder specification.
     *
     * @return the agent holder specification
     */
    @Nonnull
    public Node getAgentHolderSpec() {
        return agentHolderSpec;
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
     * @return the agent pool id if any
     */
    @Nonnull
    public Optional<Integer> getAgentPoolId() {
        return Optional.ofNullable(agentPoolId);
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
     * @param imageParser the image configuration parser
     * @param properties the map of properties
     *
     * @return the loaded list of images
     *
     * @throws NullPointerException             if any argument is {@code null}
     * @throws DockerCloudClientConfigException if the image configuration is not valid
     */
    @Nonnull
    public static List<DockerImageConfig> processParams(@Nonnull DockerImageConfigParser imageParser,
            @Nonnull Map<String, String> properties,
            @Nonnull Collection<CloudImageParameters> imagesParameters) {
        DockerCloudUtils.requireNonNull(imageParser, "Image parser cannot be null.");
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
                    DockerImageConfig imageConfig = imageParser.fromJSon(imageNode, imagesParameters);
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
}
