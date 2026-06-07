package com.csg.airtel.aaa4j.common.util;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe TPS (Transactions Per Second) rate limiter.
 * Optimized for high-throughput (3000+ TPS) by using lock-free atomics
 * and minimizing logging overhead in the hot path.
 */
@ApplicationScoped
public class TPSRateLimiter {
    private static final Logger logger = Logger.getLogger(TPSRateLimiter.class);

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong currentSecond = new AtomicLong(System.currentTimeMillis() / 1000);

    @Getter
    @ConfigProperty(name = "radius.max-tps", defaultValue = "3000")
    int maxTPS;

    public TPSRateLimiter(@ConfigProperty(name = "radius.max-tps", defaultValue = "3000") int maxTPS) {
        this.maxTPS = maxTPS;
    }

    /**
     * Attempts to acquire a request slot within TPS limit.
     * @param traceId for logging purposes
     * @return true if request is allowed, false if TPS limit exceeded
     */
    public boolean tryAcquire(String traceId) {
        long now = System.currentTimeMillis() / 1000;
        long current = currentSecond.get();

        // New second - reset counter
        if (now > current && currentSecond.compareAndSet(current, now)) {
            requestCount.set(1);
            if (logger.isDebugEnabled()) {
                logger.debugf("[%s] TPS counter reset for new second. Count: 1/%d", traceId, maxTPS);
            }
            return true;
        }

        // Same second - increment counter
        int count = requestCount.incrementAndGet();

        if (count <= maxTPS) {
            if (logger.isDebugEnabled()) {
                logger.debugf("[%s] TPS check PASSED. Current: %d/%d", traceId, count, maxTPS);
            }
            return true;
        }

        // Exceeded limit - keep error level since this is an important operational event
        logger.errorf("[%s] TPS LIMIT EXCEEDED! Current: %d/%d", traceId, count, maxTPS);
        return false;
    }

    /**
     * Get current TPS count
     */
    public int getCurrentCount() {
        return requestCount.get();
    }

}
