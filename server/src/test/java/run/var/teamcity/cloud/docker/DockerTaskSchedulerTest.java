package run.var.teamcity.cloud.docker;

import jetbrains.buildServer.clouds.InstanceStatus;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import run.var.teamcity.cloud.docker.client.DockerRegistryCredentials;
import run.var.teamcity.cloud.docker.util.Node;
import run.var.teamcity.cloud.docker.util.Stopwatch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
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
        scheduler = new DockerTaskScheduler(3, false, 2000);

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
        scheduler = new DockerTaskScheduler(3, false, 2000);

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
        assertThat(task2b.isRunning()).isFalse();

        instanceLock.unlock();

        waitMillis(500);

        assertThat(task2b.isDone()).isTrue();
    }

    @Test
    public void clientInstanceTaskPreventInstanceTaskExecution() {
        scheduler = new DockerTaskScheduler(3, false, 2000);

        ClientTestTaskDelegator clientTask = new ClientTestTaskDelegator();
        InstanceTestTaskDelegator instanceTask = new InstanceTestTaskDelegator(instance1);

        clientLock.lock();

        scheduler.scheduleClientTask(clientTask.task);
        scheduler.scheduleInstanceTask(instanceTask.task);

        waitMillis(500);

        assertThat(clientTask.isRunning()).isTrue();
        assertThat(instanceTask.isRunning()).isFalse();

        clientLock.unlock();

        waitMillis(500);

        assertThat(instanceTask.isDone()).isTrue();
    }

    @Test
    public void multipleClientTaskAreExecutedSynchronously() {
        scheduler = new DockerTaskScheduler(3, false, 2000);

        ClientTestTaskDelegator clientTask1 = new ClientTestTaskDelegator();
        ClientTestTaskDelegator clientTask2 = new ClientTestTaskDelegator();

        clientLock.lock();

        scheduler.scheduleClientTask(clientTask1.task);
        scheduler.scheduleClientTask(clientTask2.task);

        waitMillis(500);

        assertThat(clientTask1.isRunning()).isTrue();
        assertThat(clientTask2.isRunning()).isFalse();

        clientLock.unlock();

        waitMillis(500);

        assertThat(clientTask2.isDone()).isTrue();
    }

    @Test
    public void noDaemonThreadByDefault() {
        scheduler = new DockerTaskScheduler(3, false, 2000);

        InstanceTestTaskDelegator task = new InstanceTestTaskDelegator(instance1);

        task.exec = () -> assertThat(Thread.currentThread().isDaemon()).isFalse();

        scheduler.scheduleInstanceTask(task.task);

        waitUntil(task::isDone);
    }

    @Test
    public void daemonThreadsWhenFlagSet() {
        scheduler = new DockerTaskScheduler(3, true, 2000);

        InstanceTestTaskDelegator task = new InstanceTestTaskDelegator(instance1);

        task.exec = () -> assertThat(Thread.currentThread().isDaemon()).isTrue();

        scheduler.scheduleInstanceTask(task.task);

        waitUntil(task::isDone);
    }

    @Test
    public void taskExecutionFailure() {
        scheduler = new DockerTaskScheduler(3, false, 1000);

        String errorMsg = "Test exception.";

        RuntimeException exception = new RuntimeException(errorMsg);

        Runnable failure = () -> { throw exception; };

        InstanceTestTaskDelegator task1 = new InstanceTestTaskDelegator(instance1);
        InstanceTestTaskDelegator task2 = new InstanceTestTaskDelegator(instance2);
        task2.exec = failure;

        ClientTestTaskDelegator clientTask1 = new ClientTestTaskDelegator();
        ClientTestTaskDelegator clientTask2 = new ClientTestTaskDelegator();
        clientTask2.exec = failure;

        scheduler.scheduleClientTask(clientTask1.task);

        waitUntil(clientTask1::isDone);
        waitMillis(500);

        assertThat(cloudClient.getLastNotifiedFailure()).isNull();

        scheduler.scheduleClientTask(clientTask2.task);

        waitUntil(clientTask2::isDone);
        waitMillis(500);

        assertThat(cloudClient.getLastNotifiedFailure()).isNotNull();

        scheduler.scheduleInstanceTask(task1.task);
        scheduler.scheduleInstanceTask(task2.task);

        waitUntil(() -> task1.isDone() && task2.isDone());
        waitMillis(500);

        assertThat(instance1.getErrorInfo()).isNull();
        assertThat(instance2.getErrorInfo()).isNotNull();
        assertThat(instance2.getErrorInfo().getDetailedMessage()).contains(errorMsg);
    }

    @Test
    public void interruptTaskAccordingToTimeout() {
        InstanceTestTaskDelegator task = new InstanceTestTaskDelegator(instance1);

        scheduler = new DockerTaskScheduler(3, false, 500);

        task.exec = () -> {
            Stopwatch sw = Stopwatch.start();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(2));
                fail("Not interrupted.");
            } catch (InterruptedException e) {
                assertThat(sw.millis()).isCloseTo(500, Offset.offset(100L));
            }
        };

        scheduler.scheduleInstanceTask(task.task);

        waitUntil(task::isDone);
        assertThat(task.failure).isNull();
    }

    @Test
    public void timeoutMustOnlyObserveExecutionTime() {
        InstanceTestTaskDelegator task1 = new InstanceTestTaskDelegator(instance1);
        InstanceTestTaskDelegator task2 = new InstanceTestTaskDelegator(instance1);

        // Single thread, 500ms timeout.
        scheduler = new DockerTaskScheduler(1, false, 500);

        // First task will block for a long time (beyond timeout), second task for a short time (under timeout).
        task1.exec = () -> waitSec(3);
        task2.exec = () -> waitMillis(200);

        // Both task are submitted nearly simultaneously.
        scheduler.scheduleInstanceTask(task1.task);
        scheduler.scheduleInstanceTask(task2.task);

        waitUntil(() -> task1.isDone() && task2.isDone());

        assertThat(task1.failure).isNotNull();
        assertThat(task2.failure).isNull();
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

    private enum TaskStatus {
        CREATED,
        RUNNING,
        DONE
    }

    private abstract class TestTaskDelegator {

        final Lock lock;
        volatile TaskStatus status = TaskStatus.CREATED;
        volatile Runnable exec;
        volatile Throwable failure;

        TestTaskDelegator(Lock lock) {
            this.lock = lock;
        }


        void run(Lock lock) {
            status = TaskStatus.RUNNING;

            lock.lock();
            try {
                if (exec != null) {
                    exec.run();
                }
            } catch (Throwable t) {
                failure = t;
                throw t;
            } finally {
                status = TaskStatus.DONE;
                lock.unlock();
            }
        }

        boolean isRunning() {
            return status == TaskStatus.RUNNING;
        }

        boolean isDone() {
            return status == TaskStatus.DONE;
        }
    }

    private class ClientTestTaskDelegator extends TestTaskDelegator {

        final DockerClientTask task = new DockerClientTask("test client task", cloudClient) {
            @Override
            void callInternal() throws Exception {
                run(clientLock);
            }
        };

        ClientTestTaskDelegator() {
            super(clientLock);
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