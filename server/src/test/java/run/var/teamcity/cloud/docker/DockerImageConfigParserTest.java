package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.CloudImageParameters;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.util.EditableNode;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Arrays;
import java.util.Collections;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DockerImageConfigParser} test suite.
 */
public class DockerImageConfigParserTest {

    private TestDockerImageMigrationHandler migrationHandler;

    @Before
    public void setup() {
        migrationHandler = new TestDockerImageMigrationHandler();
    }

    @Test
    public void mustInvokeMigrationHandler() {
        Spec spec = new Spec();
        spec.administration.put("Profile", "profile_before_migration");
        Node imageData = spec.root.saveNode();
        spec.administration.put("Profile", "profile_after_migration");
        Node migratedData = spec.root.saveNode();

        migrationHandler.setMigratedData(migratedData);

        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);
        DockerImageConfig config = parser.fromJSon(imageData, emptyList());

        assertThat(migrationHandler.getImageData()).isSameAs(imageData);
        assertThat(config.getProfileName()).isEqualTo("profile_after_migration");
    }

    @Test
    public void profileName() {
        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);
        Spec spec = new Spec();
        spec.administration.put("Profile", "profile_name");
        DockerImageConfig config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.getProfileName()).isEqualTo("profile_name");

        spec.administration.remove("Profile");

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> parser.fromJSon(spec.root.saveNode(),
                emptyList()));
    }

    @Test
    public void rmOnExit() {
        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);
        Spec spec = new Spec();
        spec.administration.put("RmOnExit", true);
        DockerImageConfig config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isRmOnExit()).isTrue();
        spec.administration.put("RmOnExit", false);
        config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isRmOnExit()).isFalse();
        spec.administration.remove("RmOnExit");
        config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isRmOnExit()).isFalse();
    }

    @Test
    public void maxInstanceCount() {
        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);
        Spec spec = new Spec();
        spec.administration.put("MaxInstanceCount", 42);
        DockerImageConfig config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.getMaxInstanceCount()).isEqualTo(42);
        spec.administration.put("MaxInstanceCount", 0);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> parser.fromJSon(spec.root.saveNode(),
                emptyList()));
        spec.administration.remove("MaxInstanceCount");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> parser.fromJSon(spec.root.saveNode(),
                emptyList()));
    }

    @Test
    public void UseOfficialTCAgentImage() {
        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);
        Spec spec = new Spec();
        spec.administration.put("UseOfficialTCAgentImage", true);
        DockerImageConfig config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isUseOfficialTCAgentImage()).isTrue();
        spec.administration.put("UseOfficialTCAgentImage", false);
        config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
        spec.administration.remove("UseOfficialTCAgentImage");
        config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isUseOfficialTCAgentImage()).isFalse();
    }

    @Test
    public void pullOnCreate() {
        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);
        Spec spec = new Spec();
        spec.administration.put("PullOnCreate", true);
        DockerImageConfig config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isPullOnCreate()).isTrue();
        spec.administration.put("PullOnCreate", false);
        config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isPullOnCreate()).isFalse();
        spec.administration.remove("PullOnCreate");
        config = parser.fromJSon(spec.root.saveNode(), emptyList());
        assertThat(config.isPullOnCreate()).isFalse();
    }

    @Test
    public void agentPoolId() {
        CloudImageParameters imageParams1 = new CloudImageParameters();
        imageParams1.setParameter(CloudImageParameters.SOURCE_ID_FIELD, "TestProfile");
        imageParams1.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "42");

        CloudImageParameters imageParams2 = new CloudImageParameters();
        imageParams2.setParameter(CloudImageParameters.SOURCE_ID_FIELD, "AnotherTestProfile");
        imageParams2.setParameter(CloudImageParameters.AGENT_POOL_ID_FIELD, "0");

        DockerImageConfigParser parser = new DefaultDockerImageConfigParser(migrationHandler);

        Spec spec = new Spec();

        spec.administration.put("Profile", "TestProfile");

        DockerImageConfig config = parser.fromJSon(spec.root.saveNode(), emptyList());

        assertThat(config.getAgentPoolId()).isEmpty();

        config = parser.fromJSon(spec.root.saveNode(), Arrays.asList(imageParams1, imageParams2));

        assertThat(config.getAgentPoolId().get()).isEqualTo(42);
    }

    private static class Spec {
        final EditableNode root;
        final EditableNode administration;


        Spec() {
            root = Node.EMPTY_OBJECT.editNode();
            administration = root.getOrCreateObject("Administration");
            administration.
                    put("Profile", "test").
                    put("Version", 42).
                    put("MaxInstanceCount", 2);
        }
    }
}