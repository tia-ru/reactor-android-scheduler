package ru.tia.reactor.android;

import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.util.annotation.NonNull;

import java.util.concurrent.TimeUnit;

final class PeriodicTask implements Runnable {

    private final EventLoopWorker worker;
    private final Runnable decoratedRun;
    private final Scheduler clock;
    private final Disposable.Swap sd;
    private final long periodMs;

    long count;
    long lastNowMs;
    long startAtMs;
    /**
     * The tolerance for a clock drift in milliseconds where the periodic scheduler will rebase.
     * <p>
     * Associated system parameters:
     * <ul>
     * <li>{@code adocs.scheduler.drift-tolerance}, long, default {@code 15}</li>
     * <li>{@code adocs.scheduler.drift-tolerance-unit}, string, default {@code minutes},
     *     supports {@code seconds} and {@code milliseconds}.
     * </ul>
     */
    static final long CLOCK_DRIFT_TOLERANCE_MILLISECONDS =
            computeClockDrift(
                    Long.getLong("android.scheduler.drift-tolerance", 15),
                    System.getProperty("android.scheduler.drift-tolerance-unit", "minutes")
            );

    /**
     * Returns the clock drift tolerance in milliseconds based on the input selection.
     *
     * @param time     the time value
     * @param timeUnit the time unit string
     * @return the time amount in milliseconds
     */
    private static long computeClockDrift(long time, String timeUnit) {
        if ("seconds".equalsIgnoreCase(timeUnit)) {
            return TimeUnit.SECONDS.toMillis(time);
        } else if ("milliseconds".equalsIgnoreCase(timeUnit)) {
            return time;
        }
        return TimeUnit.MINUTES.toMillis(time);
    }

    PeriodicTask(@NonNull Runnable decoratedRun,
                 long initialDelay, long period, @NonNull TimeUnit unit,
                 EventLoopWorker worker, Scheduler clock, @NonNull Disposable.Swap sd) {

        this.decoratedRun = decoratedRun;
        this.worker = worker;
        this.clock = clock;
        this.sd = sd;

        this.periodMs = unit.toMillis(period);
        this.lastNowMs = clock.now(TimeUnit.MILLISECONDS);
        this.startAtMs = lastNowMs + unit.toMillis(initialDelay);
    }

    @Override
    public void run() {
        try {

            decoratedRun.run();

            //worker.isDisposed() == true когда он в состоянии SHUTDOWN, ожидая завершения поставленных в очередь задач.
            if (!sd.isDisposed() && !worker.isDisposed()) {
                scheduleNext();
            }
        } catch (Throwable e) {
            sd.dispose();
            throw e;
        }
    }

    private void scheduleNext() {
        long nextTick;
        count++;
        long nowMs = clock.now(TimeUnit.MILLISECONDS);
        // If the clock moved in a direction quite a bit, rebase the repetition period
        if (nowMs + CLOCK_DRIFT_TOLERANCE_MILLISECONDS < lastNowMs
                || nowMs >= lastNowMs + periodMs + CLOCK_DRIFT_TOLERANCE_MILLISECONDS) {

            nextTick = nowMs + periodMs;
            /*
             * Shift the start point back by the drift as if the whole thing
             * started count periods ago.
             */
            startAtMs = nextTick - (periodMs * count);
        } else {
            nextTick = startAtMs + (count * periodMs);
        }
        lastNowMs = nowMs;

        long delay = nextTick - nowMs;

        // false, т.к. next уничтожается в worker через sd
        Disposable next = worker.scheduleInternal(this, delay, TimeUnit.MILLISECONDS, false);
        // Не sd.update, т.к. излишне вызывать dispose у предыдущего ScheduledRunnable и next не утекает
        sd.replace(next);
    }
}