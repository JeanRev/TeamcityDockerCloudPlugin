package run.var.teamcity.cloud.docker;

import org.junit.Test;
import run.var.teamcity.cloud.docker.test.TestUtils;
import run.var.teamcity.cloud.docker.util.Node;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link DockerInstance} test suite.
 */
public class DockerInstanceTest {

    @Test
    public void startedTimeInitializedAfterConstruct() {
        DockerInstance instance = new DockerInstance(new DockerImage(null,
                new DockerImageConfig("test", Node.EMPTY_OBJECT, false, false, 1)));

        assertThat(instance.getStartedTime()).isInSameMinuteAs(new Date());
    }

    @Test
    public void startedTimeIsUpdated() {
        DockerInstance instance = new DockerInstance(new DockerImage(null,
                new DockerImageConfig("test", Node.EMPTY_OBJECT, false, false, 1)));

        Date before = instance.getStartedTime();

        TestUtils.waitMillis(100);

        assertThat(before).isEqualTo(instance.getStartedTime());

        instance.updateStartedTime();

        assertThat(before).isBefore(instance.getStartedTime());
    }
}