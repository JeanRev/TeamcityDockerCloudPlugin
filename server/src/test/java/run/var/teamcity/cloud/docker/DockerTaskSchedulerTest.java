package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.InstanceStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.data.Offset.offset;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitMillis;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitSec;
import static run.var.teamcity.cloud.docker.test.TestUtils.waitUntil;

/**
 * {@link DockerTaskScheduler} test suite.
 */
public class DockerTaskSchedulerTest {

    private DockerTaskScheduler scheduler;

    private ReentrantLock instanceLock;
    private ReentrantLock clientLock;

    private DockerInstance instance1;
    private DockerInstance instance2;
    private DockerInstance instance3;

    private TestDockerCloudClient cloudClient;

    @Before
    public void init() {
        instance1 = new DockerInstance(testImage());
        instance2 = new DockerInstance(testImage());
        instance3 = new DockerInstance(testImage());

        cloudClient = new TestDockerCloudClient();

        instanceLock = new ReentrantLock();
        clientLock = new ReentrantLock();
    }

    @Test
    public void differentInstanceTasksMayExecuteConcurrently() {
        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(2));

        InstanceTestTaskDelegator task1 = new InstanceTestTaskDelegator(instance1);
        InstanceTestTaskDelegator task2 = new InstanceTestTaskDelegator(instance2);
        InstanceTestTaskDelegator task3 = new InstanceTestTaskDelegator(instance3);

        instanceLock.lock();

        scheduler.scheduleInstanceTask(task1.task);
        scheduler.scheduleInstanceTask(task2.task);
        scheduler.scheduleInstanceTask(task3.task);

        waitMillis(500);

        assertThat(task1.isRunning()).isTrue();
        assertThat(task2.isRunning()).isTrue();
        assertThat(task3.isRunning()).isTrue();
    }

    @Test
    public void sameInstanceTasksMayNotExecuteConcurrently() {
        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(2));

        InstanceTestTaskDelegator task1 = new InstanceTestTaskDelegator(instance1);
        InstanceTestTaskDelegator task2 = new InstanceTestTaskDelegator(instance2);
        InstanceTestTaskDelegator task2b = new InstanceTestTaskDelegator(instance2);

        instanceLock.lock();

        scheduler.scheduleInstanceTask(task1.task);
        scheduler.scheduleInstanceTask(task2.task);
        scheduler.scheduleInstanceTask(task2b.task);

        waitMillis(500);

        assertThat(task1.isRunning()).isTrue();
        assertThat(task2.isRunning()).isTrue();
        assertThat(task2b.executions).isEmpty();

        instanceLock.unlock();

        waitMillis(500);

        assertThat(task2.isSuccessful()).isTrue();
        assertThat(task2.isSuccessful()).isTrue();
        assertThat(task2b.isSuccessful()).isTrue();
    }

    @Test
    public void clientInstanceTaskPreventInstanceTaskExecution() {
        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(2));

        ClientTestTaskDelegator clientTask = new ClientTestTaskDelegator();
        InstanceTestTaskDelegator instanceTask = new InstanceTestTaskDelegator(instance1);

        clientLock.lock();

        scheduler.scheduleClientTask(clientTask.task);
        scheduler.scheduleInstanceTask(instanceTask.task);

        waitMillis(500);

        assertThat(clientTask.executions).hasSize(1).first().matches(Execution::isRunning);
        assertThat(instanceTask.executions).isEmpty();

        clientLock.unlock();

        waitMillis(500);

        assertThat(clientTask.isSuccessful()).isTrue();
        assertThat(instanceTask.isSuccessful()).isTrue();
    }

    @Test
    public void multipleClientTaskAreExecutedSynchronously() {
        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(2));

        ClientTestTaskDelegator clientTask1 = new ClientTestTaskDelegator();
        ClientTestTaskDelegator clientTask2 = new ClientTestTaskDelegator();

        clientLock.lock();

        scheduler.scheduleClientTask(clientTask1.task);
        scheduler.scheduleClientTask(clientTask2.task);

        waitMillis(500);

        assertThat(clientTask1.executions).hasSize(1).first().matches(Execution::isRunning);
        assertThat(clientTask2.executions).isEmpty();

        clientLock.unlock();

        waitMillis(500);

        assertThat(clientTask2.isSuccessful()).isTrue();
    }

    @Test
    public void noDaemonThreadByDefault() {
        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(2));

        InstanceTestTaskDelegator task = new InstanceTestTaskDelegator(instance1);

        task.fixture = () -> assertThat(Thread.currentThread().isDaemon()).isFalse();

        scheduler.scheduleInstanceTask(task.task);

        waitUntil(() -> task.executions.size() == 1 && task.executions.get(0).isSuccessful());
    }

    @Test
    public void daemonThreadsWhenFlagSet() {
        scheduler = new DockerTaskScheduler(3, true, Duration.ofSeconds(2));

        InstanceTestTaskDelegator task = new InstanceTestTaskDelegator(instance1);

        task.fixture = () -> assertThat(Thread.currentThread().isDaemon()).isTrue();

        scheduler.scheduleInstanceTask(task.task);

        waitUntil(task::isSuccessful);
    }

    @Test
    public void taskExecutionFailure() {
        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(1));

        String errorMsg = "Test exception.";

        RuntimeException exception = new RuntimeException(errorMsg);

        Runnable failure = () -> { throw exception; };

        InstanceTestTaskDelegator task1 = new InstanceTestTaskDelegator(instance1);
        InstanceTestTaskDelegator task2 = new InstanceTestTaskDelegator(instance2);
        task2.fixture = failure;

        ClientTestTaskDelegator clientTask1 = new ClientTestTaskDelegator();
        ClientTestTaskDelegator clientTask2 = new ClientTestTaskDelegator();
        clientTask2.fixture = failure;

        scheduler.scheduleClientTask(clientTask1.task);

        waitUntil(clientTask1::isSuccessful);
        waitMillis(500);

        assertThat(cloudClient.getLastNotifiedFailure()).isNull();

        scheduler.scheduleClientTask(clientTask2.task);

        waitUntil(clientTask2::isFailed);
        waitMillis(500);

        assertThat(cloudClient.getLastNotifiedFailure()).isNotNull();

        scheduler.scheduleInstanceTask(task1.task);
        scheduler.scheduleInstanceTask(task2.task);

        waitUntil(() -> task1.isSuccessful() && task2.isFailed());
        waitMillis(500);

        assertThat(instance1.getErrorInfo()).isNull();
        assertThat(instance2.getErrorInfo()).isNotNull();
        assertThat(instance2.getErrorInfo().getDetailedMessage()).contains(errorMsg);
    }

    @Test
    public void interruptTaskAccordingToTimeout() {
        InstanceTestTaskDelegator task = new InstanceTestTaskDelegator(instance1);

        scheduler = new DockerTaskScheduler(3, false, Duration.ofMillis(500));

        task.fixture = () -> {
            Stopwatch sw = Stopwatch.start();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(2));
                fail("Not interrupted.");
            } catch (InterruptedException e) {
                assertThat(sw.getDuration().toMillis()).isCloseTo(500, offset(100L));
            }
        };

        scheduler.scheduleInstanceTask(task.task);

        waitUntil(task::isSuccessful);
    }

    @Test
    public void timeoutMustOnlyObserveExecutionTime() {
        InstanceTestTaskDelegator task1 = new InstanceTestTaskDelegator(instance1);
        InstanceTestTaskDelegator task2 = new InstanceTestTaskDelegator(instance1);

        // Single thread, 500ms timeout.
        scheduler = new DockerTaskScheduler(1, false, Duration.ofMillis(500));

        // First task will block for a long time (beyond timeout), second task for a short time (under timeout).
        task1.fixture = () -> waitSec(3);
        task2.fixture = () -> waitMillis(200);

        // Both task are submitted nearly simultaneously.
        scheduler.scheduleInstanceTask(task1.task);
        scheduler.scheduleInstanceTask(task2.task);

        waitUntil(() -> task1.isFailed() && task2.isSuccessful());
    }

    @Test
    public void initialDelay() {
        ClientTestTaskDelegator taskWithoutInitialDelay = new ClientTestTaskDelegator();

        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(1));

        scheduler.scheduleClientTask(taskWithoutInitialDelay.task);

        waitMillis(200);

        assertThat(taskWithoutInitialDelay.isSuccessful()).isTrue();

        ClientTestTaskDelegator taskWithInitialDelay = new ClientTestTaskDelegator(Duration.ofSeconds(2),
                Duration.ofMillis(-1));

        scheduler.scheduleClientTask(taskWithInitialDelay.task);

        assertThat(Stopwatch.measure(() -> waitUntil(taskWithInitialDelay::isSuccessful)).toMillis()).
                isCloseTo(2000, offset(150L));
    }

    @Test
    public void rescheduleDelay() {

        ClientTestTaskDelegator taskWithoutRescheduleDelay = new ClientTestTaskDelegator();

        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(1));

        Instant baseTime = Instant.now();


        scheduler.scheduleClientTask(taskWithoutRescheduleDelay.task);

        waitSec(1);

        assertThat(taskWithoutRescheduleDelay.isSuccessful()).isTrue();
        assertThat(Duration.between(baseTime, taskWithoutRescheduleDelay.executions.get(0).startTime).toMillis()).
                isBetween(0L, 150L);

        ClientTestTaskDelegator taskWithRescheduleDelay = new ClientTestTaskDelegator(Duration.ZERO, Duration
                .ofMillis(400));

        baseTime = Instant.now();

        scheduler.scheduleClientTask(taskWithRescheduleDelay.task);

        waitSec(1);

        scheduler.shutdown();

        assertThat(taskWithRescheduleDelay.executions).hasSize(3);

        taskWithoutRescheduleDelay.executions.
                forEach(execution -> assertThat(execution).matches(Execution::isSuccessful));

        assertThat(Duration.between(baseTime, taskWithRescheduleDelay.executions.get(0).startTime).toMillis()).
                isBetween(0L, 150L);
        assertThat(Duration.between(baseTime, taskWithRescheduleDelay.executions.get(1).startTime).toMillis()).
                isBetween(400L, 550L);
        assertThat(Duration.between(baseTime, taskWithRescheduleDelay.executions.get(2).startTime).toMillis()).
                isBetween(800L, 950L);
    }

    @Test
    public void failedRepeatableTaskMustBeRescheduled() {
        ClientTestTaskDelegator failingRepeatableTask = new ClientTestTaskDelegator(Duration.ZERO, Duration
                .ofMillis(400));

        failingRepeatableTask.fixture = () -> { throw new RuntimeException("Simulated failure"); };

        scheduler = new DockerTaskScheduler(3, false, Duration.ofSeconds(1));

        scheduler.scheduleClientTask(failingRepeatableTask.task);

        waitSec(1);

        assertThat(failingRepeatableTask.executions).hasSize(3);

        failingRepeatableTask.executions.
                forEach(execution -> assertThat(execution).matches(Execution::isFailed));

    }

    @After
    public void tearDown() {
        if (instanceLock.isHeldByCurrentThread()) {
            instanceLock.unlock();
        }
        if (clientLock.isHeldByCurrentThread()) {
            clientLock.unlock();
        }

        scheduler.shutdown();
    }

    private DockerImage testImage() {

        return new DockerImage(null,
                new DockerImageConfig("test", Node.EMPTY_OBJECT, true,true, true, DockerRegistryCredentials.ANONYMOUS, 1, null));
    }

    private class Execution {
        final Instant startTime = Instant.now();
        volatile boolean done = false;
        volatile Throwable failure;

        boolean isRunning() {
            return !done;
        }

        boolean isFailed() {
            return done && failure != null;
        }

        boolean isSuccessful() {
            return done && failure == null;
        }
    }

    private abstract class TestTaskDelegator {

        final Lock lock;
        final List<Execution> executions = new CopyOnWriteArrayList<>();
        volatile Runnable fixture;

        TestTaskDelegator(Lock lock) {
            this.lock = lock;
        }


        void run(Lock lock) {
            Execution execution = new Execution();
            executions.add(execution);

            lock.lock();
            try {
                if (fixture != null) {
                    fixture.run();
                }
            } catch (Throwable t) {
                execution.failure = t;
                throw t;
            } finally {
                execution.done = true;
                lock.unlock();
            }
        }

        boolean isRunning() {
            if (executions.isEmpty()) {
                return false;
            }
            assertThat(executions.size()).isEqualTo(1);
            Execution execution = executions.get(0);
            assertThat(execution.isSuccessful()).isFalse();
            assertThat(execution.isFailed()).isFalse();
            return executions.size() == 1 && executions.get(0).isRunning();
        }

        boolean isFailed() {
            if (executions.isEmpty()) {
                return false;
            }
            assertThat(executions.size()).isEqualTo(1);
            Execution execution = executions.get(0);
            assertThat(execution.isSuccessful()).isFalse();
            return execution.isFailed();
        }

        boolean isSuccessful() {
            if (executions.isEmpty()) {
                return false;
            }
            assertThat(executions.size()).isEqualTo(1);
            Execution execution = executions.get(0);
            assertThat(execution.isFailed()).isFalse();
            return execution.isSuccessful();
        }
    }

    private class ClientTestTaskDelegator extends TestTaskDelegator {

        final DockerClientTask task;

        ClientTestTaskDelegator() {
            super(clientLock);
            task = new DockerClientTask("test client task", cloudClient) {
                @Override
                void callInternal() throws Exception {
                    run(clientLock);
                }
            };
        }

        ClientTestTaskDelegator(Duration initialDelay, Duration rescheduleDelay) {
            super(clientLock);
             task = new DockerClientTask("test client task", cloudClient, initialDelay,
                     rescheduleDelay) {
                @Override
                void callInternal() throws Exception {
                    run(clientLock);
                }
            };
        }
    }

    private class InstanceTestTaskDelegator extends TestTaskDelegator {
        
        final DockerInstanceTask task;

        InstanceTestTaskDelegator(DockerInstance instance) {
            super(instanceLock);

            task = new DockerInstanceTask("test", instance, InstanceStatus.SCHEDULED_TO_START) {
                @Override
                void callInternal() throws Exception {
                    run(instanceLock);
                }
            };
        }
    }
}