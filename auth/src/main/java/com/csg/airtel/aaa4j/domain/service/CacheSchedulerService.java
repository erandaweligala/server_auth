package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.application.config.PoolConfig;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.Constants;
import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.VendorAttributeConfig;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.csg.airtel.aaa4j.external.repository.BNGRepository;
import com.csg.airtel.aaa4j.metrics.service.RootCauseMetricsService;
import com.csg.airtel.aaa4j.metrics.tracker.RootCauseExceptionTracker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class CacheSchedulerService {
    private static final Logger LOG = Logger.getLogger(CacheSchedulerService.class);
    private static final String CLASS_NAME = "CacheSchedulerService";

    private final BNGRepository bngRepository;
    private final ReactiveValueCommands<String, String> stringRedisCommands;
    private final ReactiveValueCommands<String, List> listRedisCommands;
    private final ObjectMapper objectMapper;

    // === IN-MEMORY CACHES (eliminate Redis round-trips on hot path) ===
    private final AtomicReference<String> inMemoryBarredRule = new AtomicReference<>();
    private final AtomicReference<Set<String>> inMemoryActiveNasIps = new AtomicReference<>(Collections.emptySet());
    private final AtomicReference<Map<String, VendorAttributeConfig>> inMemoryVendorConfigMap = new AtomicReference<>(Collections.emptyMap());

    @ConfigProperty(name = "bng.cache.ttl.seconds", defaultValue = "90000")
    Long cacheTtlSeconds;

    private static final String BARRED_PLAN_BNG_CODE_CACHE_KEY = "barred_plan";
    private static final String ACTIVE_NAS_IPS_CACHE_KEY = "active_nas_ips";
    private static final String VENDOR_CONFIG_CACHE_KEY = "vendor_configs";
    private final Pool dbPool;
    private final PoolConfig poolConfig;
    private final RootCauseMetricsService exceptionMetrics;
    private final RootCauseExceptionTracker exceptionTracker;

    @Inject
    public CacheSchedulerService(BNGRepository bngRepository,
                                 ReactiveRedisDataSource redisDataSource,
                                 ObjectMapper objectMapper, Pool dbPool, PoolConfig poolConfig, RootCauseMetricsService exceptionMetrics,
                                 RootCauseExceptionTracker exceptionTracker) {
        this.bngRepository = bngRepository;
        this.stringRedisCommands = redisDataSource.value(String.class);
        this.listRedisCommands = redisDataSource.value(List.class);
        this.objectMapper = objectMapper;
        this.dbPool = dbPool;
        this.poolConfig = poolConfig;
        this.exceptionMetrics = exceptionMetrics;
        this.exceptionTracker = exceptionTracker;
    }

    void onStart(@Observes StartupEvent ev) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "onStart", "Pre-loading in-memory caches at startup");
        updateAllCaches();
        warmUpConnectionPool();
    }

    @Scheduled(every = "${cache-update.scheduler-interval:5m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void updateAllCaches() {
        long startTime = System.currentTimeMillis();
        String traceId = MDC.get(Constants.TRACE_ID);

        LoggingUtil.logInfo(LOG, CLASS_NAME, "updateAllCaches",
                "Unified cache update initiated - traceId: %s", traceId);

        // Update all three caches in parallel
        Uni.combine().all()
                .unis(
                        updateBarredPlanCache(traceId),
                        updateNasIpsCache(traceId),
                        updateVendorConfigCache(traceId)
                )
                .asTuple()
                .subscribe().with(
                        tuple -> {
                            long duration = System.currentTimeMillis() - startTime;
                            LoggingUtil.logInfo(LOG, CLASS_NAME, "updateAllCaches",
                                    "Unified cache update completed in %dms - traceId: %s",
                                    duration, traceId);
                        },
                        failure -> {
                            long duration = System.currentTimeMillis() - startTime;
                            LoggingUtil.logError(LOG, CLASS_NAME, "updateAllCaches", failure,
                                    "Unified cache update failed after %dms - traceId: %s",
                                    duration, traceId);
                        }
                );
    }

    private Uni<Void> updateBarredPlanCache(String traceId) {
        return bngRepository.getBarredPlanRule()
                .onItem().transformToUni(bngCode -> {
                    if (bngCode == null || bngCode.trim().isEmpty()) {
                        LoggingUtil.logWarn(LOG, CLASS_NAME, "updateBarredPlanCache",
                                "No BNG code found for barred plan - traceId: %s", traceId);
                        return Uni.createFrom().voidItem();
                    }

                    // Update in-memory cache immediately
                    inMemoryBarredRule.set(bngCode);

                    LoggingUtil.logDebug(LOG, CLASS_NAME, "updateBarredPlanCache",
                            "Updating barred plan cache with BNG code: %s - traceId: %s",
                            bngCode, traceId);

                    return stringRedisCommands.setex(BARRED_PLAN_BNG_CODE_CACHE_KEY, cacheTtlSeconds, bngCode)
                            .onItem().invoke(success ->
                                    LoggingUtil.logDebug(LOG, CLASS_NAME, "updateBarredPlanCache",
                                            "Barred plan cache updated successfully - traceId: %s", traceId))
                            .replaceWithVoid();
                })
                .onFailure().invoke(e -> {
                    LoggingUtil.logError(LOG, CLASS_NAME, "updateBarredPlanCache", e,
                            "Barred plan cache update failed - traceId: %s", traceId);
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "updateBarredPlanCache");
                })
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    private Uni<Void> updateNasIpsCache(String traceId) {
        return bngRepository.getActiveNasIps()
                .onItem().transformToUni(nasIpList -> {
                    if (nasIpList == null || nasIpList.isEmpty()) {
                        LoggingUtil.logWarn(LOG, CLASS_NAME, "updateNasIpsCache",
                                "No active NAS IPs found to cache - traceId: %s", traceId);
                        return Uni.createFrom().voidItem();
                    }

                    // Update in-memory HashSet for O(1) lookup (case-insensitive)
                    Set<String> nasIpSet = new HashSet<>(nasIpList.size());
                    for (String ip : nasIpList) {
                        nasIpSet.add(ip.toLowerCase());
                    }
                    inMemoryActiveNasIps.set(Collections.unmodifiableSet(nasIpSet));

                    LoggingUtil.logDebug(LOG, CLASS_NAME, "updateNasIpsCache",
                            "Updating NAS IPs cache with %d entries - traceId: %s",
                            nasIpList.size(), traceId);

                    return listRedisCommands.setex(ACTIVE_NAS_IPS_CACHE_KEY, cacheTtlSeconds, nasIpList)
                            .onItem().invoke(success ->
                                    LoggingUtil.logDebug(LOG, CLASS_NAME, "updateNasIpsCache",
                                            "NAS IPs cache updated successfully with %d entries - traceId: %s",
                                            nasIpList.size(), traceId))
                            .replaceWithVoid();
                })
                .onFailure().invoke(e -> {
                    LoggingUtil.logError(LOG, CLASS_NAME, "updateNasIpsCache", e,
                            "NAS IPs cache update failed - traceId: %s", traceId);
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "updateNasIpsCache");
                })
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    // NEW METHOD: Update vendor config cache using JSON serialization
    private Uni<Void> updateVendorConfigCache(String traceId) {
        return bngRepository.getActiveNasWithVendorConfigs()
                .onItem().transformToUni(vendorConfigs -> {
                    if (vendorConfigs == null || vendorConfigs.isEmpty()) {
                        LoggingUtil.logWarn(LOG, CLASS_NAME, "updateVendorConfigCache",
                                "No vendor configurations found to cache - traceId: %s", traceId);
                        return Uni.createFrom().voidItem();
                    }

                    // Update in-memory map indexed by NAS IP for O(1) lookup
                    Map<String, VendorAttributeConfig> configMap = new HashMap<>(vendorConfigs.size());
                    for (VendorAttributeConfig config : vendorConfigs) {
                        configMap.put(config.getNasIpAddress(), config);
                    }
                    inMemoryVendorConfigMap.set(Collections.unmodifiableMap(configMap));

                    LoggingUtil.logDebug(LOG, CLASS_NAME, "updateVendorConfigCache",
                            "Updating vendor config cache with %d NAS entries - traceId: %s",
                            vendorConfigs.size(), traceId);

                    try {
                        // Serialize to JSON
                        String jsonValue = objectMapper.writeValueAsString(vendorConfigs);

                        return stringRedisCommands.setex(
                                        VENDOR_CONFIG_CACHE_KEY,
                                        cacheTtlSeconds,
                                        jsonValue
                                )
                                .onItem().invoke(success -> {
                                    int totalAttributes = vendorConfigs.stream()
                                            .mapToInt(c -> c.getAttributes().size())
                                            .sum();
                                    LoggingUtil.logDebug(LOG, CLASS_NAME, "updateVendorConfigCache",
                                            "Vendor config cache updated: %d NAS entries, %d total attributes - traceId: %s",
                                            vendorConfigs.size(), totalAttributes, traceId);
                                })
                                .replaceWithVoid();

                    } catch (Exception e) {
                        LoggingUtil.logError(LOG, CLASS_NAME, "updateVendorConfigCache", e,
                                "Failed to serialize vendor configs - traceId: %s", traceId);
                        return Uni.createFrom().failure(e);
                    }
                })
                .onFailure().invoke(e -> {
                    LoggingUtil.logError(LOG, CLASS_NAME, "updateVendorConfigCache", e,
                            "Vendor config cache update failed - traceId: %s", traceId);
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "updateVendorConfigCache");
                })
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    /**
     * Get Barred status rule from in-memory cache (zero latency).
     * Falls back to Redis if in-memory is empty.
     */
    public Uni<String> getBarredStatusRule() {
        // Fast path: in-memory lookup (nanoseconds)
        String cached = inMemoryBarredRule.get();
        if (cached != null) {
            LoggingUtil.logTrace(LOG, CLASS_NAME, "getBarredStatusRule",
                    "In-memory hit: barred rule=%s", cached);
            return Uni.createFrom().item(cached);
        }

        // Fallback to Redis if in-memory not yet populated
        return stringRedisCommands.get(BARRED_PLAN_BNG_CODE_CACHE_KEY)
                .onItem().transform(bngCode -> {
                    if (bngCode != null && !bngCode.trim().isEmpty()) {
                        inMemoryBarredRule.set(bngCode);
                    }
                    return bngCode;
                })
                .onFailure().transform(this::mapToCacheException);
    }

    /**
     * Get active NAS IPs from in-memory cache (zero latency, O(1) lookup).
     * Falls back to Redis if in-memory is empty.
     */
    public Uni<Set<String>> getActiveNasIps() {
        // Fast path: in-memory lookup
        Set<String> cached = inMemoryActiveNasIps.get();
        if (cached != null && !cached.isEmpty()) {
            return Uni.createFrom().item(cached);
        }

        // Fallback to Redis
        return listRedisCommands.get(ACTIVE_NAS_IPS_CACHE_KEY)
                .onItem().transform(cachedList -> {
                    if (cachedList != null && !cachedList.isEmpty()) {
                        Set<String> nasIpSet = new HashSet<>(cachedList.size());
                        for (Object ip : cachedList) {
                            nasIpSet.add(ip.toString().toLowerCase());
                        }
                        inMemoryActiveNasIps.set(Collections.unmodifiableSet(nasIpSet));
                        return Collections.unmodifiableSet(nasIpSet);
                    }
                    return Collections.<String>emptySet();
                })
                .onFailure().transform(this::mapToCacheException);
    }

    /**
     * Check if a NAS IP is active using in-memory HashSet (O(1) lookup, zero latency).
     */
    public boolean isActiveNasIp(String nasIp) {
        if (nasIp == null) return false;
        Set<String> cached = inMemoryActiveNasIps.get();
        return cached != null && cached.contains(nasIp.toLowerCase());
    }

    /**
     * Get vendor config for specific NAS IP from in-memory HashMap (O(1) lookup, zero latency).
     * Falls back to Redis if in-memory is empty.
     */
    public Uni<VendorAttributeConfig> getVendorConfigByNasIp(String nasIp) {
        // Fast path: in-memory O(1) HashMap lookup (nanoseconds)
        Map<String, VendorAttributeConfig> configMap = inMemoryVendorConfigMap.get();
        if (configMap != null && !configMap.isEmpty()) {
            VendorAttributeConfig config = configMap.get(nasIp);
            LoggingUtil.logTrace(LOG, CLASS_NAME, "getVendorConfigByNasIp",
                    "In-memory lookup NAS=%s found=%s", nasIp, config != null);
            return Uni.createFrom().item(config);
        }

        // Fallback: fetch from Redis if in-memory not populated yet
        LoggingUtil.logDebug(LOG, CLASS_NAME, "getVendorConfigByNasIp",
                "In-memory miss, falling back to Redis for NAS=%s", nasIp);

        return stringRedisCommands.get(VENDOR_CONFIG_CACHE_KEY)
                .onItem().transform(jsonValue -> {
                    if (jsonValue == null || jsonValue.trim().isEmpty()) {
                        return null;
                    }
                    try {
                        List<VendorAttributeConfig> configs = objectMapper.readValue(
                                jsonValue, new TypeReference<List<VendorAttributeConfig>>() {});
                        // Populate in-memory cache
                        Map<String, VendorAttributeConfig> map = new HashMap<>(configs.size());
                        for (VendorAttributeConfig c : configs) {
                            map.put(c.getNasIpAddress(), c);
                        }
                        inMemoryVendorConfigMap.set(Collections.unmodifiableMap(map));
                        return map.get(nasIp);
                    } catch (Exception e) {
                        LoggingUtil.logError(LOG, CLASS_NAME, "getVendorConfigByNasIp", e,
                                "Failed to deserialize vendor configs");
                        return null;
                    }
                })
                .onFailure().transform(this::mapToCacheException);
    }

    private Throwable mapToCacheException(Throwable e) {
        if (e instanceof BaseException) {
            return e;
        }

        LoggingUtil.logError(LOG, CLASS_NAME, "mapToCacheException", e,
                "Cache operation failed: %s", e.getMessage());
        return new BaseException(
                "Cache operation failed: " + e.getMessage(),
                ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.description(),
                Response.Status.INTERNAL_SERVER_ERROR,
                ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.code(),
                e.getStackTrace()
        );
    }
    private void warmUpConnectionPool() {
        LoggingUtil.logInfo(LOG, CLASS_NAME, "warmUpConnectionPool",
                "Warming up Oracle connection pool with %d connections", poolConfig.maxSize());
        try {
            List<Uni<RowSet<Row>>> warmupQueries = new ArrayList<>();
            for (int i = 0; i < poolConfig.maxSize(); i++) {
                warmupQueries.add(
                        dbPool.preparedQuery("SELECT 1 FROM DUAL").execute()
                );
            }

            Uni.combine().all()
                    .unis(warmupQueries)
                    .usingConcurrencyOf(poolConfig.maxSize())
                    .discardItems()
                    .await().atMost(Duration.ofSeconds(30));

            LoggingUtil.logDebug(LOG, CLASS_NAME, "warmUpConnectionPool",
                    "Connection pool warmed up successfully");
        } catch (Exception e) {
            LoggingUtil.logError(LOG, CLASS_NAME, "warmUpConnectionPool", e,
                    "Pool warm-up failed, continuing startup");
        }
    }
}