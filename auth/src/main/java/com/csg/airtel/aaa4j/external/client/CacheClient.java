package com.csg.airtel.aaa4j.external.client;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.Duration;


@ApplicationScoped
public class CacheClient {

    private static final Logger log = Logger.getLogger(CacheClient.class);
    private static final String CLASS_NAME = "CacheClient";

    final ReactiveRedisDataSource reactiveRedisDataSource;
    final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "user:";

    @Inject
    public CacheClient(ReactiveRedisDataSource reactiveRedisDataSource, ObjectMapper objectMapper) {
        this.reactiveRedisDataSource = reactiveRedisDataSource;
        this.objectMapper = objectMapper;
        LoggingUtil.logInfo(log, CLASS_NAME, "constructor",
                "CacheClient initialized with key prefix: %s", KEY_PREFIX);
    }

    /**
     * Store user data in Redis
     */
    public Uni<Void> storeUserData(String userId, UserSessionData userData) {
        long startTime = System.currentTimeMillis();

        LoggingUtil.logInfo(log, CLASS_NAME, "storeUserData",
                "Storing user data in cache for userId: %s", userId);

        String key = KEY_PREFIX + userId;
        String jsonValue = serialize(userData);
        Uni<Void> result = reactiveRedisDataSource.value(String.class)
                .set(key, jsonValue);
        LoggingUtil.logInfo(log, CLASS_NAME, "storeUserData","User data stored Complete for userId: %s in %d ms", userId, (System.currentTimeMillis() - startTime));
        return result;
    }

    /**
     * Retrieve user data from Redis
     */
    @CircuitBreaker(
            requestVolumeThreshold = 10,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2
    )
    @Retry(
            maxRetries = 2,
            delay = 100,
            maxDuration = 5000
    )
    @Timeout(value = 5000)
    public Uni<UserSessionData> getUserData(String userId) {
        long startTime = System.currentTimeMillis();

        LoggingUtil.logInfo(log, CLASS_NAME, "getUserData",
                "Retrieving user data from cache for userId: %s", userId);

        String key = KEY_PREFIX + userId;

        return reactiveRedisDataSource.value(String.class)
                .get(key)
                .onItem().transform(Unchecked.function(jsonValue -> {
                    if (jsonValue == null || jsonValue.isEmpty()) {
                        LoggingUtil.logDebug(log, CLASS_NAME, "getUserData",
                                "No cache entry found for userId: %s", userId);
                        return null; // No record found
                    }
                    try {
                        UserSessionData userSessionData = objectMapper.readValue(jsonValue, UserSessionData.class);
                        long duration = System.currentTimeMillis() - startTime;
                        LoggingUtil.logInfo(log, CLASS_NAME, "getUserData",
                                "Successfully retrieved user data for userId: %s in %d ms",
                                userId, duration);
                        return userSessionData;
                    } catch (Exception e) {
                        LoggingUtil.logError(log, CLASS_NAME, "getUserData", e,
                                "Failed to deserialize user data for userId: %s", userId);
                        throw new BaseException(
                                "Failed to deserialize user data",
                                ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.description(),
                                Response.Status.INTERNAL_SERVER_ERROR,
                                ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.code(),
                                e.getStackTrace()
                        );
                    }
                }))
                .onFailure().invoke(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logError(log, CLASS_NAME, "getUserData", e,
                            "Failed to get user data for userId: %s after %d ms",
                            userId, duration);
                });
    }


    public Uni<Void> updateUserAndRelatedCaches(String userId, UserSessionData userData) {
        long startTime = System.currentTimeMillis();

        LoggingUtil.logInfo(log, CLASS_NAME, "updateUserAndRelatedCaches",
                "Updating user data and related caches for userId: %s", userId);

        String userKey = KEY_PREFIX + userId;

        return Uni.createFrom().item(() -> serialize(userData))
                .onItem().invoke(json ->
                        LoggingUtil.logDebug(log, CLASS_NAME, "updateUserAndRelatedCaches",
                                "Serialized data for userId: %s, key: %s", userId, userKey)
                )
                .onItem().transformToUni(serializedData ->
                        reactiveRedisDataSource.value(String.class)
                                .set(userKey, serializedData, new SetArgs().ex(Duration.ofHours(1000)))
                )
                .onItem().invoke(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logInfo(log, CLASS_NAME, "updateUserAndRelatedCaches",
                            "Cache update complete for userId: %s in %d ms",
                            userId, duration);
                })
                .onFailure().invoke(err -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logError(log, CLASS_NAME, "updateUserAndRelatedCaches", err,
                            "Failed to update cache for userId: %s after %d ms",
                            userId, duration);
                })
                .replaceWithVoid();
    }

    public Uni<String> deleteKey(String key) {
        LoggingUtil.logInfo(log, CLASS_NAME, "deleteKey",
                "Attempting to delete cache key: %s", key);

        String userKey = KEY_PREFIX + key;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();

        return keyCommands.del(userKey)
                .map(deleted -> deleted > 0
                        ? "Key deleted: " + key
                        : "Key not found: " + key);
    }



    private String serialize(UserSessionData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BaseException("Failed to deserialize user data", ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.description(), Response.Status.INTERNAL_SERVER_ERROR,ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.code(), e.getStackTrace());

        }
    }

}