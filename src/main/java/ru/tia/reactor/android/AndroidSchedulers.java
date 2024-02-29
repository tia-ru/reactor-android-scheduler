package ru.tia.reactor.android;

import android.os.Looper;
import android.os.Message;
import reactor.core.Exceptions;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/** Android-specific Schedulers. */
public enum AndroidSchedulers {
    ;

    private static final Logger LOGGER = Loggers.getLogger(Schedulers.class);

    private static final MethodHandle handleErrorMh = getHandleErrorMh();

    private static final class MainHolder {
        static final CachedScheduler DEFAULT;

        static {
            Looper looper = Looper.getMainLooper();
            DEFAULT = new CachedScheduler("mainThread", from(looper, true));
        }
    }

    /**
     * A {@link Scheduler} which executes actions on the Android main thread.
     * <p>
     * The returned scheduler will post asynchronous messages to the main thread looper.
     * <p>
     * Only one instance of this common scheduler will be created on the first call and is cached. The same instance
     * is returned on subsequent calls until it is disposed.
     * <p>
     * One cannot directly {@link Scheduler#dispose() dispose} the common instances, as they are cached and shared
     * between callers. They can however be all {@link #shutdownNow() shut down} together.
     *
     * @see #newMainThread()
     * @see #from(Looper, boolean)
     */
    @NonNull
    public static Scheduler mainThread() {
        return MainHolder.DEFAULT;
    }

    /**
     * Creates new {@link Scheduler} which executes actions on the Android main thread looper.
     * <p>
     * The returned scheduler will post asynchronous messages.
     *
     * @see #from(Looper, boolean)
     */
    @NonNull
    public static Scheduler newMainThread() {
        Looper looper = Looper.getMainLooper();
        return from(looper, true);
    }

    /**
     * Creates new {@link Scheduler} which executes actions on the Android main thread looper.
     * <p>
     * @param async if true, the scheduler will use async messaging to avoid VSYNC locking.
     *
     * @see #from(Looper, boolean)
     */
    @NonNull
    public static Scheduler newMainThread(boolean async) {
        Looper looper = Looper.getMainLooper();
        return from(looper, async);
    }

    /**
     * A {@link Scheduler} which executes actions on {@code looper}.
     * <p>
     * The returned scheduler will post asynchronous messages to the looper by default.
     *
     * @see #from(Looper, boolean)
     */
    @NonNull
    public static Scheduler from(@NonNull Looper looper) {
        return from(looper, true);
    }

    /**
     * A {@link Scheduler} which executes actions on {@code looper}.
     *
     * @param async if true, the scheduler will use async messaging to avoid VSYNC
     *              locking.
     * @see Message#setAsynchronous(boolean)
     */
    @NonNull
    public static Scheduler from(@NonNull Looper looper, boolean async) {
        Objects.requireNonNull(looper, "looper == null");
        return new EventLoopScheduler(looper, async);
    }

    /**
     * Replace {@link Schedulers} factory {@link Schedulers#newSingle(String)} and {@link Schedulers#single()}
     * by {@link #newMainThread()}. So {@code .publishOn(Schedulers.single())} will run on Android main thread.
     * <p>
     * It allows test reactive chains using {@code StepVerifier.withVirtualTime().publishOn(Schedulers.single())} on
     * Android main thread.
     * <p>
     * The method also shutdown Schedulers from the cached factories (like {@link Schedulers#single()}) in order to
     * also use these replacements, re-creating the shared schedulers from the new factory
     * upon next use.
     * <p>
     * This method should be called safely and with caution, typically on app startup.
     */
    public static void installMainThreadAsSingle(){
        Schedulers.Factory factory = new Schedulers.Factory() {
            @NonNull
            public Scheduler newSingle(@NonNull ThreadFactory threadFactory) {
                return newMainThread();
            }
        };
        Schedulers.setFactory(factory);
    }

    /**
     * Clear any cached {@link Scheduler} and call dispose on them.
     */
    public static void shutdownNow() {
        MainHolder.DEFAULT._dispose();
    }

    static void handleError(Throwable ex) {
        boolean doFallback = (handleErrorMh == null);
        if (!doFallback) {
            try {
                handleErrorMh.invokeExact(ex);
            } catch (Throwable ignore) {
                doFallback = true;
            }
        }
        if (doFallback) {
            Thread thread = Thread.currentThread();
            Throwable t = Exceptions.unwrap(ex);
            Thread.UncaughtExceptionHandler x = thread.getUncaughtExceptionHandler();
            if (x != null) {
                x.uncaughtException(thread, t);
            } else {
                LOGGER.error("Scheduler worker failed with an uncaught exception", t);
            }
        }

    }


    @Nullable
    private static MethodHandle getHandleErrorMh() {
        MethodHandle handleErrorMh = null;

        try {
            Method method = Schedulers.class.getDeclaredMethod("handleError", Throwable.class);
            method.setAccessible(true);
            handleErrorMh = MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException ignore) {
            //ignore.printStackTrace();
        }
        return handleErrorMh;
    }
}
