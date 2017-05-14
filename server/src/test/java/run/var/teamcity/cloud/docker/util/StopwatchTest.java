package run.var.teamcity.cloud.docker.util;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import run.var.teamcity.cloud.docker.test.LongRunning;
import run.var.teamcity.cloud.docker.test.TestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;


/**
 * {@link Stopwatch} test suite.
 */
@Category(LongRunning.class)
public class StopwatchTest {

    @Test
    public void getElapsedTimeNanos() {
        Stopwatch sw = Stopwatch.start();
        TestUtils.waitMillis(1000L);
        assertThat(sw.nanos()).isCloseTo(TimeUnit.MILLISECONDS.toNanos(1000L),
                offset(TimeUnit.MILLISECONDS.toNanos(100L)));
    }


    @Test
    public void getElapsedTimeMillis() {
        Stopwatch sw = Stopwatch.start();
        TestUtils.waitMillis(1000L);
        assertThat(sw.millis()).isCloseTo(1000L, offset(100L));
    }

    @Test
    public void getElapsedTimeSeconds() {
        Stopwatch sw = Stopwatch.start();
        TestUtils.waitSec(3);
        assertThat(sw.seconds()).isCloseTo(3L, offset(1L));
        ;
    }

    @Test
    public void measureMillis() {
        Runnable runnable = () -> TestUtils.waitSec(1);
        assertThat(Stopwatch.measureMillis(runnable)).isCloseTo(1000L, offset(150L));
    }

    @Test
    public void reset() {
        Stopwatch sw = Stopwatch.start();
        TestUtils.waitSec(1);
        sw.reset();
        assertThat(sw.millis()).isBetween(0L, 100L);
    }
}
