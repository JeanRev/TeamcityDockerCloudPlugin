package run.var.teamcity.cloud.docker.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.SBuildServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.DockerImageDefaultResolver;
import run.var.teamcity.cloud.docker.DockerImageNameResolver;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.client.DockerRegistryClient;
import run.var.teamcity.cloud.docker.client.DockerRegistryClientFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Utility class to resolve the TeamCity agent official images tag name.
 * <p>
 *     In order to keep agent upgrade/downgrade time as low as possible, we should select the newest agent image
 *     matching this server version. The algorithm implemented here rely on the assumption that the image version tag
 *     uses the server major/minor version of the server as prefix, followed with the version of the image itself.
 * </p>
 * <p>
 *     At this time, the Docker remote API does not permits to search for image tags. Therefore, we must perform the
 *     lookup ourselves on the Docker Hub registry (which is not guaranteed to succeed, depending for example, of
 *     network related restrictions).
 * </p>
 * <p>
 *     If no valid version tag could be resolved, the {@code latest} agent image will always be returned. the
 *     resolution result will be cached indefinitely.
 * </p>
 * <p>
 *     This class is thread-safe.
 * </p>
 */
public class OfficialAgentImageResolver extends DockerImageNameResolver {

    private final static Logger LOG = DockerCloudUtils.getLogger(OfficialAgentImageResolver.class);
    final static String REPO = "jetbrains/teamcity-agent";
    static { assert !REPO.contains(":"): "Repository name must NOT contains a tag name"; }
    final static String DEFAULT = REPO + ":latest";
    private final static Pattern EXPECTED_VERSION_PTN = Pattern.compile("(?:\\d+\\.)*\\d+");

    private final ReentrantLock lock = new ReentrantLock();
    private final int serverMajorVersion;
    private final int serverMinorVersion;
    private final DockerRegistryClientFactory registryClientFty;

    private String imageTag;

    /**
     * Creates a new resolved for the specified major and minor server version.
     *
     * @param serverMajorVersion the server major version
     * @param serverMinorVersion the server minor version
     *
     * @throws IllegalArgumentException if any version number is negative
     */
    public OfficialAgentImageResolver(int serverMajorVersion, int serverMinorVersion,
                                      DockerRegistryClientFactory registryClientFty) {
        super(new DockerImageDefaultResolver());
        DockerCloudUtils.requireNonNull(registryClientFty, "Registry client factory cannot be null.");
        if (serverMajorVersion < 0 || serverMinorVersion < 0) {
            throw new IllegalArgumentException("Invalid version: " + serverMajorVersion + "." + serverMinorVersion);
        }
        this.serverMajorVersion = serverMajorVersion;
        this.serverMinorVersion = serverMinorVersion;
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
            return DEFAULT;
        } else {
            LOG.info("Found remote version tags: " + tags);
        }

        String foundTag = null;
        int[] foundTagVersionTokens = null;

        for (Node tag : tags.getArray("tags").getArrayValues()) {
            String tagValue = tag.getAsString();
            int[] tagVersionTokens = parseVersion(tagValue);
            if (tagVersionTokens == null || tagVersionTokens.length < 2) {
                LOG.debug("Ignoring unparseable tag: " + tagValue);
                continue;
            }
            if (tagVersionTokens[0] == serverMajorVersion && tagVersionTokens[1] == serverMinorVersion) {
                if (foundTagVersionTokens == null || compare(tagVersionTokens, foundTagVersionTokens) > 0) {
                    foundTag = tagValue;
                    foundTagVersionTokens = tagVersionTokens;
                }
            }
        }

        if (foundTag == null) {
            LOG.info("No match found in remote tags. Using default.");
            return DEFAULT;
        }

        return this.imageTag = REPO + ":" + foundTag;
    }


    private int[] parseVersion(String version) {
        assert version != null;
        if (EXPECTED_VERSION_PTN.matcher(version).matches()) {
            String[] tokens = version.split("\\.");
            int[] intTokens = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                intTokens[i] = Integer.parseInt(tokens[i]);
            }
            return intTokens;
        }
        return null;
    }

    private int compare(int[] versionTokens1, int[] versionTokens2) {
        assert versionTokens1 != null && versionTokens2 != null;
        assert versionTokens1.length >= 2 && versionTokens2.length >= 2;

        for (int i = 2; i < versionTokens1.length && i < versionTokens2.length; i++) {
            int cmp = Integer.compare(versionTokens1[i], versionTokens2[i]);
            if (cmp != 0) {
                return cmp;
            }
        }

        // Exited loop without finding a difference: a token is the version prefix of the other.
        // The longest is then the newest.
        return Integer.compare(versionTokens1.length, versionTokens2.length);
    }

    /**
     * Creates a resolver for the provided build server instance.
     *
     * @param server the build server
     *
     * @return the resolver
     *
     * @throws NullPointerException if {@code server} is {@code null}
     */
    @NotNull
    public static OfficialAgentImageResolver forServer(@NotNull SBuildServer server) {
        DockerCloudUtils.requireNonNull(server, "Server instance cannot be null.");

        return new OfficialAgentImageResolver(server.getServerMajorVersion(), server.getServerMinorVersion(),
                DockerRegistryClientFactory.getDefault());
    }
}
