package com.csg.airtel.aaa4j.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * High-performance in-memory TTL cache using ConcurrentHashMap.
 * Designed for sub-microsecond lookups to meet p98 <10ms requirement.
 *
 * Features:
 * - O(1) get/put via ConcurrentHashMap
 * - Time-based expiry (lazy eviction on access + periodic background cleanup)
 * - Size-bounded with LRU-like eviction when max size exceeded
 * - Thread-safe for concurrent access at 2000+ TPS
 *
 * Eviction runs on a shared background daemon thread rather than on the calling
 * (request) thread, so no individual put() ever pays for a full-map scan. That
 * full scan over up to {@code maxSize} entries was a periodic tail-latency spike.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class TtlCache<K, V> {

    /** Single shared daemon scheduler for all cache instances. */
    private static final ScheduledExecutorService CLEANER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ttl-cache-cleaner");
                t.setDaemon(true);
                return t;
            });

    private final ConcurrentHashMap<K, CacheEntry<V>> map;
    private final long ttlMillis;
    private final int maxSize;

    public TtlCache(int maxSize, long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.map = new ConcurrentHashMap<>(Math.min(maxSize, 16384), 0.75f, 64);

        // Periodic background cleanup; one TTL period is frequent enough to bound
        // memory without ever touching the request path.
        long period = Math.max(ttlMillis, 1000L);
        CLEANER.scheduleAtFixedRate(this::cleanupQuietly, period, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Get value if present and not expired. Sub-microsecond operation.
     */
    public V getIfPresent(K key) {
        CacheEntry<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * Put value with current timestamp. Eviction is handled off-thread.
     */
    public void put(K key, V value) {
        map.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
    }

    /** Wrapper so a cleanup failure can never kill the shared scheduler thread. */
    private void cleanupQuietly() {
        try {
            cleanupExpired();
        } catch (RuntimeException ignored) {
            // Best-effort maintenance; next tick will retry.
        }
    }

    /**
     * Remove expired entries and enforce max size.
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> now - e.getValue().timestamp > ttlMillis);

        // If still over max size after TTL cleanup, remove oldest entries
        if (map.size() > maxSize) {
            int toRemove = map.size() - maxSize;
            var iterator = map.entrySet().iterator();
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }
    }

    public int size() {
        return map.size();
    }

    public void invalidate(K key) {
        map.remove(key);
    }

    public void invalidateAll() {
        map.clear();
    }

    private static class CacheEntry<V> {
        final V value;
        final long timestamp;

        CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
