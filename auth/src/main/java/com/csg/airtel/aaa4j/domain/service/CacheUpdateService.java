package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class CacheUpdateService {
    private static final Logger log = Logger.getLogger(CacheUpdateService.class);
    private static final String CLASS_NAME = "CacheUpdateService";
    private static final String M_INIT = "init";
    private static final String M_RECORD = "recordMetric";
    private static final String ACCESS_ACCEPT = "accessAcceptCount";
    private static final String ACCESS_REJECT = "accessRejectCount";
    private static final String ACCEPT_REQUEST_COUNT_CACHE_KEY = "authenticationSuccessCount";
    private static final String REJECT_REQUEST_COUNT_CACHE_KEY = "authenticationFailureCount";

    private final Counter accessAcceptCounter;
    private final Counter accessRejectCounter;
    private final ReactiveValueCommands<String, Long> redisValueCommands;
    private volatile LocalDate currentDay;
    private final AtomicLong dailyAcceptRequestCount;
    private final AtomicLong dailyRejectRequestCount;

    public CacheUpdateService(MeterRegistry registry, ReactiveRedisDataSource reactiveRedisDataSource) {
        this.redisValueCommands = reactiveRedisDataSource.value(String.class, Long.class);
        this.accessAcceptCounter = Counter.builder("access.accept.total")
                .description("Total number of Access Accept requests sent")
                .register(registry);

        this.accessRejectCounter = Counter.builder("access.reject.total")
                .description("Total number of Access Reject requests sent")
                .register(registry);
        this.currentDay = LocalDate.now();
        this.dailyAcceptRequestCount = new AtomicLong(0);
        this.dailyRejectRequestCount = new AtomicLong(0);
    }

    public void recordAccessAcceptRequest() {
        try {
            // Increment lifetime counter
            accessAcceptCounter.increment();

            // Update daily cache with automatic reset at midnight
            updateDailyAccessAcceptCount();

            LoggingUtil.logDebug(log,CLASS_NAME, M_RECORD, "Access Accept request metric recorded. Total: %.0f, Daily: %d",
                    accessAcceptCounter.count(), dailyAcceptRequestCount.get());
        } catch (Exception e) {
            LoggingUtil.logWarn(log, CLASS_NAME, M_RECORD, "Failed to record Access Accept request metric: %s", e.getMessage());
        }
    }

    private synchronized void updateDailyAccessAcceptCount() {
        LocalDate today = LocalDate.now();

        // Check if we've moved to a new day
        if (!today.equals(currentDay)) {
            currentDay = today;
            dailyAcceptRequestCount.set(0);

            // Reset Redis cache for new day
            redisValueCommands.set(ACCEPT_REQUEST_COUNT_CACHE_KEY, 0L)
                    .subscribe().with(
                            success -> LoggingUtil.logDebug(log, CLASS_NAME, ACCESS_ACCEPT, "Redis Access Accept count reset for new day"),
                            error -> LoggingUtil.logWarn(log, ACCESS_ACCEPT, "Failed to reset Redis Access Accept count: %s", error.getMessage())
                    );
        }

        // Increment daily count in memory
        long newCount = dailyAcceptRequestCount.incrementAndGet();

        // Increment Redis cache (fire and forget for performance)
        redisValueCommands.incr(ACCEPT_REQUEST_COUNT_CACHE_KEY)
                .subscribe().with(
                        redisCount -> LoggingUtil.logDebug(log, CLASS_NAME, ACCESS_ACCEPT, "Redis Access Accept count incremented (local: %d)", newCount),
                        error -> LoggingUtil.logWarn(log, CLASS_NAME, ACCESS_ACCEPT, "Failed to increment Redis Access Accept count: %s", error.getMessage())
                );
    }

    public void recordAccessRejectRequest() {
        try {
            // Increment lifetime counter
            accessRejectCounter.increment();

            // Update daily cache with automatic reset at midnight
            updateDailyAccessRejectCount();

            LoggingUtil.logDebug(log,CLASS_NAME, M_RECORD, "Access Reject request metric recorded. Total: %.0f, Daily: %d",
                    accessRejectCounter.count(), dailyRejectRequestCount.get());
        } catch (Exception e) {
            LoggingUtil.logWarn(log, CLASS_NAME, M_RECORD, "Failed to record Access Reject request metric: %s", e.getMessage());
        }
    }

    private synchronized void updateDailyAccessRejectCount() {
        LocalDate today = LocalDate.now();

        // Check if we've moved to a new day
        if (!today.equals(currentDay)) {
            currentDay = today;
            dailyRejectRequestCount.set(0);

            // Reset Redis cache for new day
            redisValueCommands.set(REJECT_REQUEST_COUNT_CACHE_KEY, 0L)
                    .subscribe().with(
                            success -> LoggingUtil.logDebug(log, CLASS_NAME, ACCESS_REJECT, "Redis Access Reject count reset for new day"),
                            error -> LoggingUtil.logWarn(log, ACCESS_REJECT, "Failed to reset Redis Access Reject count: %s", error.getMessage())
                    );
        }

        // Increment daily count in memory
        long newCount = dailyRejectRequestCount.incrementAndGet();

        // Increment Redis cache (fire and forget for performance)
        redisValueCommands.incr(REJECT_REQUEST_COUNT_CACHE_KEY)
                .subscribe().with(
                        redisCount -> LoggingUtil.logDebug(log, CLASS_NAME, ACCESS_REJECT, "Redis Access Reject count incremented (local: %d)", newCount),
                        error -> LoggingUtil.logWarn(log, CLASS_NAME, ACCESS_REJECT, "Failed to increment Redis Access Reject count: %s", error.getMessage())
                );
    }
}
