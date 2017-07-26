package run.var.teamcity.cloud.docker.util;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static run.var.teamcity.cloud.docker.test.TestUtils.callAsync;
import static run.var.teamcity.cloud.docker.test.TestUtils.runAsync;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitMillis;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitSec;

/**
 * {@link LockHandler} test suite.
 */
public class LockHandlerTest {

    @Test(timeout = 5000)
    public void run() {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        runAsync(() -> {
            assertThat(lock.isLocked()).isFalse();
            lock.run(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl1.notifyExecution();
            });
        });

        ctrl1.awaitExecution();

        ExecutionControl ctrl2 = new ExecutionControl();

        runAsync(() -> {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
            lock.run(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl2.notifyExecution();
            });
        });

        waitMillis(500);

        assertThat(ctrl2.executionStarted).isFalse();

        ctrl1.release();

        assertThat(Stopwatch.measureMillis(ctrl2::awaitExecution)).isBetween(0L, 100L);

        ctrl2.release();
    }

    @Test(timeout = 5000)
    public void runCheckedLockBehavior() {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        runAsync(() -> {
            assertThat(lock.isLocked()).isFalse();
            lock.runChecked(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl1.notifyExecution();
            });
        });

        ctrl1.awaitExecution();

        ExecutionControl ctrl2 = new ExecutionControl();

        runAsync(() -> {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
            lock.runChecked(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl2.notifyExecution();
            });
        });

        waitMillis(500);

        assertThat(ctrl2.executionStarted).isFalse();

        ctrl1.release();

        assertThat(Stopwatch.measureMillis(ctrl2::awaitExecution)).isBetween(0L, 100L);

        ctrl2.release();
    }

    @Test(timeout = 5000)
    public void runCheckedPropagateException() {

        LockHandler lock = LockHandler.newReentrantLock();

        IOException e = new IOException();

        try {
            lock.runChecked(() -> { throw e; });
            fail("No exception caught.");
        } catch (IOException caught) {
            assertThat(caught).isSameAs(e);
        }
    }

    @Test(timeout = 5000)
    public void runInterruptiblyLockBehavior() {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        runAsync(() -> {
            assertThat(lock.isLocked()).isFalse();
            lock.runInterruptibly(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl1.notifyExecution();
            });
        });

        ctrl1.awaitExecution();

        ExecutionControl ctrl2 = new ExecutionControl();

        runAsync(() -> {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
            lock.runInterruptibly(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl2.notifyExecution();
            });
        });

        waitMillis(500);

        assertThat(ctrl2.executionStarted).isFalse();

        ctrl1.release();

        assertThat(Stopwatch.measureMillis(ctrl2::awaitExecution)).isBetween(0L, 100L);

        ctrl2.release();
    }

    @Test(timeout = 5000)
    public void runInterruptiblyReactToInterrupt() throws ExecutionException, InterruptedException {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        runAsync(() -> lock.run(ctrl1::notifyExecution));

        ctrl1.awaitExecution();

        assertThat(lock.isLocked()).isTrue();

        ExecutionControl ctrl2 = new ExecutionControl();

        Future<Void> future = runAsync(() -> {
            ctrl2.notifyExecution();
                    try {
                        lock.runInterruptibly(() -> fail("Interruption expected."));
                        fail("Interruption expected.");
                    } catch (InterruptedException e) {
                        // OK
                    }
                });

        ctrl2.awaitExecution();
        ctrl2.release();

        waitMillis(500);

        assertThat(future.isDone()).isFalse();

        ctrl2.executingThread.interrupt();

        assertThat(Stopwatch.measureMillis(() -> {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                fail("Unexpected exception.", e);
            }
        })).isBetween(0L, 200L);

        ctrl1.release();
    }

    @Test(timeout = 5000)
    public void call() throws ExecutionException, InterruptedException {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        Future<Integer> future = callAsync(() -> {
            assertThat(lock.isLocked()).isFalse();
            return lock.call(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl1.notifyExecution();
                return 42;
            });
        });

        ctrl1.awaitExecution();

        ExecutionControl ctrl2 = new ExecutionControl();

        callAsync(() -> {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
            return lock.call(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl2.notifyExecution();
                return null;
            });
        });

        waitMillis(500);

        assertThat(ctrl2.executionStarted).isFalse();

        ctrl1.release();

        assertThat(future.get()).isEqualTo(42);
        assertThat(Stopwatch.measureMillis(ctrl2::awaitExecution)).isBetween(0L, 100L);

        ctrl2.release();
    }

    @Test(timeout = 5000)
    public void callCheckedLockBehavior() throws ExecutionException, InterruptedException {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        Future<Integer> future = callAsync(() -> {
            assertThat(lock.isLocked()).isFalse();
            return lock.callChecked(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl1.notifyExecution();
                return 42;
            });
        });

        ctrl1.awaitExecution();

        ExecutionControl ctrl2 = new ExecutionControl();

        callAsync(() -> {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
            return lock.callChecked(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl2.notifyExecution();
                return null;
            });
        });

        waitMillis(500);

        assertThat(ctrl2.executionStarted).isFalse();

        ctrl1.release();

        assertThat(future.get()).isEqualTo(42);
        assertThat(Stopwatch.measureMillis(ctrl2::awaitExecution)).isBetween(0L, 100L);

        ctrl2.release();
    }

    @Test(timeout = 5000)
    public void callCheckedPropagateException() {
        LockHandler lock = LockHandler.newReentrantLock();

        IOException e = new IOException();

        try {
            lock.callChecked(() -> { throw e; });
            fail("No exception caught.");
        } catch (IOException caught) {
            assertThat(caught).isSameAs(e);
        }
    }

    @Test(timeout = 5000)
    public void callInterruptiblyLockBehavior() throws ExecutionException, InterruptedException {
        LockHandler lock = LockHandler.newReentrantLock();

        ExecutionControl ctrl1 = new ExecutionControl();

        Future<Integer> future = callAsync(() -> {
            assertThat(lock.isLocked()).isFalse();
            return lock.callInterruptibly(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl1.notifyExecution();
                return 42;
            });
        });

        ctrl1.awaitExecution();

        ExecutionControl ctrl2 = new ExecutionControl();

        callAsync(() -> {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
            return lock.callInterruptibly(() -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                ctrl2.notifyExecution();
                return null;
            });
        });

        waitMillis(500);

        assertThat(ctrl2.executionStarted).isFalse();

        ctrl1.release();

        assertThat(future.get()).isEqualTo(42);
        assertThat(Stopwatch.measureMillis(ctrl2::awaitExecution)).isBetween(0L, 100L);

        ctrl2.release();
    }

    @Test(timeout = 5000)
    public void callInterruptiblyReactToInterrupt() {

        LockHandler lock = LockHandler.newReentrantLock();

        assertThat(Stopwatch.measureMillis(() -> runAsync(() ->  {
            Thread.currentThread().interrupt();
            try {
                lock.callInterruptibly(() -> {
                    waitSec(1);
                    return null;
                });
                fail("Interruption expected.");
            } catch (InterruptedException e){
                // OK
            }
        }))).isBetween(0L, 200L);
    }

    @Test(timeout = 5000)
    public void isHeldByCurrentThread() {
        LockHandler lock = LockHandler.newReentrantLock();

        assertThat(lock.isHeldByCurrentThread()).isFalse();

        lock.run(() -> assertThat(lock.isHeldByCurrentThread()).isTrue());

        assertThat(lock.isHeldByCurrentThread()).isFalse();
    }

    private static class ExecutionControl {

        final CountDownLatch latch = new CountDownLatch(1);
        final ReentrantLock executionLock = new ReentrantLock();
        volatile boolean executionStarted = false;
        volatile Thread executingThread;

        {
            executionLock.lock();
        }


        void awaitExecution() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                fail("Thread interrupted.", e);
            }
        }

        void release() {
            assertThat(executionLock.isLocked() && executionLock.isHeldByCurrentThread());
            executionLock.unlock();
        }

        public void notifyExecution() {
            executionStarted = true;
            executingThread = Thread.currentThread();
            assertThat(executionLock.isLocked() && !executionLock.isHeldByCurrentThread());
            latch.countDown();
            executionLock.lock();
        }
    }
}