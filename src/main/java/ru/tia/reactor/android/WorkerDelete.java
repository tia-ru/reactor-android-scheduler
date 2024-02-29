package ru.tia.reactor.android;

/**
 * A copy of {@link reactor.core.scheduler.ExecutorScheduler.WorkerDelete}
 */
interface WorkerDelete<T> {

    void delete(T r);
}