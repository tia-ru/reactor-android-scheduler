package ru.tia.reactor.android;

import reactor.core.Disposable;
import reactor.core.Scannable;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *  A copy of {@link reactor.core.scheduler.Schedulers.CachedScheduler}
 *  to open package-private class for this package
 */
class CachedScheduler implements Scheduler, Supplier<Scheduler>, Scannable {

    final Scheduler cached;
    final String    stringRepresentation;

    CachedScheduler(String key, Scheduler cached) {
        this.cached = cached;
        this.stringRepresentation = "AndroidSchedulers." + key + "()";
    }

    @Override
    public Disposable schedule(Runnable task) {
        return cached.schedule(task);
    }

    @Override
    public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
        return cached.schedule(task, delay, unit);
    }

    @Override
    public Disposable schedulePeriodically(Runnable task,
                                           long initialDelay,
                                           long period,
                                           TimeUnit unit) {
        return cached.schedulePeriodically(task, initialDelay, period, unit);
    }

    @Override
    public Worker createWorker() {
        return cached.createWorker();
    }

    @Override
    public long now(TimeUnit unit) {
        return cached.now(unit);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void start() {
        cached.start();
    }

    @Override
    public void init() {
        cached.init();
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean isDisposed() {
        return cached.isDisposed();
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }

    @Override
    public Object scanUnsafe(Attr key) {
        if (Attr.NAME == key) return stringRepresentation;
        return Scannable.from(cached).scanUnsafe(key);
    }

    /**
     * Get the {@link Scheduler} that is cached and wrapped inside this
     * {@link CachedScheduler}.
     *
     * @return the cached Scheduler
     */
    @Override
    public Scheduler get() {
        return cached;
    }

    void _dispose() {
        cached.dispose();
    }
}