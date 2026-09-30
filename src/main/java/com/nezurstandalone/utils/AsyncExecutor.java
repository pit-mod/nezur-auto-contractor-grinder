package com.nezurstandalone.utils;

import java.util.concurrent.*;

/**
 * Shared thread pool executor for background tasks (network calls, skin lookups, delayed timers).
 * Prevents heavy tasks from stalling the Minecraft main render thread.
 */
public class AsyncExecutor {

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(4, new ThreadFactory() {
        private int threadCount = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "Nezur-Worker-" + (++threadCount));
            thread.setDaemon(true);
            return thread;
        }
    });

    public static Future<?> runAsync(Runnable runnable) {
        return EXECUTOR.submit(runnable);
    }

    public static <T> Future<T> submit(Callable<T> callable) {
        return EXECUTOR.submit(callable);
    }

    public static ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return EXECUTOR.schedule(runnable, delay, unit);
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        return EXECUTOR.scheduleAtFixedRate(runnable, initialDelay, period, unit);
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}
