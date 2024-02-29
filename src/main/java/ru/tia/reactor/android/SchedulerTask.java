package ru.tia.reactor.android;

import android.os.Handler;
import reactor.core.Disposable;
import reactor.util.annotation.NonNull;

final class SchedulerTask implements Runnable, Disposable {
    private final Handler handler;
    private final Runnable delegate;
    private final WorkerDelete<Disposable> workerDelete;

    private volatile boolean disposed; // Tracked solely for isDisposed().


    SchedulerTask(@NonNull Handler handler, @NonNull Runnable delegate, @NonNull WorkerDelete<Disposable> workerDelete) {
        this.handler = handler;
        this.delegate = delegate;
        this.workerDelete = workerDelete;
    }

    @Override
    public void run() {
        try {
            delegate.run();
        } catch (Throwable t) {
            AndroidSchedulers.handleError(t);
        } finally {
            disposed = true;
            workerDelete.delete(this);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        handler.removeCallbacks(this);
        workerDelete.delete(this);
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
