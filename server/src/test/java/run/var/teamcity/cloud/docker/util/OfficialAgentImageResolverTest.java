package run.var.teamcity.cloud.docker.util;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import run.var.teamcity.cloud.docker.DockerImageConfig;
import run.var.teamcity.cloud.docker.client.DockerClientProcessingException;
import run.var.teamcity.cloud.docker.client.TestDockerClientRegistryFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Test
public class OfficialAgentImageResolverTest {

    private TestDockerClientRegistryFactory clientFty;
    private DockerImageConfig imageConfig;

    private int majorVersion;
    private int minorVersion;

    @BeforeMethod
    public void init() {
        clientFty = new TestDockerClientRegistryFactory();
        imageConfig = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, true, 1);
        majorVersion = 5;
        minorVersion = 2;
    }

    public void normalResolution() {
        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "5.2", "5.2.1",
                "5.2.1.1", "6.0"));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve(imageConfig)).isEqualTo(OfficialAgentImageResolver.REPO + ":5.2.1.1");

        assertThat(clientFty.getClient().isClosed()).isTrue();
    }

    public void shouldNotResolveImagesWithoutFlag() {
        imageConfig = new DockerImageConfig("test", Node.EMPTY_OBJECT, false, false, 1);

        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "5.2", "5.2.1",
                "5.2.1.1", "6.0"));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve(imageConfig)).isNull();
    }

    public void usingDefaultWhenNoMatchingVersion() {
        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "6.0"));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve(imageConfig)).isEqualTo(OfficialAgentImageResolver.DEFAULT);
    }

    public void usingDefaultWhenRepoLookupFail() {
        clientFty.configureClient(clt -> clt.failOnAccess(new DockerClientProcessingException("Test failure.")));

        clientFty.configureClient(clt -> clt.knownImage(OfficialAgentImageResolver.REPO, "5.2"));

        OfficialAgentImageResolver resolver = createResolver();

        assertThat(resolver.resolve(imageConfig)).isEqualTo(OfficialAgentImageResolver.DEFAULT);
    }

    public void mustCacheResult() {
        clientFty.configureClient((clt) -> clt.knownImage(OfficialAgentImageResolver.REPO, "4.0", "6.0"));

        OfficialAgentImageResolver resolver = createResolver();

        String resolved = resolver.resolve(imageConfig);

        clientFty.getClient().failOnAccess(new DockerClientProcessingException("Test failure."));

        assertThat(resolver.resolve(imageConfig)).isEqualTo(resolved);
    }

    public void constructor() {
        // No error expected.
        createResolver();
        majorVersion = 0;
        minorVersion = 0;
        createResolver();
        majorVersion = -1;
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(this::createResolver);
        majorVersion = 0;
        minorVersion = -1;
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(this::createResolver);
        minorVersion = 0;
        clientFty = null;
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(this::createResolver);
    }

    public OfficialAgentImageResolver createResolver() {
        return new OfficialAgentImageResolver(majorVersion, minorVersion, clientFty);
    }

}