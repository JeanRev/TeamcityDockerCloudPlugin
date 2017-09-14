package run.var.teamcity.cloud.docker.util;

import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;
import run.var.teamcity.cloud.docker.client.TestDockerClientRegistryFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link OfficialAgentImageResolver} test suite.
 */
public class OfficialAgentImageResolverTest {

    private TestDockerClientRegistryFactory clientFty;

    private String version;

    @Before
    public void init() {
        clientFty = new TestDockerClientRegistryFactory();
        version = "10.0.3";
    }

    @Test
    public void normalResolution() {
        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "5.2", "5.2.1",
                                                          "5.2.1.1", "6.0", version));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve()).isEqualTo(OfficialAgentImageResolver.REPO + ":" + version);

        assertThat(clientFty.getClient().isClosed()).isTrue();
    }

    @Test
    public void usingLatestWhenNoMatchingVersion() {
        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "6.0"));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve()).isEqualTo(OfficialAgentImageResolver.LATEST);
    }

    @Test
    public void usingDefaultWhenRepoLookupFail() {
        clientFty.configureClient(clt -> clt.failOnAccess(new DockerClientProcessingException("Test failure.")));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve()).isEqualTo(OfficialAgentImageResolver.REPO + ":" + version);
    }

    @Test
    public void mustCacheResult() {
        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "6.0"));

        OfficialAgentImageResolver resolver = createResolver();

        String resolved = resolver.resolve();

        clientFty.getClient().failOnAccess(new DockerClientProcessingException("Test failure."));

        assertThat(resolver.resolve()).isEqualTo(resolved);
    }

    @Test
    public void constructor() {
        // No error expected.
        createResolver();
        version = null;
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(this::createResolver);
        version = "1.0";
        clientFty = null;
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(this::createResolver);
    }

    @Test
    public void usingAPIStaticVersion() {
        OfficialAgentImageResolver resolver = OfficialAgentImageResolver.forCurrentServer(clientFty);

        // Check that the resolution process complete without error.
        // We also assert that the version tag will always the latest since the no image is available in our test
        // registry.
        assertThat(resolver.resolve()).isEqualTo(OfficialAgentImageResolver.LATEST);
    }

    private OfficialAgentImageResolver createResolver() {
        return new OfficialAgentImageResolver(version, clientFty);
    }

}