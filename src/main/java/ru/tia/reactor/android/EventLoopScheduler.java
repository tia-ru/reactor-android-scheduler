package ru.tia.reactor.android;

import android.os.Handler;
import android.os.Looper;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.Exceptions;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

final class EventLoopScheduler implements Scheduler, Scannable, WorkerDelete<EventLoopWorker>
        , DisposeAwaiter<Set<EventLoopWorker>> {

    private static final ShutdownWorker SHUTDOWN = new ShutdownWorker();
    private final Handler handler;
    private final boolean async;
    private final Set<EventLoopWorker> workers = new HashSet<>();

    private volatile Worker worker;

    EventLoopScheduler(Looper looper, boolean async) {
        this.handler = new Handler(looper);
        this.async = async;
        EventLoopWorker eventLoopWorker = createWorker();
        this.worker = eventLoopWorker;
        workers.add(eventLoopWorker);  //всегда держится 1 внутренний worker до EventLoopScheduler#dispose
    }

    @Override
    public void init() {
        if (worker == SHUTDOWN) throw new IllegalStateException("Initializing a disposed scheduler is not permitted");
    }

    @Override
    @NonNull
    public Disposable schedule(@NonNull Runnable task) {
        return worker.schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    @NonNull
    public Disposable schedule(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
        return worker.schedule(run, delay, unit);
    }


    /**
     * Schedules a periodic execution of the given task with the given initial time delay and repeat period.
     *
     * <p>
     * This method is safe to be called from multiple threads but there are no
     * ordering guarantees between tasks.
     * <p>
     * If task throw an exception then it be disposed and not be executed anymore.
     * <p>
     * The periodic execution is at a fixed rate, that is, the first execution will be after the
     * {@code initialDelay}, the second after {@code initialDelay + period}, the third after
     * {@code initialDelay + 2 * period}, and so on.
     *
     * @param task         the task to schedule
     * @param initialDelay the initial delay amount, non-positive values indicate non-delayed scheduling
     * @param period       the period at which the task should be re-executed
     * @param unit         the unit of measure of the delay amount
     * @return the Disposable that let's one cancel this particular delayed task.
     * @throws NullPointerException if {@code run} or {@code unit} is {@code null}
     * @since 2.0
     */
    //schedulePeriodicallyDirect
    @Override
    @NonNull
    public Disposable schedulePeriodically(@NonNull Runnable task, long initialDelay, long period, @NonNull TimeUnit unit) {
        return worker.schedulePeriodically(task, initialDelay, period, unit);
    }

    @Override
    @NonNull
    public EventLoopWorker createWorker() {
        if (isDisposed()) throw Exceptions.failWithRejected();
        EventLoopWorker newWorker = new EventLoopWorker(handler, this, async);
        workers.add(newWorker);
        return newWorker;
    }

    @Override
    public long now(@NonNull TimeUnit unit) {
        return Scheduler.super.now(unit);
    }

    @Override
    public Object scanUnsafe(@NonNull Attr key) {
        if (key == Attr.TERMINATED) return isDisposed();
        if (key == Attr.CANCELLED) return worker == SHUTDOWN;
        if (key == Attr.NAME) return this.toString();
        if (key == Attr.CAPACITY) return 1;
        if (key == Attr.BUFFERED) return workers.size();

        return null;
    }

    @Override
    public void dispose() {
        worker = SHUTDOWN;
        Composite composite = Disposables.composite(workers);
        workers.clear();
        composite.dispose();
    }

    /**
     * Subscription initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>To wait for previously submitted tasks to
     * complete execution use {@link #disposeGracefully()}.block()
     * <p>
     * NOTICE: Periodical tasks will stop after next invocation only.
     * <p>
     * To force hard shutdown after timeout for gracefully shutdown  use
     * <pre>scheduler
     *   .disposeGracefully()
     *   .timeout(Duration.ofSeconds(1))
     *   .onErrorResume(e -> Mono.fromRunnable(scheduler::dispose))
     *   .block();</pre>
     */
    @Override
    @NonNull
    public Mono<Void> disposeGracefully() {
        return Mono.defer(() -> {
            worker = SHUTDOWN;
            for (EventLoopWorker w : workers) {
                w.disposeGracefully();
            }

            return Flux.<Void>create(sink -> DisposeAwaiterRunnable.awaitInPool(this, workers, sink, 100))
                    .replay()
                    .refCount()
                    .next();
        });
    }

    @Override
    public boolean await(Set<EventLoopWorker> workers, long timeout, TimeUnit timeUnit) throws InterruptedException {

        long startMs = now(TimeUnit.MILLISECONDS);
        long timeoutMs = timeUnit.toMillis(timeout);

        boolean hasTasks;
        long durationMs;
        do {
            hasTasks = false;
            for (EventLoopWorker worker : workers) {
                Integer activeTaskCount = worker.scan(Attr.BUFFERED);
                if (activeTaskCount != null && activeTaskCount > 0) {
                    hasTasks = true;
                    break;
                }
            }
            durationMs = now(TimeUnit.MILLISECONDS) - startMs;
            long toSleepMs = (timeoutMs - durationMs) / 10;
            toSleepMs = Math.min(toSleepMs, 50);
            if (hasTasks && toSleepMs > 0) {
                Thread.sleep(toSleepMs);
            }
        } while (hasTasks && durationMs < timeoutMs);

        return !hasTasks;
    }

    @Override
    public boolean isDisposed() {
        return worker == SHUTDOWN && workers.isEmpty();
    }

    @Override
    @NonNull
    public String toString() {

        return "eventLoop(" + handler.getLooper().getThread().getName() + ")";
    }

    @Override
    public void delete(EventLoopWorker r) {
        workers.remove(r);
    }

    @Override
    @NonNull
    public Stream<? extends Scannable> inners() {
        return workers.stream();         
    }
}