package com.sunrisedental.email;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NotificationScheduler {

    private final NotificationDispatcher dispatcher;
    private final int intervalSeconds;
    private final Consumer<String> logger;
    private ScheduledExecutorService executor;

    public NotificationScheduler(
            NotificationDispatcher dispatcher,
            int intervalSeconds,
            Consumer<String> logger
    ) {
        this.dispatcher = dispatcher;
        this.intervalSeconds = Math.max(10, intervalSeconds);
        this.logger = logger;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "sunrise-email-dispatcher"
                    );

                    thread.setDaemon(true);
                    return thread;
                }
        );

        executor.scheduleWithFixedDelay(
                this::dispatchSafely,
                2,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        logger.accept("Email notification scheduler started.");
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }

        executor.shutdownNow();
        executor = null;
        logger.accept("Email notification scheduler stopped.");
    }

    private void dispatchSafely() {
        try {
            NotificationDispatcher.DeliverySummary summary =
                    dispatcher.processBatch();

            if (summary.processed() > 0) {
                logger.accept(
                        "Email delivery batch: " +
                                summary.sent() + " sent, " +
                                summary.failed() + " failed."
                );
            }
        } catch (Exception exception) {
            logger.accept(
                    "Email notification batch failed: " +
                            exception.getMessage()
            );
        }
    }
}
