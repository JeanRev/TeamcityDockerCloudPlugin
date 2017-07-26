package run.var.teamcity.cloud.docker.util;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A {@link Lock} wrapper providing additional lambda-friendly operations.
 */
public class LockHandler {

    private final ReentrantLock lock;

    private LockHandler(ReentrantLock lock) {
        assert lock != null;
        this.lock = lock;
    }

    /**
     * Invoke the specified runnable in a locked execution context.
     *
     * @param runnable the runnable
     *
     * @throws NullPointerException if {@code runnable} is {@code null}
     */
    public void run(@Nonnull Runnable runnable) {
        DockerCloudUtils.requireNonNull(runnable, "Runnable cannot be null.");
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invoke the specified checked-runnable in a locked execution context.
     *
     * @param runnable the runnable
     *
     * @throws NullPointerException if {@code runnable} is {@code null}
     * @throws E if any
     */
    public <E extends Exception> void runChecked(@Nonnull CheckedRunnable<E> runnable) throws E {
        DockerCloudUtils.requireNonNull(runnable, "Runnable cannot be null.");
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invoke the specified in a interruptible, locked, execution context.
     *
     * @param runnable the runnable
     *
     * @throws NullPointerException if {@code runnable} is {@code null}
     * @throws InterruptedException if the current thread is interrupted while waiting to acquire the lock
     */
    public void runInterruptibly(@Nonnull Runnable runnable) throws InterruptedException {
        DockerCloudUtils.requireNonNull(runnable, "Runnable cannot be null.");
        lock.lockInterruptibly();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invoke the specified value supplier in a locked execution context.
     *
     * @param supplier the value supplier
     *
     * @return the supplier value
     *
     * @throws NullPointerException if {@code supplier} is {@code null}
     */
    public <U> U call(@Nonnull Supplier<U> supplier) {
        DockerCloudUtils.requireNonNull(supplier, "Supplier cannot be null.");
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invoke the specified value supplier in a locked execution context.
     *
     * @param supplier the value supplier
     *
     * @return the supplier value
     *
     * @throws NullPointerException if {@code supplier} is {@code null}
     * @throws E if any
     */
    public <U, E extends Exception> U callChecked(CheckedSupplier<U,E> supplier) throws E {
        DockerCloudUtils.requireNonNull(supplier, "Supplier cannot be null.");
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invoke the specified value supplier in a interruptible, locked, execution context.
     *
     * @param supplier the value supplier
     *
     * @return the supplier value
     *
     * @throws NullPointerException if {@code runnable} is {@code null}
     * @throws InterruptedException if the current thread is interrupted while waiting to acquire the lock
     */
    public <U> U callInterruptibly(Supplier<U> supplier) throws InterruptedException {
        DockerCloudUtils.requireNonNull(supplier, "Supplier cannot be null.");
        lock.lockInterruptibly();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the lock wrapped by this handler, is in a locked state.
     *
     * @return {@code true} if the wrapped lock is locked
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    /**
     * Checks if the lock wrapped by this handler, is held by the current thread..
     *
     * @return {@code true} if the wrapped lock is held by the current thread
     */
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Creates a new lock handler wrapping a {@link ReentrantLock}.
     *
     * @return the new lock handler
     */
    @Nonnull
    public static LockHandler newReentrantLock() {
        return new LockHandler(new ReentrantLock());
    }
}
