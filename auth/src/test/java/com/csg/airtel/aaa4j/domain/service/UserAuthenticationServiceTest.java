//package com.csg.airtel.aaa4j.domain.service;
//
//import com.csg.airtel.aaa4j.common.strategy.AuthenticationStrategy;
//import com.csg.airtel.aaa4j.common.strategy.AuthenticationStrategyFactory;
//import com.csg.airtel.aaa4j.domain.constant.Constants;
//import com.csg.airtel.aaa4j.domain.model.*;
//import com.csg.airtel.aaa4j.domain.model.session.Balance;
//import com.csg.airtel.aaa4j.domain.model.session.ConsumptionRecord;
//import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
//import com.csg.airtel.aaa4j.exception.BaseException;
//import com.csg.airtel.aaa4j.external.client.CacheClient;
//import com.csg.airtel.aaa4j.external.repository.UserAuthenticationRepository;
//import io.smallrye.mutiny.Uni;
//import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//class UserAuthenticationServiceTest {
//
//    @Mock
//    private UserAuthenticationRepository userAuthenticationRepository;
//
//    @Mock
//    private AuthenticationStrategyFactory authStrategyFactory;
//
//    @Mock
//    private AuthenticationStrategy authenticationStrategy;
//
//    @Mock
//    private CacheClient cacheClient;
//
//    @Mock
//    private CacheSchedulerService cacheSchedulerService;
//
//    private UserAuthenticationService userAuthenticationService;
//
//    private static final String TEST_USERNAME = "testuser";
//    private static final String TEST_PASSWORD = "testpass123";
//    private static final String TEST_NAS_IP = "192.168.1.1";
//    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
//    private static final String CHAP_PASSWORD = "chappass";
//    private static final String CHAP_CHALLENGE = "challenge123";
//    private static final String TEST_BUCKET_ID = "bucket123";
//    private static final String TEST_RULE = "test-rule";
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        userAuthenticationService = new UserAuthenticationService(
//                userAuthenticationRepository,
//                authStrategyFactory,
//                cacheClient,
//                cacheSchedulerService
//        );
//
//        // Set configuration properties via reflection
//        setField(userAuthenticationService, "userAttributes", "attr1,attr2,attr3");
//        setField(userAuthenticationService, "isCheckBucket", true);
//
//        // Mock NAS IP validation by default
//        when(cacheSchedulerService.getActiveNasIps())
//                .thenReturn(Uni.createFrom().item(List.of(TEST_NAS_IP)));
//    }
//
//    // ==================== Successful Authentication Tests ====================
//
//    @Nested
//    @DisplayName("Successful Authentication Tests")
//    class SuccessfulAuthenticationTests {
//
//        @Test
//        @DisplayName("Should authenticate user with PAP protocol successfully")
//        void testUserAuthenticate_PapProtocol_Success() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//            UserDetails expectedUser = createUserDetails(TEST_USERNAME, true, true);
//
//            mockSuccessfulAuthentication(dbDetails, expectedUser, Constants.PAP_PROTOCOL);
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsActive());
//            assertTrue(userDetails.getIsAuthorized());
//            assertEquals(TEST_USERNAME, userDetails.getUsername());
//            verify(userAuthenticationRepository).getDbDetails(eq(TEST_USERNAME), anyList());
//            verify(authStrategyFactory).getStrategy(Constants.PAP_PROTOCOL);
//        }
//
//        @Test
//        @DisplayName("Should authenticate user with CHAP protocol successfully")
//        void testUserAuthenticate_ChapProtocol_Success() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, CHAP_PASSWORD, CHAP_CHALLENGE);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//            UserDetails expectedUser = createUserDetails(TEST_USERNAME, true, true);
//
//            mockSuccessfulAuthentication(dbDetails, expectedUser, Constants.CHAP_PROTOCOL);
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsActive());
//            assertTrue(userDetails.getIsAuthorized());
//            verify(authStrategyFactory).getStrategy(Constants.CHAP_PROTOCOL);
//        }
//
//        @Test
//        @DisplayName("Should authenticate successfully with unlimited bucket")
//        void testUserAuthenticate_UnlimitedBucket_Success() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//
//            BucketDetails bucket = createBucketDetails(TEST_BUCKET_ID, 0L, TEST_RULE, 1L);
//            bucket.setIsUnlimited(1); // Unlimited bucket
//            dbDetails.setBucketDetails(List.of(bucket));
//
//            UserDetails expectedUser = createUserDetails(TEST_USERNAME, true, true);
//            mockSuccessfulAuthentication(dbDetails, expectedUser, Constants.PAP_PROTOCOL);
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsAuthorized());
//        }
//    }
//
//    // ==================== Authorization Failure Tests ====================
//
//    @Nested
//    @DisplayName("Authorization Failure Tests")
//    class AuthorizationFailureTests {
//
//        @Test
//        @DisplayName("Should fail authentication when NAS IP mismatch")
//        void testUserAuthenticate_NasIpMismatch_Unauthorized() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            request.setNasIpAddress("192.168.1.2");
//
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//            when(cacheSchedulerService.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().item(List.of(TEST_NAS_IP)));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsActive());
//            assertFalse(userDetails.getIsAuthorized());
//        }
//
//        @Test
//        @DisplayName("Should fail authentication when NAS IP cache is empty")
//        void testUserAuthenticate_EmptyNasIpCache_Unauthorized() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//            when(cacheSchedulerService.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().item(Collections.emptyList()));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertFalse(userDetails.getIsAuthorized());
//        }
//
//        @Test
//        @DisplayName("Should fail authentication when insufficient balance")
//        void testUserAuthenticate_InsufficientBalance_Unauthorized() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//
//            BucketDetails bucket = createBucketDetails(TEST_BUCKET_ID, 0L, TEST_RULE, 1L);
//            dbDetails.setBucketDetails(Collections.singletonList(bucket));
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertFalse(userDetails.getIsAuthorized());
//            assertFalse(userDetails.getIsEnoughBalance());
//        }
//
//        @Test
//        @DisplayName("Should fail authentication when user status is inactive")
//        void testUserAuthenticate_InactiveUser_Unauthorized() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, "INACTIVE");
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertFalse(userDetails.getIsAuthorized());
//        }
//
//        @Test
//        @DisplayName("Should handle barred user with barred rule")
//        void testUserAuthenticate_BarredUser_WithRule() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_BARRED);
//            Map<String, String> attributes = new HashMap<>();
//            attributes.put("attr1", "value1");
//            dbDetails.setAttributes(attributes);
//
//            String barredRule = "barred-rule";
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//            when(cacheSchedulerService.getBarredStatusRule())
//                    .thenReturn(Uni.createFrom().item(barredRule));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsAuthorized());
//            assertTrue(userDetails.getIsActive());
//            assertTrue(userDetails.getIsEnoughBalance());
//            assertEquals(barredRule, userDetails.getRule());
//            assertEquals(attributes, userDetails.getAttributes());
//        }
//
//        @Test
//        @DisplayName("Should handle barred user without barred rule")
//        void testUserAuthenticate_BarredUser_WithoutRule() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_BARRED);
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//            when(cacheSchedulerService.getBarredStatusRule())
//                    .thenReturn(Uni.createFrom().item((String) null));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertNull(userDetails.getRule());
//        }
//
//        @Test
//        @DisplayName("Should fail MAC authentication when username does not match password")
//        void testUserAuthenticate_MacAddress_UsernamePasswordMismatch() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_MAC_ADDRESS, "differentpassword", null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_MAC_ADDRESS, TEST_MAC_ADDRESS, Constants.STATUS_ACTIVE);
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_MAC_ADDRESS), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsActive());
//            assertFalse(userDetails.getIsAuthorized());
//            assertTrue(userDetails.getIsEnoughBalance());
//        }
//    }
//
//    // ==================== Balance and Bucket Selection Tests ====================
//
//    @Nested
//    @DisplayName("Balance and Bucket Selection Tests")
//    class BalanceAndBucketSelectionTests {
//
//        @Test
//        @DisplayName("Should return null when no eligible buckets found")
//        void testGetBalanceWithConsumptionCheck_NoEligibleBuckets_ReturnsNull() {
//            // Arrange
//            List<BucketDetails> buckets = Collections.emptyList();
//
//            when(cacheClient.getUserData(TEST_USERNAME))
//                    .thenReturn(Uni.createFrom().item(createUserSessionData()));
//
//            // Act
//            Uni<BucketDetails> result = userAuthenticationService.getBalanceWithConsumptionCheck(buckets, TEST_USERNAME);
//
//            // Assert
//            BucketDetails selected = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            assertNull(selected);
//        }
//    }
//
//    // ==================== Time Window Tests ====================
//
//    @Nested
//    @DisplayName("Time Window Tests")
//    class TimeWindowTests {
//
//        @Test
//        @DisplayName("Should validate time window within normal range")
//        void testIsWithinTimeWindow_NormalRange_ValidatesCorrectly() {
//            // Current time is between 8 and 18
//            String timeWindow = "8-18";
//
//            // This test depends on current time, so we'll test the logic
//            boolean result = userAuthenticationService.isWithinTimeWindow(timeWindow);
//
//            // Result depends on actual current time
//            assertTrue(result);
//        }
//
//        @Test
//        @DisplayName("Should handle time window crossing midnight")
//        void testIsWithinTimeWindow_CrossesMidnight() {
//            // Window from 22:00 to 06:00
//            String timeWindow = "22-6";
//
//            boolean result = userAuthenticationService.isWithinTimeWindow(timeWindow);
//
//            assertFalse(result);
//        }
//
//        @Test
//        @DisplayName("Should handle 24-hour window")
//        void testIsWithinTimeWindow_24HourWindow() {
//            String timeWindow = "0-24";
//
//            boolean result = userAuthenticationService.isWithinTimeWindow(timeWindow);
//
//            assertTrue(result);
//        }
//
//        @Test
//        @DisplayName("Should throw exception for invalid time window format")
//        void testIsWithinTimeWindow_InvalidFormat_ThrowsException() {
//            String invalidWindow = "8";
//
//            assertThrows(IllegalArgumentException.class, () -> {
//                userAuthenticationService.isWithinTimeWindow(invalidWindow);
//            });
//        }
//
//        @Test
//        @DisplayName("Should throw exception for null time window")
//        void testIsWithinTimeWindow_NullWindow_ThrowsException() {
//            assertThrows(IllegalArgumentException.class, () -> {
//                userAuthenticationService.isWithinTimeWindow(null);
//            });
//        }
//
//        @Test
//        @DisplayName("Should throw exception for empty time window")
//        void testIsWithinTimeWindow_EmptyWindow_ThrowsException() {
//            assertThrows(IllegalArgumentException.class, () -> {
//                userAuthenticationService.isWithinTimeWindow("");
//            });
//        }
//    }
//
//    // ==================== Consumption Limit Tests ====================
//
//    @Nested
//    @DisplayName("Consumption Limit Tests")
//    class ConsumptionLimitTests {
//
//        @Test
//        @DisplayName("Should calculate consumption within 24-hour window")
//        void testCalculateConsumptionInWindow_24HourWindow() {
//            // Arrange
//            Balance balance = createBalance(TEST_BUCKET_ID, 1000L, 24L);
//
//            LocalDateTime now = LocalDateTime.now();
//            ConsumptionRecord record1 = new ConsumptionRecord(now.minusHours(2), 100L);
//            ConsumptionRecord record2 = new ConsumptionRecord(now.minusHours(5), 200L);
//            ConsumptionRecord record3 = new ConsumptionRecord(now.minusHours(30), 500L); // Outside window
//
//            balance.setConsumptionHistory(Arrays.asList(record1, record2, record3));
//
//            // Act
//            long consumption = userAuthenticationService.calculateConsumptionInWindow(balance, 24L);
//
//            // Assert
//            assertEquals(300L, consumption); // Only record1 and record2
//        }
//
//        @Test
//        @DisplayName("Should calculate consumption within 12-hour window")
//        void testCalculateConsumptionInWindow_12HourWindow() {
//            // Arrange
//            Balance balance = createBalance(TEST_BUCKET_ID, 1000L, 12L);
//
//            LocalDateTime now = LocalDateTime.now();
//            ConsumptionRecord record1 = new ConsumptionRecord(now.minusHours(2), 100L);
//            ConsumptionRecord record2 = new ConsumptionRecord(now.minusHours(15), 200L); // Outside window
//
//            balance.setConsumptionHistory(Arrays.asList(record1, record2));
//
//            // Act
//            long consumption = userAuthenticationService.calculateConsumptionInWindow(balance, 12L);
//
//            // Assert
//            assertEquals(100L, consumption); // Only record1
//        }
//
//        @Test
//        @DisplayName("Should return zero when no consumption history")
//        void testCalculateConsumptionInWindow_NoHistory_ReturnsZero() {
//            // Arrange
//            Balance balance = createBalance(TEST_BUCKET_ID, 1000L, 24L);
//            balance.setConsumptionHistory(null);
//
//            // Act
//            long consumption = userAuthenticationService.calculateConsumptionInWindow(balance, 24L);
//
//            // Assert
//            assertEquals(0L, consumption);
//        }
//    }
//
//    // ==================== Exception Handling Tests ====================
//
//    @Nested
//    @DisplayName("Exception Handling Tests")
//    class ExceptionHandlingTests {
//
//        @Test
//        @DisplayName("Should handle repository exception and map to BaseException")
//        void testUserAuthenticate_RepositoryException_MapsToBaseException() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            RuntimeException exception = new RuntimeException("Database error");
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().failure(exception));
//
//            // Act & Assert
//            userAuthenticationService.userAuthenticate(request)
//                    .subscribe()
//                    .withSubscriber(UniAssertSubscriber.create())
//                    .awaitFailure()
//                    .assertFailedWith(BaseException.class);
//        }
//
//        @Test
//        @DisplayName("Should propagate BaseException without wrapping")
//        void testUserAuthenticate_BaseException_NotWrapped() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            BaseException baseException = new BaseException(
//                    "Authentication failed",
//                    "AUTH_ERROR",
//                    jakarta.ws.rs.core.Response.Status.UNAUTHORIZED,
//                    "401",
//                    null
//            );
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().failure(baseException));
//
//            // Act & Assert
//            userAuthenticationService.userAuthenticate(request)
//                    .subscribe()
//                    .withSubscriber(UniAssertSubscriber.create())
//                    .awaitFailure()
//                    .assertFailedWith(BaseException.class);
//        }
//
//        @Test
//        @DisplayName("Should handle NAS IP validation failure gracefully")
//        void testUserAuthenticate_NasIpValidationFailure_RecoverWithFalse() {
//            // Arrange
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//
//            when(userAuthenticationRepository.getDbDetails(eq(TEST_USERNAME), anyList()))
//                    .thenReturn(Uni.createFrom().item(dbDetails));
//            when(cacheSchedulerService.getActiveNasIps())
//                    .thenReturn(Uni.createFrom().failure(new RuntimeException("Cache error")));
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//            assertFalse(userDetails.getIsAuthorized());
//        }
//    }
//
//// ==================== Configuration Tests ====================
//
//    @Nested
//    @DisplayName("Configuration Tests")
//    class ConfigurationTests {
//
//        @Test
//        @DisplayName("Should skip bucket check when disabled")
//        void testUserAuthenticate_BucketCheckDisabled_SkipsBucketLogic() {
//            // Arrange
//            setField(userAuthenticationService, "isCheckBucket", false);
//
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//
//            // Set zero balance
//            BucketDetails bucket = createBucketDetails(TEST_BUCKET_ID, 0L, TEST_RULE, 1L);
//            dbDetails.setBucketDetails(List.of(bucket));
//
//            UserDetails expectedUser = createUserDetails(TEST_USERNAME, true, true);
//            mockSuccessfulAuthentication(dbDetails, expectedUser, Constants.PAP_PROTOCOL);
//
//            // Act
//            Uni<UserDetails> result = userAuthenticationService.userAuthenticate(request);
//
//            // Assert
//            UserDetails userDetails = result.subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem()
//                    .getItem();
//
//            // Should succeed even with zero balance when bucket check is disabled
//            assertNotNull(userDetails);
//            assertTrue(userDetails.getIsAuthorized());
//            assertNull(userDetails.getRule()); // No rule assigned when bucket check disabled
//        }
//
//        @Test
//        @DisplayName("Should parse user attributes correctly")
//        void testGetAttributesToFetch_ParsesCorrectly() {
//            // Arrange
//            setField(userAuthenticationService, "userAttributes", "attr1, attr2 ,attr3");
//
//            // This is tested indirectly through authentication
//            AuthenticationRequest request = createAuthRequest(TEST_USERNAME, TEST_PASSWORD, null, null);
//            AuthenticationDbDetails dbDetails = createDbDetails(TEST_USERNAME, TEST_PASSWORD, Constants.STATUS_ACTIVE);
//            UserDetails expectedUser = createUserDetails(TEST_USERNAME, true, true);
//
//            mockSuccessfulAuthentication(dbDetails, expectedUser, Constants.PAP_PROTOCOL);
//
//            // Act
//            userAuthenticationService.userAuthenticate(request)
//                    .subscribe().withSubscriber(UniAssertSubscriber.create())
//                    .awaitItem();
//
//            // Assert
//            verify(userAuthenticationRepository).getDbDetails(eq(TEST_USERNAME), argThat(list ->
//                    list.contains("attr1") && list.contains("attr2") && list.contains("attr3")
//            ));
//        }
//    }
//
//// ==================== Helper Methods ====================
//
//    private AuthenticationRequest createAuthRequest(String username, String password,
//                                                    String chapPassword, String chapChallenge) {
//        AuthenticationRequest request = new AuthenticationRequest();
//        request.setUsername(username);
//        request.setPassword(password);
//        request.setChapPassword(chapPassword);
//        request.setChapChallenge(chapChallenge);
//        request.setNasIpAddress(TEST_NAS_IP);
//        return request;
//    }
//
//    private AuthenticationDbDetails createDbDetails(String username, String password, String status) {
//        AuthenticationDbDetails dbDetails = new AuthenticationDbDetails();
//        dbDetails.setUserName(username);
//        dbDetails.setPassword(password);
//        dbDetails.setStatus(status);
//        dbDetails.setNasIpAddress(TEST_NAS_IP);
//
//        BucketDetails bucket = createBucketDetails(TEST_BUCKET_ID, 100L, TEST_RULE, 1L);
//        dbDetails.setBucketDetails(Collections.singletonList(bucket));
//
//        return dbDetails;
//    }
//
//    private BucketDetails createBucketDetails(String bucketId, Long balance, String rule, long priority) {
//        BucketDetails bucket = new BucketDetails();
//        bucket.setBucketId(bucketId);
//        bucket.setCurrentBalance(balance);
//        bucket.setRule(rule);
//        bucket.setPriority(priority);
//        bucket.setIsUnlimited(0);
//        bucket.setServiceStatus(Constants.STATUS_ACTIVE);
//        bucket.setServiceStartDate(LocalDateTime.now().minusDays(1));
//        bucket.setServiceExpiry(LocalDateTime.now().plusDays(30));
//        bucket.setTimeWindow("0-24");
//        return bucket;
//    }
//
//    private UserDetails createUserDetails(String username, boolean isActive, boolean isAuthorized) {
//        UserDetails userDetails = new UserDetails();
//        userDetails.setUsername(username);
//        userDetails.setIsActive(isActive);
//        userDetails.setIsAuthorized(isAuthorized);
//        userDetails.setIsEnoughBalance(true);
//        return userDetails;
//    }
//
//    private UserSessionData createUserSessionData() {
//        UserSessionData sessionData = new UserSessionData();
//        Balance balance = createBalance(TEST_BUCKET_ID, 10000L, 24L);
//        sessionData.setBalance(List.of(balance));
//        return sessionData;
//    }
//
//    private Balance createBalance(String bucketId, Long consumptionLimit, Long window) {
//        Balance balance = new Balance();
//        balance.setBucketId(bucketId);
//        balance.setConsumptionLimit(consumptionLimit);
//        balance.setConsumptionLimitWindow(window);
//        balance.setConsumptionHistory(new ArrayList<>());
//        return balance;
//    }
//
//    private void mockSuccessfulAuthentication(AuthenticationDbDetails dbDetails,
//                                              UserDetails expectedUser,
//                                              String protocol) {
//        when(userAuthenticationRepository.getDbDetails(eq(dbDetails.getUserName()), anyList()))
//                .thenReturn(Uni.createFrom().item(dbDetails));
//        when(authStrategyFactory.getStrategy(protocol))
//                .thenReturn(authenticationStrategy);
//        when(authenticationStrategy.authenticate(anyString(), anyString(), anyString(), any(), any(), anyInt()))
//                .thenReturn(Uni.createFrom().item(expectedUser));
//        when(cacheClient.getUserData(anyString()))
//                .thenReturn(Uni.createFrom().item(createUserSessionData()));
//    }
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