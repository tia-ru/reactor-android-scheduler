package ru.tia.reactor.android;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.Exceptions;
import reactor.core.Scannable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * При вызове {@link #dispose()} отменяются только задачи, запущенные с помощью данного {@link EventLoopWorker}
 */
final class EventLoopWorker implements Scheduler.Worker, WorkerDelete<Disposable>, Scannable {

    @NonNull
    private final Handler handler;
    @NonNull
    private final EventLoopScheduler scheduler;
    @NonNull
    private final Disposable.Composite tasks;
    private final boolean async;
    private volatile boolean shutdown = false;

    EventLoopWorker(@NonNull Handler handler, @NonNull EventLoopScheduler scheduler, boolean async) {
        this.handler = handler;
        this.scheduler = scheduler;
        this.async = async;
        this.tasks = Disposables.composite();
    }

    @Override
    @NonNull
    public Disposable schedule(@NonNull Runnable task) {
        return scheduleInternal(task, 0, TimeUnit.MILLISECONDS, true);
    }

    @Override
    @NonNull
    public Disposable schedule(@NonNull Runnable task, long delay, @NonNull TimeUnit unit) {
        return scheduleInternal(task, delay, unit, true);
    }

    @NonNull
    public Disposable schedulePeriodically(@NonNull Runnable run, long initialDelay,long period, @NonNull TimeUnit unit) {

        if (isDisposed()) throw Exceptions.failWithRejected();

        //Общий Disposable для всех повторных schedule(PeriodicTask)
        Disposable.Swap sd = new SwapDisposableThen(this::delete);
        final Runnable decoratedRun = Schedulers.onSchedule(run);

        PeriodicTask periodicTask = new PeriodicTask(decoratedRun,
                initialDelay, period, unit,
                this, scheduler, sd);

        // false, т.к. d уничтожается через sd
        Disposable d = scheduleInternal(periodicTask, initialDelay, unit , false);
        sd.replace(d);
        if (d.isDisposed() || !tasks.add(sd)) {
            sd.dispose();
            throw Exceptions.failWithRejected();
        }
        return sd;
    }

    Disposable scheduleInternal(@NonNull Runnable task, long delay, @NonNull TimeUnit unit, boolean isRequiresRegister) {
        Objects.requireNonNull(task, "run == null");
        Objects.requireNonNull(unit, "unit == null");
        if (isDisposed()) {
            throw Exceptions.failWithRejected();
        }

        task = Schedulers.onSchedule(task);
        SchedulerTask scheduled = new SchedulerTask(handler, task, this);
        if (tasks.isDisposed() || (isRequiresRegister && !tasks.add(scheduled))) {
            throw Exceptions.failWithRejected();
        }
        sendToLooper(scheduled, delay, unit);

        // Re-check disposed state for removing in case we were racing a call to dispose().
        if (tasks.isDisposed()) {
            scheduled.dispose();
            throw Exceptions.failWithRejected();
        }

        return scheduled;
    }

    @Override
    public void dispose() {
        shutdown = true;
        if (!tasks.isDisposed()) {
            handler.removeCallbacksAndMessages(this /* token */);
            tasks.dispose();
            scheduler.delete(this);
        }
    }

    @Override
    public boolean isDisposed() {
        return shutdown || tasks.isDisposed();
    }

    @Override
    public void delete(Disposable r) {
        tasks.remove(r);
        if (shutdown && tasks.size() == 0) {
            dispose();
        }
    }

    @Override
    public Object scanUnsafe(@NonNull Attr key) {
        if (key == Attr.TERMINATED ) return tasks.isDisposed();
        if (key == Attr.CANCELLED) return shutdown;
        if (key == Attr.BUFFERED) return tasks.size();
        if (key == Attr.PARENT) return scheduler;
        if (key == Attr.NAME) {
            //hack to recognize the SingleWorker
            //if (scheduler instanceof SingleWorkerScheduler) return scheduler + ".worker";
            return scheduler + ".worker";
        }

        //return Schedulers.scanExecutor(executor, key);
        return ((Scannable) scheduler).scanUnsafe(key);

    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    void disposeGracefully() {
        shutdown = true;
    }

    @SuppressLint("NewApi") // Async will only be true when the API is available to call.
    private void sendToLooper(SchedulerTask task, long delay, TimeUnit unit) {
        Message message = Message.obtain(handler, task);
        message.obj = this; // Used as token for batch disposal of this worker's runnables.

        if (async) {
            message.setAsynchronous(true);
        }

        handler.sendMessageDelayed(message, unit.toMillis(delay));
    }
}
