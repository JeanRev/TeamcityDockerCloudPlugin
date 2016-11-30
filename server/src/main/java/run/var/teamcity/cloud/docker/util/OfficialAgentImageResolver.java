package run.var.teamcity.cloud.docker.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.version.ServerVersionHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageDefaultResolver;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerClientFactory;
import run.var.teamcity.cloud.docker.client.DockerRegistryClient;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to resolve the TeamCity agent official images tag name.
 * <p>
 *     In order to keep agent upgrade/downgrade time as low as possible, we should always use the agent image matching
 *     this server version. The algorithm implemented here rely on the assumption that the official image version tag
 *     uses the server version string (excluding the EAP marker).
 * </p>
 * <p>
 *     This resolver will attempt to connect the Docker HUB registry in order to know if the resulting version tag
 *     exists. If not, the {@code latest} tag will be used instead. This check is ignored the connection with the
 *     Docker registry is possible (eg. because of network related restrictions).
 * </p>
 * <p>
 *     The resolution result will be cached indefinitely.
 * </p>
 * <p>
 *     This class is thread-safe.
 * </p>
 */
public class OfficialAgentImageResolver extends DockerImageNameResolver {

    private final static Logger LOG = DockerCloudUtils.getLogger(OfficialAgentImageResolver.class);
    final static String REPO = "jetbrains/teamcity-agent";
    static { assert !REPO.contains(":"): "Repository name must NOT contains a tag name"; }
    final static String LATEST = REPO + ":latest";

    private final ReentrantLock lock = new ReentrantLock();
    private final String version;

    private final DockerRegistryClientFactory registryClientFty;

    private String imageTag;

    /**
     * Creates a new resolved for the specified major and minor server version.
     *
     * @param version the server major version
     * @param registryClientFty the Docker registry client factory to be used to query the available tags
     *
     * @throws IllegalArgumentException if any version number is negative
     */
    public OfficialAgentImageResolver(String version, DockerRegistryClientFactory registryClientFty) {
        super(new DockerImageDefaultResolver());
        DockerCloudUtils.requireNonNull(version, "Version string cannot be null.");
        DockerCloudUtils.requireNonNull(registryClientFty, "Registry client factory cannot be null.");

        this.version = version;
        this.registryClientFty = registryClientFty;
    }

    /**
     * Perform the resolution process. This method is a potentially I/O bound operation and may not return immediately.
     *
     * @return the resolved image, including the version tag
     */
    @Nullable
    @Override
    protected String resolveInternal(DockerImageConfig imgConfig) {
        if (!imgConfig.isUseOfficialTCAgentImage()) {
            return null;
        }
        String imageTag;
        lock.lock();
        try {
            imageTag = this.imageTag;
            if (imageTag == null) {
                imageTag = this.imageTag = performResolution();
            }

            return imageTag;
        } finally {
            lock.unlock();
        }
    }

    private String performResolution() {
        Node tags = null;
        try (DockerRegistryClient registry = registryClientFty.createDockerHubClient()) {
            String loginToken = registry.anonymousLogin("repository:" + REPO + ":pull").getAsString("token");
            tags = registry.listTags(loginToken, REPO);
        } catch (DockerClientException e) {
            LOG.warn("Failed to communicate with the registry.", e);
        }

        if (tags == null) {
            LOG.info("No remote version tags available, using default.");
            return REPO + ":" + version;
        } else {
            LOG.info("Found remote version tags: " + tags);
        }

        boolean foundMatchingTag = false;

        for (Node tag : tags.getArray("tags").getArrayValues()) {
            if (version.equals(tag.getAsString())) {
                foundMatchingTag = true;
            }
        }

        if (foundMatchingTag)  {
            return REPO + ":" + version;
        }

        return LATEST;
    }

    private static final Pattern TC_DISPLAY_VERSION_PTN = Pattern.compile("([0-9]+(?:\\.[0-9]+)*)(?:\\s.*)?");

    /**
     * Creates a resolver for the current build server instance.
     *
     * @param registryFty the Docker registry client factory
     *
     * @return the resolver
     *
     * @throws NullPointerException if {@code registryFty} is {@code null}
     */
    @NotNull
    public static OfficialAgentImageResolver forCurrentServer(DockerRegistryClientFactory registryFty,
                                                              DockerClientFactory clientFty) {
        DockerCloudUtils.requireNonNull(registryFty, "Registry client factory cannot be null.");
        String version = ServerVersionHolder.getVersion().getDisplayVersion();
        Matcher m = TC_DISPLAY_VERSION_PTN.matcher(ServerVersionHolder.getVersion().getDisplayVersion());
        boolean matches = m.matches();
        assert matches: "Unparseable server version: " + version;

        return new OfficialAgentImageResolver(m.group(1), registryFty);
    }
}
