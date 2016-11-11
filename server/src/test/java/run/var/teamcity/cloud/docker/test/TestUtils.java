package run.var.teamcity.cloud.docker.test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * All purpose utility class for tests.
 */
public final class TestUtils {

    public static final UUID TEST_UUID = UUID.fromString("00000000-dead-beef-0000-000000000000");

    public static final UUID TEST_UUID_2 = UUID.fromString("00000000-1ced-beef-0000-000000000000");

    private final static int WAIT_DEFAULT_REFRESH_RATE_SEC = 1;
    private final static int WAIT_DEFAULT_MAX_WAIT_TIME_SEC = 10;

    public static void waitSec(long sec) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(sec));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitUntil(Supplier<Boolean> condition) {
        waitUntil(condition, WAIT_DEFAULT_MAX_WAIT_TIME_SEC);
    }

    public static void waitUntil(Supplier<Boolean> condition, long maxWaitSec) {
        long waitSince = System.nanoTime();
        while(!condition.get()) {
            if (Math.abs(System.nanoTime() - waitSince) > TimeUnit.SECONDS.toNanos(maxWaitSec)) {
                throw new RuntimeException("Time out.");
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(WAIT_DEFAULT_REFRESH_RATE_SEC));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
