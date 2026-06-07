//package com.csg.airtel.aaa4j.domain.service;
//
//import com.csg.airtel.aaa4j.domain.constant.Constants;
//import com.csg.airtel.aaa4j.exception.BaseException;
//import com.csg.airtel.aaa4j.external.repository.BNGRepository;
//import io.quarkus.redis.datasource.ReactiveRedisDataSource;
//import io.quarkus.redis.datasource.value.ReactiveValueCommands;
//import io.smallrye.mutiny.Uni;
//import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.slf4j.MDC;
//
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//class CacheSchedulerServiceTest {
//
//    @Mock
//    private BNGRepository bngRepository;
//
//    @Mock
//    private ReactiveRedisDataSource redisDataSource;
//
//    @Mock
//    private ReactiveValueCommands<String, String> stringRedisCommands;
//
//    @Mock
//    private ReactiveValueCommands<String, List> listRedisCommands;
//
//    private CacheSchedulerService cacheSchedulerService;
//
//    private static final String TEST_TRACE_ID = "test-trace-123";
//    private static final String TEST_BNG_CODE = "BARRED_RULE_001";
//    private static final String BARRED_PLAN_CACHE_KEY = "barred_plan";
//    private static final String ACTIVE_NAS_IPS_CACHE_KEY = "active_nas_ips";
//    private static final Long DEFAULT_TTL = 90000L;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//
//        // Setup Redis data source mocks
//        when(redisDataSource.value(String.class)).thenReturn(stringRedisCommands);
//        when(redisDataSource.value(List.class)).thenReturn(listRedisCommands);
//
//        cacheSchedulerService = new CacheSchedulerService(bngRepository, redisDataSource);
//
//        // Set TTL via reflection
//        setField(cacheSchedulerService, "cacheTtlSeconds", DEFAULT_TTL);
//
//        // Setup MDC
//        MDC.put(Constants.TRACE_ID, TEST_TRACE_ID);
//    }
//
//    // ==================== Barred Plan Cache Tests ====================
//
//    @Nested
//    @DisplayName("Barred Plan Cache Tests")
//    class BarredPlanCacheTests {
//
//        @Test
//        @DisplayName("Should retrieve barred status rule from cache successfully")
//        void testGetBarredStatusRule_Success() {
//            // Arrange
//            when(stringRedisCommands.get(BARRED_PLAN_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().item(TEST_BNG_CODE));
//
//            // Act
//            Uni<String> result = cacheSchedulerService.getBarredStatusRule();
//
//            // Assert
//            String bngCode = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(bngCode);
//            assertEquals(TEST_BNG_CODE, bngCode);
//            verify(stringRedisCommands).get(BARRED_PLAN_CACHE_KEY);
//        }
//
//        @Test
//        @DisplayName("Should return null when barred status rule not found in cache")
//        void testGetBarredStatusRule_NotFound() {
//            // Arrange
//            when(stringRedisCommands.get(BARRED_PLAN_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().nullItem());
//
//            // Act
//            Uni<String> result = cacheSchedulerService.getBarredStatusRule();
//
//            // Assert
//            String bngCode = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNull(bngCode);
//            verify(stringRedisCommands).get(BARRED_PLAN_CACHE_KEY);
//        }
//
//        @Test
//        @DisplayName("Should return null when barred status rule is empty in cache")
//        void testGetBarredStatusRule_EmptyString() {
//            // Arrange
//            when(stringRedisCommands.get(BARRED_PLAN_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().item(""));
//
//            // Act
//            Uni<String> result = cacheSchedulerService.getBarredStatusRule();
//
//            // Assert
//            String bngCode = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNull(bngCode);
//        }
//
//        @Test
//        @DisplayName("Should handle Redis failure when getting barred status rule")
//        void testGetBarredStatusRule_RedisFailure() {
//            // Arrange
//            RuntimeException exception = new RuntimeException("Redis connection error");
//            when(stringRedisCommands.get(BARRED_PLAN_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().failure(exception));
//
//            // Act & Assert
//            cacheSchedulerService.getBarredStatusRule()
//                    .subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitFailure()
//                    .assertFailedWith(BaseException.class);
//
//            verify(stringRedisCommands).get(BARRED_PLAN_CACHE_KEY);
//        }
//    }
//
//    // ==================== NAS IPs Cache Tests ====================
//
//    @Nested
//    @DisplayName("NAS IPs Cache Tests")
//    class NasIpsCacheTests {
//
//        @Test
//        @DisplayName("Should retrieve active NAS IPs from cache successfully")
//        void testGetActiveNasIps_Success() {
//            // Arrange
//            List<String> nasIps = Arrays.asList("192.168.1.1", "192.168.1.2", "192.168.1.3");
//            when(listRedisCommands.get(ACTIVE_NAS_IPS_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().item(nasIps));
//
//            // Act
//            Uni<List> result = cacheSchedulerService.getActiveNasIps();
//
//            // Assert
//            List retrievedIps = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(retrievedIps);
//            assertEquals(3, retrievedIps.size());
//            assertEquals(nasIps, retrievedIps);
//            verify(listRedisCommands).get(ACTIVE_NAS_IPS_CACHE_KEY);
//        }
//
//        @Test
//        @DisplayName("Should return null when NAS IPs not found in cache")
//        void testGetActiveNasIps_NotFound() {
//            // Arrange
//            when(listRedisCommands.get(ACTIVE_NAS_IPS_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().nullItem());
//
//            // Act
//            Uni<List> result = cacheSchedulerService.getActiveNasIps();
//
//            // Assert
//            List retrievedIps = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNull(retrievedIps);
//            verify(listRedisCommands).get(ACTIVE_NAS_IPS_CACHE_KEY);
//        }
//
//        @Test
//        @DisplayName("Should return null when NAS IPs list is empty in cache")
//        void testGetActiveNasIps_EmptyList() {
//            // Arrange
//            when(listRedisCommands.get(ACTIVE_NAS_IPS_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().item(Collections.emptyList()));
//
//            // Act
//            Uni<List> result = cacheSchedulerService.getActiveNasIps();
//
//            // Assert
//            List retrievedIps = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNull(retrievedIps);
//        }
//
//        @Test
//        @DisplayName("Should handle Redis failure when getting active NAS IPs")
//        void testGetActiveNasIps_RedisFailure() {
//            // Arrange
//            RuntimeException exception = new RuntimeException("Redis connection error");
//            when(listRedisCommands.get(ACTIVE_NAS_IPS_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().failure(exception));
//
//            // Act & Assert
//            cacheSchedulerService.getActiveNasIps()
//                    .subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitFailure()
//                    .assertFailedWith(BaseException.class);
//
//            verify(listRedisCommands).get(ACTIVE_NAS_IPS_CACHE_KEY);
//        }
//
//        @Test
//        @DisplayName("Should retrieve single NAS IP successfully")
//        void testGetActiveNasIps_SingleIp() {
//            // Arrange
//            List<String> nasIps = Collections.singletonList("192.168.1.1");
//            when(listRedisCommands.get(ACTIVE_NAS_IPS_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().item(nasIps));
//
//            // Act
//            Uni<List> result = cacheSchedulerService.getActiveNasIps();
//
//            // Assert
//            List retrievedIps = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(retrievedIps);
//            assertEquals(1, retrievedIps.size());
//            assertEquals("192.168.1.1", retrievedIps.get(0));
//        }
//    }
//
//    // ==================== Unified Cache Update Tests ====================
//
//    @Nested
//    @DisplayName("Unified Cache Update Tests")
//    class UnifiedCacheUpdateTests {
//
//        @Test
//        @DisplayName("Should update both caches successfully in parallel")
//        void testUpdateAllCaches_BothSucceed() {
//            // Arrange
//            List<String> nasIps = Arrays.asList("192.168.1.1", "192.168.1.2");
//
//            when(bngRepository.getBarredPlanRule())
//                    .thenReturn(Uni.createFrom().item(TEST_BNG_CODE));
//            when(bngRepository.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().item(nasIps));
//            when(stringRedisCommands.setex(BARRED_PLAN_CACHE_KEY, DEFAULT_TTL, TEST_BNG_CODE))
//                    .thenReturn(Uni.createFrom().voidItem());
//            when(listRedisCommands.setex(ACTIVE_NAS_IPS_CACHE_KEY, DEFAULT_TTL, nasIps))
//                    .thenReturn(Uni.createFrom().voidItem());
//
//            // Act
//            cacheSchedulerService.updateAllCaches();
//
//            // Assert
//            verify(bngRepository).getBarredPlanRule();
//            verify(bngRepository).getActiveNasIps();
//            verify(stringRedisCommands).setex(BARRED_PLAN_CACHE_KEY, DEFAULT_TTL, TEST_BNG_CODE);
//            verify(listRedisCommands).setex(ACTIVE_NAS_IPS_CACHE_KEY, DEFAULT_TTL, nasIps);
//        }
//
//        @Test
//        @DisplayName("Should handle partial failure - barred plan fails, NAS IPs succeeds")
//        void testUpdateAllCaches_BarredPlanFails() {
//            // Arrange
//            List<String> nasIps = Arrays.asList("192.168.1.1", "192.168.1.2");
//
//            when(bngRepository.getBarredPlanRule())
//                    .thenReturn(Uni.createFrom().failure(new RuntimeException("Barred plan error")));
//            when(bngRepository.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().item(nasIps));
//            when(listRedisCommands.setex(ACTIVE_NAS_IPS_CACHE_KEY, DEFAULT_TTL, nasIps))
//                    .thenReturn(Uni.createFrom().voidItem());
//
//            // Act
//            cacheSchedulerService.updateAllCaches();
//
//            // Assert
//            verify(bngRepository).getBarredPlanRule();
//            verify(bngRepository).getActiveNasIps();
//            verify(listRedisCommands).setex(ACTIVE_NAS_IPS_CACHE_KEY, DEFAULT_TTL, nasIps);
//        }
//
//        @Test
//        @DisplayName("Should handle partial failure - NAS IPs fails, barred plan succeeds")
//        void testUpdateAllCaches_NasIpsFails() {
//            // Arrange
//            when(bngRepository.getBarredPlanRule())
//                    .thenReturn(Uni.createFrom().item(TEST_BNG_CODE));
//            when(bngRepository.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().failure(new RuntimeException("NAS IPs error")));
//            when(stringRedisCommands.setex(BARRED_PLAN_CACHE_KEY, DEFAULT_TTL, TEST_BNG_CODE))
//                    .thenReturn(Uni.createFrom().voidItem());
//
//            // Act
//            cacheSchedulerService.updateAllCaches();
//
//            // Assert
//            verify(bngRepository).getBarredPlanRule();
//            verify(bngRepository).getActiveNasIps();
//            verify(stringRedisCommands).setex(BARRED_PLAN_CACHE_KEY, DEFAULT_TTL, TEST_BNG_CODE);
//        }
//
//        @Test
//        @DisplayName("Should handle complete failure - both updates fail")
//        void testUpdateAllCaches_BothFail() {
//            // Arrange
//            when(bngRepository.getBarredPlanRule())
//                    .thenReturn(Uni.createFrom().failure(new RuntimeException("Barred plan error")));
//            when(bngRepository.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().failure(new RuntimeException("NAS IPs error")));
//
//            // Act
//            cacheSchedulerService.updateAllCaches();
//
//            // Assert
//            verify(bngRepository).getBarredPlanRule();
//            verify(bngRepository).getActiveNasIps();
//            verify(stringRedisCommands, never()).setex(anyString(), anyLong(), anyString());
//            verify(listRedisCommands, never()).setex(anyString(), anyLong(), anyList());
//        }
//    }
//
//    // ==================== Exception Mapping Tests ====================
//
//    @Nested
//    @DisplayName("Exception Mapping Tests")
//    class ExceptionMappingTests {
//
//        @Test
//        @DisplayName("Should map runtime exception to BaseException")
//        void testMapToCacheException_RuntimeException() {
//            // Arrange
//            RuntimeException exception = new RuntimeException("Redis error");
//            when(stringRedisCommands.get(BARRED_PLAN_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().failure(exception));
//
//            // Act & Assert
//            UniAssertSubscriber<String> subscriber = cacheSchedulerService.getBarredStatusRule()
//                    .subscribe().withSubscriber(UniAssertSubscriber.create());
//
//            Throwable failure = subscriber.awaitFailure().getFailure();
//
//            assertInstanceOf(BaseException.class, failure);
//            assertTrue(failure.getMessage().contains("Cache operation failed"));
//        }
//
//        @Test
//        @DisplayName("Should not wrap BaseException")
//        void testMapToCacheException_BaseException() {
//            // Arrange
//            BaseException baseException = new BaseException(
//                    "Cache error",
//                    "CACHE_ERROR",
//                    jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR,
//                    "500",
//                    null
//            );
//            when(stringRedisCommands.get(BARRED_PLAN_CACHE_KEY))
//                    .thenReturn(Uni.createFrom().failure(baseException));
//
//            // Act & Assert
//            UniAssertSubscriber<String> subscriber = cacheSchedulerService.getBarredStatusRule()
//                    .subscribe().withSubscriber(UniAssertSubscriber.create());
//
//            Throwable failure = subscriber.awaitFailure().getFailure();
//
//            assertInstanceOf(BaseException.class, failure);
//            assertSame(baseException, failure);
//        }
//    }
//
//    // ==================== Helper Methods ====================
//
//    private void setField(Object target, String fieldName, Object value) {
//        try {
//            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
//            field.setAccessible(true);
//            field.set(target, value);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to set field: " + fieldName, e);
//        }
//    }
//}