package ru.tia.reactor.android;

import java.util.concurrent.TimeUnit;

/**
 * A copy of {@link reactor.core.scheduler.SchedulerState.DisposeAwaiter}
 * to open package-private class for this package
 * @param <T>
 */
interface DisposeAwaiter<T> {

    boolean await(T resource, long timeout, TimeUnit timeUnit) throws InterruptedException;
}