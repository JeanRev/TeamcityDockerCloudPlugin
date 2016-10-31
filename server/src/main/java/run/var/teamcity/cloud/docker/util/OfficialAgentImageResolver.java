package run.var.teamcity.cloud.docker.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.SBuildServer;
import org.jetbrains.annotations.NotNull;
import run.var.teamcity.cloud.docker.client.DockerClientException;
import run.var.teamcity.cloud.docker.registry.DockerRegistryClient;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Utility class to resolve the TeamCity agent official images tag name.
 * <p>
 *     In order to keep agent upgrade/downgrade time as low as possible, we should select the newest agent image
 *     matching this server version. The algorithm implemented here rely on two assumptions:
 *     <ul>
 *         <li>The server version is expected to be composed exactly from a major and minor version number.</li>
 *         <li>The remote tag is expected to use the server major/minor version as prefix, followed with the
 *         version of the image itself.</li>
 *     </ul>
 * </p>
 * <p>
 *     At this time, the Docker remote API does not permits to search for image tags. Therefore, we must perform the
 *     lookup ourselves on the Docker Hub registry (which is not guaranteed to succeed, depending for example, of
 *     network related restrictions).
 * </p>
 * <p>
 *     If no valid version tag could be resolved, the {@code latest} agent image will always be returned.
 * </p>
 */
public class OfficialAgentImageResolver {

    private final static Logger LOG = DockerCloudUtils.getLogger(OfficialAgentImageResolver.class);
    private final static String REPO = "jetbrains/teamcity-agent";
    static { assert !REPO.contains(":"): "Repository name must NOT contains a tag name"; }
    private final static Pattern EXPECTED_VERSION_PTN = Pattern.compile("(?:\\d+\\.)*\\d+");

    private final ReentrantLock lock = new ReentrantLock();
    private final int serverMajorVersion;
    private final int serverMinorVersion;

    private String imageTag;

    public OfficialAgentImageResolver(int serverMajorVersion, int serverMinorVersion) {
        this.serverMajorVersion = serverMajorVersion;
        this.serverMinorVersion = serverMinorVersion;
    }

    public String resolve() {
        String imageTag;
        lock.lock();
        try {
            imageTag = this.imageTag;
            if (imageTag != null) {
                return imageTag;
            }

            Node tags = null;
            try (DockerRegistryClient registry = DockerRegistryClient.openDockerHubClient()) {
                String loginToken = registry.anonymousLogin("repository:" + REPO + ":pull").getAsString("token");
                tags = registry.listTags(loginToken, REPO);
            } catch (DockerClientException e) {
                LOG.warn("Failed to communicate with the registry.", e);
            }

            if (tags == null) {
                LOG.info("No remote version tags available, using default.");
                return REPO;
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
                return REPO;
            }

            return this.imageTag = REPO + ":" + foundTag;
        } finally {
            lock.unlock();
        }
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
        // The longest is then newest.
        return Integer.compare(versionTokens1.length, versionTokens2.length);
    }

    @NotNull
    public static OfficialAgentImageResolver forServer(@NotNull SBuildServer server) {
        DockerCloudUtils.requireNonNull(server, "Server instance cannot be null.");

        return new OfficialAgentImageResolver(server.getServerMajorVersion(), server.getServerMinorVersion());
    }
}
