package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@link DockerInstance} test suite.
 */
public class DockerInstanceTest {

    @Test
    public void startedTimeInitializedAfterConstruct() {
        DockerInstance instance = createInstance();

        assertThat(instance.getStartedTime()).isInSameMinuteAs(new Date());
    }

    @Test
    public void startedTimeIsUpdated() {
        DockerInstance instance = createInstance();

        Date before = instance.getStartedTime();

        TestUtils.waitMillis(100);

        assertThat(before).isEqualTo(instance.getStartedTime());

        instance.updateStartedTime();

        assertThat(before).isBefore(instance.getStartedTime());
    }

    @Test
    public void getAgentHolderId() {
        DockerInstance instance = createInstance();

        assertThat(instance.getAgentHolderId()).isEmpty();

        String id = TestUtils.createRandomSha256();
        instance.setAgentHolderId(id);

        assertThat(instance.getAgentHolderId().get()).isEqualTo(id);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> instance.setAgentHolderId(null));
    }

    @Test
    public void getTaskId() {
        DockerInstance instance = createInstance();

        assertThat(instance.getTaskId()).isEmpty();

        String id = TestUtils.createRandomSha256();
        instance.setTaskId(id);

        assertThat(instance.getTaskId().get()).isEqualTo(id);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> instance.setTaskId(null));
    }

    @Test
    public void registerAgentRuntimeId() {
        DockerInstance instance = createInstance();

        assertThat(instance.getAgentRuntimeUuid()).isEmpty();

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> instance.registerAgentRuntimeUuid(null));
    }

    @Test
    public void registerAgentRuntimeIdMustIgnoreDoubleRegistration() {
        DockerInstance instance = createInstance();

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID_2);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);
    }

    @Test
    public void unregisterAgentRuntimeId() {
        DockerInstance instance = createInstance();

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);

        instance.unregisterAgentRuntimeUUid(TestUtils.TEST_UUID);

        assertThat(instance.getAgentRuntimeUuid()).isEmpty();

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> instance.unregisterAgentRuntimeUUid(null));
    }

    @Test
    public void unregisterAgentRuntimeIdMustIgnoreNonMatchingAgent() {
        DockerInstance instance = createInstance();

        instance.registerAgentRuntimeUuid(TestUtils.TEST_UUID);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);

        instance.unregisterAgentRuntimeUUid(TestUtils.TEST_UUID_2);

        assertThat(instance.getAgentRuntimeUuid().get()).isEqualTo(TestUtils.TEST_UUID);
    }

    private DockerInstance createInstance() {
        return new DockerInstance(new DockerImage(null,
                new DockerImageConfig("test", Node.EMPTY_OBJECT, false,false, false, DockerRegistryCredentials.ANONYMOUS, 1, null)));

    }
}