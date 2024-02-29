package ru.tia.reactor.android;

import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A copy of {@link reactor.core.scheduler.SchedulerState.DisposeAwaiterRunnable}
 * to open package-private class for this package
 * @param <T>
 */
class DisposeAwaiterRunnable<T> implements Runnable {

    static final ScheduledExecutorService TRANSITION_AWAIT_POOL;

    static {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(0);
        executor.setKeepAliveTime(10, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        executor.setMaximumPoolSize(Schedulers.DEFAULT_POOL_SIZE);
        TRANSITION_AWAIT_POOL = executor;
    }

    private final DisposeAwaiter<T> awaiter;
    private final T                 initial;
    private final int               awaitMs;
    private final FluxSink<Void> sink;

    volatile boolean cancelled;

    static <R> void awaitInPool(DisposeAwaiter<R> awaiter, R initial, FluxSink<Void> sink, int awaitMs) {
        DisposeAwaiterRunnable<R> poller = new DisposeAwaiterRunnable<>(awaiter, initial, sink, awaitMs);
        TRANSITION_AWAIT_POOL.submit(poller);
    }

    DisposeAwaiterRunnable(DisposeAwaiter<T> awaiter, T initial, FluxSink<Void> sink, int awaitMs) {
        this.awaiter = awaiter;
        this.initial = initial;
        this.sink = sink;
        this.awaitMs = awaitMs;
        //can only call onCancel once so we rely on DisposeAwaiterRunnable#cancel
        sink.onCancel(this::cancel);
    }

    private void cancel() {
        cancelled = true;
        //we don't really care about the future. next round we'll abandon the task
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }
        try {
            if (awaiter.await(initial, awaitMs, TimeUnit.MILLISECONDS)) {
                sink.complete();
            }
            else {
                if (cancelled) {
                    return;
                }
                // trampoline
                TRANSITION_AWAIT_POOL.submit(this);
            }
        }
        catch (InterruptedException e) {
            //NO-OP
        }
    }
}