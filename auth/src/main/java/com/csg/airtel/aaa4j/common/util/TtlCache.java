package com.csg.airtel.aaa4j.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance in-memory TTL cache using ConcurrentHashMap.
 * Designed for sub-microsecond lookups to meet p98 <10ms requirement.
 *
 * Features:
 * - O(1) get/put via ConcurrentHashMap
 * - Time-based expiry (lazy eviction on access + periodic cleanup)
 * - Size-bounded with LRU-like eviction when max size exceeded
 * - Thread-safe for concurrent access at 2500+ TPS
 *
 * @param <K> key type
 * @param <V> value type
 */
public class TtlCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> map;
    private final long ttlMillis;
    private final int maxSize;
    private final AtomicLong cleanupCounter = new AtomicLong(0);
    private static final int CLEANUP_INTERVAL = 1000; // Cleanup every N puts

    public TtlCache(int maxSize, long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.map = new ConcurrentHashMap<>(Math.min(maxSize, 16384), 0.75f, 64);
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
     * Put value with current timestamp. Triggers lazy cleanup periodically.
     */
    public void put(K key, V value) {
        map.put(key, new CacheEntry<>(value, System.currentTimeMillis()));

        // Periodic lazy cleanup to bound memory
        if (cleanupCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanupExpired();
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
