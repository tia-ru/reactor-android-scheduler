package ru.tia.reactor.android;

import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

/**
 * No-op worker that represent scheduler's shutdown state.
 */
final class ShutdownWorker implements Scheduler.Worker {
    @Override
    public Disposable schedule(Runnable task) {
        throw Exceptions.failWithRejected();
    }

    @Override
    public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
        throw Exceptions.failWithRejected();
    }

    @Override
    public Disposable schedulePeriodically(Runnable task, long initialDelay, long period, TimeUnit unit) {
        throw Exceptions.failWithRejected();
    }

    @Override
    public void dispose() {
        // NO-OP
    }

    @Override
    public boolean isDisposed() {
        return true;
    }
}
