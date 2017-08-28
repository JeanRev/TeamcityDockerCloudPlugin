package run.var.teamcity.cloud.docker.util;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.test.LongRunning;
import run.var.teamcity.cloud.docker.test.TestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;


/**
 * {@link Stopwatch} test suite.
 */
@Category(LongRunning.class)
public class StopwatchTest {

    @Test
    public void measure() {
        Stopwatch sw = Stopwatch.start();
        TestUtils.waitSec(1);
        assertThat(sw.getDuration().toMillis()).isCloseTo(1000L, offset(150L));
    }

    @Test
    public void measureRunnable() {
        Runnable runnable = () -> TestUtils.waitSec(1);
        assertThat(Stopwatch.measure(runnable).toMillis()).isCloseTo(1000L, offset(150L));
    }
}
