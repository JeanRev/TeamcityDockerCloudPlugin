package run.var.teamcity.cloud.docker;


import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static run.var.teamcity.cloud.docker.util.DockerCloudUtils.requireNonNull;

/**
 * Parameters to setup a new containerized agent.
 * <p>
 *     Instances of this class are NOT thread-safe.
 * </p>
 */
public class CreateAgentParameters {

    private final Node agentHolderSpec;

    private Map<String, String> labels = new LinkedHashMap<>();
    private Map<String, String> env = new LinkedHashMap<>();
    private String imageName = null;
    private PullStrategy pullStrategy = PullStrategy.NO_PULL;
    private DockerRegistryCredentials registryCredentials = DockerRegistryCredentials.ANONYMOUS;
    private PullStatusListener pullStatusListener = PullStatusListener.NOOP;

    private CreateAgentParameters(Node agentHolderSpec) {
        assert agentHolderSpec != null;
        this.agentHolderSpec = agentHolderSpec;
    }

    /**
     * Gets the agent holder specification.
     *
     * @return the agent holder specification
     */
    @Nonnull
    public Node getAgentHolderSpec() {
        return agentHolderSpec;
    }

    /**
     * The labels to be applied on the agent holder.
     *
     * @return the labels to be applied
     */
    @Nonnull
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * The environment variables to be published on the agent container.
     *
     * @return the environment variables to be published
     */
    @Nonnull
    public Map<String, String> getEnv() {
        return env;
    }

    /**
     * Returns the overridden image name (if any). To be used if another image name than the one stored in the
     * agent holder specification must be used.
     *
     * @return the overridden image name (if any)
     */
    @Nonnull
    public Optional<String> getImageName() {
        return Optional.ofNullable(imageName);
    }


    /**
     * Gets the pull strategy.
     *
     * @return the pull strategy
     */
    @Nonnull
    public PullStrategy getPullStrategy() {
        return pullStrategy;
    }

    /**
     * Gets the registry credentials to be used when pulling the Docker image.
     *
     * @return the registry credentials
     */
    @Nonnull
    public DockerRegistryCredentials getRegistryCredentials() {
        return registryCredentials;
    }

    /**
     * Gets the listener to monitor the pull process. By default, no pull will be performed.
     *
     * @return the pull listener
     */
    @Nonnull
    public PullStatusListener getPullStatusListener() {
        return pullStatusListener;
    }

    /**
     * Adds a new label mapping for the agent holder.
     *
     * @param key the label key
     * @param value the label value
     *
     * @return this instance for chained invocation
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public CreateAgentParameters label(@Nonnull String key, @Nonnull String value) {
        requireNonNull(key, "Label key cannot be null.");
        requireNonNull(value, "Label value cannot be null.");

        labels.put(key, value);
        return this;
    }

    /**
     * Adds a new environment variable mapping for the container.
     *
     * @param var the environment variable name
     * @param value the environment variable value
     *
     * @return this instance for chained invocation
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public CreateAgentParameters env(@Nonnull String var, @Nonnull String value) {
        requireNonNull(var, "Environment variable name cannot be null.");
        requireNonNull(value, "Environment variable value cannot be null.");

        env.put(var, value);
        return this;
    }

    /**
     * Overrides the image name from the agent holder specification.
     *
     * @param imageName the image name to be used
     *
     *  @return this instance for chained invocation
     *
     * @throws NullPointerException if {@code imageName} is {@code null}
     */
    public CreateAgentParameters imageName(@Nonnull String imageName) {
        this.imageName = requireNonNull(imageName, "Image name cannot be null.");
        return this;
    }

    /**
     * Sets the strategy to be used for pulling images.
     *
     * @param pullStrategy the pull strategy
     *
     * @return this instance for chained invocation
     *
     * @throws NullPointerException if {@code pullStrategy} is {@code null}
     */
    public CreateAgentParameters pullStrategy(@Nonnull PullStrategy pullStrategy) {
        this.pullStrategy = requireNonNull(pullStrategy, "Pull strategy cannot be null.");
        return this;
    }

    /**
     * Sets the registry credentials to be used for pulling images.
     *
     * @param registryCredentials the registry credentials
     *
     * @return this instance for chained invocation
     *
     * @throws NullPointerException if {@code registryCredentials} is {@code null}
     */
    public CreateAgentParameters registryCredentials(@Nonnull DockerRegistryCredentials registryCredentials) {
        this.registryCredentials = requireNonNull(registryCredentials, "Docker registry credentials cannot be null.");
        return this;
    }

    /**
     * Sets the listener to be used when pulling images.
     *
     * @param pullStatusListener the pull listener
     *
     * @return this instance for chained invocation
     *
     * @throws NullPointerException if {@code pullStatusListener} is {@code null}
     */
    public CreateAgentParameters pullStatusListener(@Nonnull PullStatusListener pullStatusListener) {
        this.pullStatusListener = requireNonNull(pullStatusListener, "Pull status listener cannot be null.");
        return this;
    }

    /**
     * Create  a new of parameter.
     *
     * @param agentHolderSpec the agent holder specification
     *
     * @return the new set of parameters
     *
     * @throws NullPointerException if {@code agentHolderSpec} is {@code null}
     */
    public static CreateAgentParameters from(@Nonnull Node agentHolderSpec) {
        DockerCloudUtils.requireNonNull(agentHolderSpec, "Agent holder specification cannot be null.");
        return new CreateAgentParameters(agentHolderSpec);
    }

    /**
     * Creates a new set of parameters from {@link DockerImageConfig}.
     *
     * @param imageConfig the image configuration
     * @param resolver the resolver for official TeamCity agent images
     * @param ignorePullFailure any error encountered when pulling images will be ignored when this flag is set to
     * {@code true}
     *
     * @return the new set of parameters
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public static CreateAgentParameters fromImageConfig(@Nonnull DockerImageConfig imageConfig,
                                                        @Nonnull DockerImageNameResolver resolver,
                                                        boolean  ignorePullFailure) {
        CreateAgentParameters createAgentParameters = new CreateAgentParameters(imageConfig.getContainerSpec());

        PullStrategy pullStrategy;
        if (imageConfig.isPullOnCreate()) {
            pullStrategy = ignorePullFailure ? PullStrategy.PULL_IGNORE_FAILURE : PullStrategy.PULL;
        } else {
            pullStrategy = PullStrategy.NO_PULL;
        }

        createAgentParameters.
                pullStrategy(pullStrategy).
                registryCredentials(imageConfig.getRegistryCredentials());

        if (imageConfig.isUseOfficialTCAgentImage()) {
            createAgentParameters.imageName(resolver.resolve());
        }

        return createAgentParameters;
    }
}
