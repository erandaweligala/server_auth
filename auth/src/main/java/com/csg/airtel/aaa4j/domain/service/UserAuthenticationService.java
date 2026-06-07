package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.strategy.AuthenticationStrategy;
import com.csg.airtel.aaa4j.common.strategy.AuthenticationStrategyFactory;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.Constants;
import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.*;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.ConsumptionRecord;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.csg.airtel.aaa4j.exception.BusinessValidationException;
import com.csg.airtel.aaa4j.external.repository.UserAuthenticationRepository;
import com.csg.airtel.aaa4j.external.client.CacheClient;
import com.csg.airtel.aaa4j.common.util.TtlCache;
import com.csg.airtel.aaa4j.metrics.service.RootCauseMetricsService;      // NEW
import com.csg.airtel.aaa4j.metrics.tracker.RootCauseExceptionTracker;     // NEW
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class UserAuthenticationService {
    private static final Logger LOG = Logger.getLogger(UserAuthenticationService.class);
    private static final String CLASS_NAME = "UserAuthenticationService";

    private final UserAuthenticationRepository userAuthenticationRepository;
    private final CacheClient cacheClient;
    private final CacheSchedulerService cacheSchedulerService;
    private final RootCauseMetricsService exceptionMetrics;    // NEW
    private final RootCauseExceptionTracker exceptionTracker;  // NEW — @RequestScoped, one per HTTP request

    @ConfigProperty(name = "radius.accept.attributes")
    String userAttributes;
    @ConfigProperty(name = "auth.isCheckBucket")
    boolean isCheckBucket;

    private final AuthenticationStrategyFactory authStrategyFactory;
    private static final ThreadLocal<LocalDateTime> CACHED_NOW = new ThreadLocal<>();
    private static final ThreadLocal<LocalDate> CACHED_TODAY = new ThreadLocal<>();
    private final CacheUpdateService cacheUpdateService;
    private static final ConcurrentHashMap<String, LocalTime[]> TIME_WINDOW_CACHE = new ConcurrentHashMap<>();

    private final TtlCache<String, AuthenticationDbDetails> userAuthCache =
            new TtlCache<>(500_000, 30_000);

    private final TtlCache<String, Optional<UserSessionData>> sessionDataCache =
            new TtlCache<>(500_000, 10_000);

    @Inject
    public UserAuthenticationService(UserAuthenticationRepository userAuthenticationRepository,
                                     AuthenticationStrategyFactory authStrategyFactory,
                                     CacheClient cacheClient,
                                     CacheSchedulerService cacheSchedulerService,
                                     CacheUpdateService cacheUpdateService,
                                     RootCauseMetricsService exceptionMetrics,      // NEW
                                     RootCauseExceptionTracker exceptionTracker) {  // NEW
        this.userAuthenticationRepository = userAuthenticationRepository;
        this.authStrategyFactory = authStrategyFactory;
        this.cacheClient = cacheClient;
        this.cacheSchedulerService = cacheSchedulerService;
        this.cacheUpdateService = cacheUpdateService;
        this.exceptionMetrics = exceptionMetrics;
        this.exceptionTracker = exceptionTracker;
        LoggingUtil.logInfo(LOG, CLASS_NAME, "init", "Service initialized successfully");
    }

    public Uni<UserDetails> userAuthenticate(AuthenticationRequest request) {
        String username = request.getUsername();

        LoggingUtil.logDebug(LOG, CLASS_NAME, "userAuthenticate",
                "Auth request username=%s nasIp=%s", username, request.getNasIpAddress());

        return cacheSchedulerService.getVendorConfigByNasIp(request.getNasIpAddress())
                .onItem().transformToUni(vendorConfig -> {
                    if (vendorConfig == null) {
                        LoggingUtil.logDebug(LOG, CLASS_NAME, "userAuthenticate",
                                "No vendor config for NAS IP=%s", request.getNasIpAddress());
                        return authenticateWithVendorConfig(request, username, null);
                    }
                    return authenticateWithVendorConfig(request, username, vendorConfig);
                })
                .onFailure().invoke(e -> {
                    LoggingUtil.logError(LOG, CLASS_NAME, "userAuthenticate", null,
                            "Authentication failed username=%s", username);
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "userAuthenticate");
                })
                .onFailure().transform(this::mapToBaseException)
                .onTermination().invoke(this::clearThreadLocalCache);
    }

    private Uni<UserDetails> authenticateWithVendorConfig(AuthenticationRequest request,
                                                          String username,
                                                          VendorAttributeConfig vendorConfig) {
        List<String> valuePaths = vendorConfig != null
                ? vendorConfig.getAttributes().stream()
                .map(VendorAttribute::getValuePath)
                .toList()
                : List.of();

        AuthenticationDbDetails cached = userAuthCache.getIfPresent(username);
        if (cached != null) {
            LoggingUtil.logTrace(LOG, CLASS_NAME, "authenticateWithVendorConfig",
                    "L1 cache hit username=%s", username);
            return processAuthentication(request, cached, vendorConfig);
        }

        return userAuthenticationRepository.getDbDetails(username, valuePaths)
                .onItem().invoke(user -> {
                    userAuthCache.put(username, user);
                    LoggingUtil.logDebug(LOG, CLASS_NAME, "authenticateWithVendorConfig",
                            "User details retrieved from DB and cached username=%s", user.getUserName());
                })
                .onItem().transformToUni(user -> processAuthentication(request, user, vendorConfig));
    }

    private void clearThreadLocalCache() {
        CACHED_NOW.remove();
        CACHED_TODAY.remove();
        LoggingUtil.logTrace(LOG, CLASS_NAME, "clearThreadLocalCache", "ThreadLocal cache cleared");
    }

    private Uni<UserDetails> processAuthentication(AuthenticationRequest request,
                                                   AuthenticationDbDetails userFromDb,
                                                   VendorAttributeConfig vendorConfig) {
        String username = userFromDb.getUserName();
        LoggingUtil.logDebug(LOG, CLASS_NAME, "processAuthentication",
                "Processing authentication username=%s", username);

        UserDetails userDetails = new UserDetails();
        userDetails.setUsername(username);
        if (vendorConfig != null) {
            userDetails.setVendorId(vendorConfig.getVendorId());
        }

        boolean isValidNas = isValidNasIpSync(request);
        return processAuthenticationAfterNasValidation(isValidNas, request, userFromDb, userDetails, vendorConfig);
    }

    private Uni<UserDetails> processAuthenticationAfterNasValidation(boolean isValidNas,
                                                                     AuthenticationRequest request,
                                                                     AuthenticationDbDetails userFromDb,
                                                                     UserDetails userDetails,
                                                                     VendorAttributeConfig vendorConfig) {
        String username = userFromDb.getUserName();

        if (!isValidNas) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processAuthentication",
                    "Invalid NAS IP username=%s received=%s", username, request.getNasIpAddress());
            cacheUpdateService.recordAccessRejectRequest();
            return unauthorizedDueToNasIp(userDetails);
        }

        if (!hasValidBalance(userFromDb, username)) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processAuthentication",
                    "Insufficient balance username=%s", username);
            cacheUpdateService.recordAccessRejectRequest();
            return insufficientBalance(userDetails);
        }

        return handleUserStatusAndAuthentication(request, userFromDb, userDetails, vendorConfig);
    }

    private boolean hasValidBalance(AuthenticationDbDetails userFromDb, String username) {
        double totalBalance = getTotalBalance(userFromDb.getBucketDetails());
        LoggingUtil.logDebug(LOG, CLASS_NAME, "processAuthentication",
                "Total balance username=%s balance=%s checkBucket=%s",
                username, totalBalance, isCheckBucket);
        return !isCheckBucket || totalBalance > 0 || hasUnlimitedBucket(userFromDb.getBucketDetails());
    }

    private Uni<UserDetails> handleUserStatusAndAuthentication(AuthenticationRequest request,
                                                               AuthenticationDbDetails userFromDb,
                                                               UserDetails userDetails,
                                                               VendorAttributeConfig vendorConfig) {
        String username = userFromDb.getUserName();

        if (Constants.STATUS_BARRED.equalsIgnoreCase(userFromDb.getStatus())) {
            return handleBarredUser(userDetails, userFromDb, vendorConfig);
        }

        if (!Constants.STATUS_ACTIVE.equalsIgnoreCase(userFromDb.getStatus())) {
            userDetails.setIsActive(false);
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processAuthentication",
                    "User is not active username=%s status=%s", username, userFromDb.getStatus());
            cacheUpdateService.recordAccessRejectRequest();
            return Uni.createFrom().item(userDetails);
        }

        return routeToAuthenticationMethod(request, userFromDb, userDetails, vendorConfig);
    }

    private Uni<UserDetails> handleBarredUser(UserDetails userDetails,
                                              AuthenticationDbDetails userFromDb,
                                              VendorAttributeConfig vendorConfig) {
        return cacheSchedulerService.getBarredStatusRule()
                .onItem().transformToUni(barredRule -> {
                    applyBarredRuleIfPresent(barredRule, userDetails, userFromDb, vendorConfig);
                    cacheUpdateService.recordAccessAcceptRequest();
                    return Uni.createFrom().item(userDetails);
                });
    }

    private void applyBarredRuleIfPresent(String barredRule, UserDetails userDetails,
                                          AuthenticationDbDetails userFromDb,
                                          VendorAttributeConfig vendorConfig) {
        if (barredRule != null && !barredRule.trim().isEmpty()) {
            userDetails.setIsAuthorized(true);
            userDetails.setIsEnoughBalance(true);
            userDetails.setIsActive(true);
            userDetails.setVendorId(vendorConfig.getVendorId());
            userDetails.setVendorAttributes(mapToVendorAttributes(userFromDb.getAttributes(), vendorConfig, barredRule));
            LoggingUtil.logDebug(LOG, CLASS_NAME, "applyBarredRuleIfPresent",
                    "User is barred, applied barred rule: %s - traceId: %s",
                    barredRule, MDC.get(Constants.TRACE_ID));
        } else {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "applyBarredRuleIfPresent",
                    "User is barred but no barred rule found in cache - traceId: %s",
                    MDC.get(Constants.TRACE_ID));
        }
    }

    private Uni<UserDetails> routeToAuthenticationMethod(AuthenticationRequest request,
                                                         AuthenticationDbDetails userFromDb,
                                                         UserDetails userDetails,
                                                         VendorAttributeConfig vendorConfig) {
        String username = userFromDb.getUserName();
        if (request.getFramedProtocol() != null && request.getFramedProtocol().equalsIgnoreCase("1")) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "routeToAuthenticationMethod",
                    "PPPoE authentication initiated for username: %s", username);
            return handleProtocolAuthentication(request, userFromDb, userDetails, vendorConfig);
        } else {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "routeToAuthenticationMethod",
                    "IPoE authentication detected for: %s", username);
            return handleMacAuthentication(request, userFromDb, userDetails, vendorConfig);
        }
    }

    private boolean isValidNasIpSync(AuthenticationRequest request) {
        return cacheSchedulerService.isActiveNasIp(request.getNasIpAddress());
    }

    private Uni<UserDetails> unauthorizedDueToNasIp(UserDetails user) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "unauthorizedDueToNasIp",
                "User unauthorized due to NAS IP mismatch username=%s", user.getUsername());
        user.setIsActive(true);
        user.setIsEnoughBalance(true);
        user.setIsAuthorized(false);
        return Uni.createFrom().item(user);
    }

    private Uni<UserDetails> insufficientBalance(UserDetails user) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "insufficientBalance",
                "User unauthorized due to insufficient balance username=%s", user.getUsername());
        user.setIsAuthorized(false);
        user.setIsActive(true);
        user.setIsEnoughBalance(false);
        return Uni.createFrom().item(user);
    }

    private Uni<UserDetails> handleMacAuthentication(AuthenticationRequest request,
                                                     AuthenticationDbDetails userFromDb,
                                                     UserDetails userDetails,
                                                     VendorAttributeConfig vendorConfig) {
        String username = request.getUsername();
        LoggingUtil.logDebug(LOG, CLASS_NAME, "handleMacAuthentication",
                "Handling MAC authentication username=%s", username);

        userDetails.setIsActive(true);
        userDetails.setIsEnoughBalance(true);
        userDetails.setIsAuthorized(true);

        return getHighestPriorityRule(userFromDb.getBucketDetails(), username)
                .onItem().transform(rule -> {
                    userDetails.setVendorId(vendorConfig.getVendorId());
                    userDetails.setVendorAttributes(
                            mapToVendorAttributes(userFromDb.getAttributes(), vendorConfig, rule));
                    cacheUpdateService.recordAccessAcceptRequest();
                    return userDetails;
                });
    }

    private Uni<UserDetails> handleProtocolAuthentication(AuthenticationRequest request,
                                                          AuthenticationDbDetails userFromDb,
                                                          UserDetails userDetails,
                                                          VendorAttributeConfig vendorConfig) {
        String username = userFromDb.getUserName();
        String protocolType = determineProtocol(username, request.getChapPassword());

        LoggingUtil.logDebug(LOG, CLASS_NAME, "handleProtocolAuthentication",
                "Protocol authentication username=%s protocol=%s", username, protocolType);

        AuthenticationStrategy strategy = authStrategyFactory.getStrategy(protocolType);

        return strategy.authenticate(
                        username,
                        request.getPassword(),
                        userFromDb.getPassword(),
                        request.getChapChallenge(),
                        request.getChapPassword(),
                        userFromDb.getEncryptionMethod()
                )
                .onItem().invoke(result ->
                        LoggingUtil.logDebug(LOG, CLASS_NAME, "handleProtocolAuthentication",
                                "Authentication strategy completed username=%s authorized=%s",
                                username, result.getIsAuthorized()))
                .onItem().transformToUni(authenticatedUser -> {
                    authenticatedUser.setIsActive(true);

                    if (authenticatedUser.getIsAuthorized()) {
                        return getHighestPriorityRule(userFromDb.getBucketDetails(), username)
                                .onItem().transform(rule -> {
                                    authenticatedUser.setVendorId(vendorConfig.getVendorId());
                                    authenticatedUser.setVendorAttributes(
                                            mapToVendorAttributes(userFromDb.getAttributes(), vendorConfig, rule));
                                    cacheUpdateService.recordAccessAcceptRequest();
                                    return authenticatedUser;
                                });
                    }

                    LoggingUtil.logWarn(LOG, CLASS_NAME, "handleProtocolAuthentication",
                            "User authentication failed username=%s", username);
                    cacheUpdateService.recordAccessRejectRequest();
                    return Uni.createFrom().item(authenticatedUser);
                });
    }

    private List<VendorAttributeDetail> mapToVendorAttributes(Map<String, String> dbAttributes,
                                                              VendorAttributeConfig vendorConfig,
                                                              String barredRule) {
        if (vendorConfig == null || dbAttributes == null) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "mapToVendorAttributes",
                    "No vendor config or DB attributes available");
            return new ArrayList<>();
        }

        List<VendorAttributeDetail> vendorAttributes = new ArrayList<>();
        for (VendorAttribute attr : vendorConfig.getAttributes()) {
            String valuePath = attr.getValuePath();
            String value = dbAttributes.get(valuePath);
            if (valuePath != null && valuePath.equalsIgnoreCase("bucket_instance.rule")) {
                value = barredRule;
            }
            if (value != null) {
                VendorAttributeDetail detail = new VendorAttributeDetail(
                        attr.getAttributeId(),
                        attr.getAttributeName(),
                        attr.getAttributePrefix(),
                        value
                );
                vendorAttributes.add(detail);
                LoggingUtil.logTrace(LOG, CLASS_NAME, "mapToVendorAttributes",
                        "Mapped attribute: id=%d name=%s prefix=%s path=%s value=%s",
                        attr.getAttributeId(), attr.getAttributeName(),
                        attr.getAttributePrefix(), valuePath, value);
            } else {
                LoggingUtil.logDebug(LOG, CLASS_NAME, "mapToVendorAttributes",
                        "No value found for path=%s attributeId=%d", valuePath, attr.getAttributeId());
            }
        }

        LoggingUtil.logDebug(LOG, CLASS_NAME, "mapToVendorAttributes",
                "Mapped %d vendor attributes", vendorAttributes.size());
        return vendorAttributes;
    }

    private Throwable mapToBaseException(Throwable e) {
        if (e instanceof BaseException) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "mapToBaseException",
                    "Exception is already BaseException message=%s", e.getMessage());
            return e;
        }
        if (e instanceof BusinessValidationException) return e;

        // ❌ No exceptionMetrics.record() here — already called in userAuthenticate's onFailure above
        LoggingUtil.logError(LOG, CLASS_NAME, "mapToBaseException", e,
                "Mapping exception to BaseException message=%s", e.getMessage());
        return new BaseException(
                e.getMessage(),
                ResponseCodeEnum.EXCEPTION_SERVICE_LAYER.description(),
                Response.Status.INTERNAL_SERVER_ERROR,
                ResponseCodeEnum.EXCEPTION_SERVICE_LAYER.code(),
                e.getStackTrace()
        );
    }

    private String determineProtocol(String userName, String chapPassword) {
        String protocol = (chapPassword != null && !chapPassword.isEmpty())
                ? Constants.CHAP_PROTOCOL
                : Constants.PAP_PROTOCOL;
        LoggingUtil.logTrace(LOG, CLASS_NAME, "determineProtocol",
                "Protocol determined username=%s protocol=%s", userName, protocol);
        return protocol;
    }

    private double getTotalBalance(List<BucketDetails> details) {
        if (details == null || details.isEmpty()) return 0.0;
        return details.stream()
                .filter(s -> s.getCurrentBalance() != null)
                .mapToDouble(s -> {
                    try {
                        return Double.parseDouble(String.valueOf(s.getCurrentBalance()));
                    } catch (NumberFormatException e) {
                        LoggingUtil.logWarn(LOG, CLASS_NAME, "getTotalBalance",
                                "Invalid balance format bucketId=%s, setting to 0.0", s.getBucketId());
                        return 0.0;
                    }
                })
                .sum();
    }

    private boolean hasUnlimitedBucket(List<BucketDetails> details) {
        if (details == null || details.isEmpty()) return false;
        return details.stream().anyMatch(s -> s.getIsUnlimited() == 1);
    }

    private Uni<String> getHighestPriorityRule(List<BucketDetails> bucketDetailsList, String username) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "getHighestPriorityRule",
                "Fetching highest priority rule username=%s", username);
        return getBalanceWithConsumptionCheck(bucketDetailsList, username)
                .onItem().invoke(balance -> {
                    if (balance != null) {
                        LoggingUtil.logDebug(LOG, CLASS_NAME, "getHighestPriorityRule",
                                "Highest priority balance selected username=%s bucketId=%s rule=%s",
                                username, balance.getBucketId(), balance.getRule());
                    } else {
                        LoggingUtil.logWarn(LOG, CLASS_NAME, "getHighestPriorityRule",
                                "No eligible balance found username=%s", username);
                    }
                })
                .onItem().transform(balance -> balance != null ? balance.getRule() : null);
    }

    private LocalDateTime getNow() {
        LocalDateTime now = CACHED_NOW.get();
        if (now == null) {
            now = LocalDateTime.now();
            CACHED_NOW.set(now);
        }
        return now;
    }

    public Uni<BucketDetails> getBalanceWithConsumptionCheck(List<BucketDetails> bucketDetailsList, String username) {
        if (bucketDetailsList == null || bucketDetailsList.isEmpty()) {
            return Uni.createFrom().item(() -> null);
        }

        LocalDateTime now = getNow();
        String activeStatus = "Active";

        Optional<UserSessionData> cachedSession = sessionDataCache.getIfPresent(username);
        if (cachedSession != null) {
            UserSessionData sessionData = cachedSession.orElse(null);
            if (sessionData == null) {
                return Uni.createFrom().item(
                        selectHighestPriorityBalanceWithoutConsumption(bucketDetailsList, username, now, activeStatus));
            }
            return Uni.createFrom().item(
                    selectHighestPriorityBalance(bucketDetailsList, username, now, activeStatus, sessionData));
        }

        return cacheClient.getUserData(username)
                .onItem().transform(userSessionData -> {
                    sessionDataCache.put(username, Optional.ofNullable(userSessionData));
                    if (userSessionData == null) {
                        return selectHighestPriorityBalanceWithoutConsumption(bucketDetailsList, username, now, activeStatus);
                    }
                    return selectHighestPriorityBalance(bucketDetailsList, username, now, activeStatus, userSessionData);
                })
                .onFailure().recoverWithItem(throwable -> {
                    LoggingUtil.logWarn(LOG, CLASS_NAME, "getBalanceWithConsumptionCheck",
                            "Failed to fetch cache data username=%s, proceeding with basic selection", username);
                    return selectHighestPriorityBalanceWithoutConsumption(bucketDetailsList, username, now, activeStatus);
                });
    }

    private BucketDetails selectHighestPriorityBalanceWithoutConsumption(List<BucketDetails> bucketDetailsList,
                                                                         String username,
                                                                         LocalDateTime now,
                                                                         String activeStatus) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                "Selecting balance WITHOUT consumption checks username=%s", username);

        BucketDetails highestPriorityBalance = null;
        long highestPriority = Long.MIN_VALUE;
        LocalDateTime highestExpiry = null;

        for (BucketDetails balance : bucketDetailsList) {
            String bucketId = balance.getBucketId();
            if (!isBucketEligibleWithoutConsumption(balance, now, activeStatus, bucketId)) continue;
            long priority = balance.getPriority();
            LocalDateTime expiry = balance.getServiceExpiry();
            if (shouldSelectBalance(highestPriorityBalance, highestPriority, highestExpiry, priority, expiry)) {
                highestPriorityBalance = balance;
                highestPriority = priority;
                highestExpiry = expiry;
            }
        }

        logSelectionResult(username, highestPriorityBalance);
        return highestPriorityBalance;
    }

    private boolean isBucketEligibleWithoutConsumption(BucketDetails balance, LocalDateTime now,
                                                       String activeStatus, String bucketId) {
        return hasValidBalance(balance, bucketId)
                && !isExpired(balance, now, bucketId)
                && isWithinTimeWindow(balance.getTimeWindow(), bucketId)
                && isServiceActive(balance, now, activeStatus, bucketId);
    }

    private boolean hasValidBalance(BucketDetails balance, String bucketId) {
        if (balance.getIsUnlimited() == 0 && balance.getCurrentBalance() != null && balance.getCurrentBalance() <= 0) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "Skipping bucket %s: insufficient balance (%s)", bucketId, balance.getCurrentBalance());
            return false;
        }
        return true;
    }

    private boolean isExpired(BucketDetails balance, LocalDateTime now, String bucketId) {
        if (balance.getServiceExpiry().isBefore(now)) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "Skipping bucket=%s expired on=%s", bucketId, balance.getServiceExpiry());
            return true;
        }
        return false;
    }

    private boolean isWithinTimeWindow(String timeWindow, String bucketId) {
        if (!isWithinTimeWindow(timeWindow)) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "Skipping bucket=%s outside time window=%s", bucketId, timeWindow);
            return false;
        }
        return true;
    }

    private boolean isServiceActive(BucketDetails balance, LocalDateTime now,
                                    String activeStatus, String bucketId) {
        LocalDateTime serviceStartDate = balance.getServiceStartDate();
        if (serviceStartDate.isAfter(now)) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "Skipping bucket=%s service not started yet starts=%s", bucketId, serviceStartDate);
            return false;
        }
        if (!activeStatus.equals(balance.getServiceStatus())) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "Skipping bucket=%s service status=%s expected=%s",
                    bucketId, balance.getServiceStatus(), activeStatus);
            return false;
        }
        return true;
    }

    private void logSelectionResult(String username, BucketDetails selectedBalance) {
        if (selectedBalance != null) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "Selected bucket username=%s bucketId=%s", username, selectedBalance.getBucketId());
        } else {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "selectHighestPriorityBalanceWithoutConsumption",
                    "No eligible bucket found username=%s", username);
        }
    }

    private BucketDetails selectHighestPriorityBalance(List<BucketDetails> bucketDetailsList,
                                                       String username,
                                                       LocalDateTime now,
                                                       String activeStatus,
                                                       UserSessionData userSessionData) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalance",
                "Selecting balance username=%s", username);

        BucketDetails highestPriorityBalance = null;
        long highestPriority = Long.MIN_VALUE;
        LocalDateTime highestExpiry = null;

        for (BucketDetails balance : bucketDetailsList) {
            String bucketId = balance.getBucketId();
            if (!isBucketEligibleWithConsumption(balance, now, activeStatus, userSessionData, bucketId)) continue;
            long priority = balance.getPriority();
            LocalDateTime expiry = balance.getServiceExpiry();
            if (shouldSelectBalance(highestPriorityBalance, highestPriority, highestExpiry, priority, expiry)) {
                LoggingUtil.logDebug(LOG, CLASS_NAME, "selectHighestPriorityBalance",
                        "New highest priority bucket selected=%s priority=%d expiry=%s replacing=%s",
                        bucketId, priority, expiry,
                        highestPriorityBalance != null ? highestPriorityBalance.getBucketId() : "none");
                highestPriorityBalance = balance;
                highestPriority = priority;
                highestExpiry = expiry;
            }
        }
        return highestPriorityBalance;
    }

    private boolean isBucketEligibleWithConsumption(BucketDetails balance, LocalDateTime now,
                                                    String activeStatus, UserSessionData userSessionData,
                                                    String bucketId) {
        return hasValidBalance(balance, bucketId)
                && !isExpired(balance, now, bucketId)
                && isWithinTimeWindow(balance.getTimeWindow(), bucketId)
                && isWithinConsumptionLimit(userSessionData, bucketId)
                && isServiceActive(balance, now, activeStatus, bucketId);
    }

    private boolean isWithinConsumptionLimit(UserSessionData userSessionData, String bucketId) {
        if (!isWithinConsumptionLimitForBucket(userSessionData, bucketId)) {
            LoggingUtil.logDebug(LOG, CLASS_NAME, "isBucketEligibleWithConsumption",
                    "Skipping bucket %s: consumption limit exceeded", bucketId);
            return false;
        }
        return true;
    }

    private boolean shouldSelectBalance(BucketDetails currentHighest, long currentHighestPriority,
                                        LocalDateTime currentHighestExpiry, long candidatePriority,
                                        LocalDateTime candidateExpiry) {
        if (currentHighest == null) return true;
        if (candidatePriority < currentHighestPriority) return true;
        if (candidatePriority == currentHighestPriority && candidateExpiry != null) {
            return currentHighestExpiry == null || candidateExpiry.isBefore(currentHighestExpiry);
        }
        return false;
    }

    private boolean isWithinConsumptionLimitForBucket(UserSessionData userSessionData, String bucketId) {
        if (userSessionData == null || userSessionData.getBalance() == null) return true;
        Balance cachedBalance = findBalanceInCache(userSessionData.getBalance(), bucketId);
        if (cachedBalance == null) return true;

        boolean withinLimit = isWithinConsumptionLimit(cachedBalance);
        if (!withinLimit) {
            Long limit = cachedBalance.getConsumptionLimit();
            Long window = cachedBalance.getConsumptionLimitWindow();
            long consumption = calculateConsumptionInWindow(cachedBalance, window != null ? window : 0);
            LoggingUtil.logDebug(LOG, CLASS_NAME, "isWithinConsumptionLimitForBucket",
                    "Bucket consumption check failed bucket=%s consumed=%d bytes limit=%d bytes window=%d hours",
                    bucketId, consumption, limit, window);
        }
        return withinLimit;
    }

    private boolean isWithinConsumptionLimit(Balance cachedBalance) {
        Long consumptionLimit = cachedBalance.getConsumptionLimit();
        Long consumptionLimitWindow = cachedBalance.getConsumptionLimitWindow();
        if (consumptionLimit == null || consumptionLimit <= 0
                || consumptionLimitWindow == null || consumptionLimitWindow <= 0) {
            return true;
        }
        long currentConsumption = calculateConsumptionInWindow(cachedBalance, consumptionLimitWindow);
        return currentConsumption < consumptionLimit;
    }

    private Balance findBalanceInCache(List<Balance> balances, String bucketId) {
        if (balances == null || bucketId == null) return null;
        return balances.stream()
                .filter(b -> bucketId.equals(b.getBucketId()))
                .findFirst()
                .orElse(null);
    }

    public long calculateConsumptionInWindow(Balance balance, long windowHours) {
        List<ConsumptionRecord> history = balance.getConsumptionHistory();
        if (history == null || history.isEmpty()) return 0L;

        LocalDate today = getToday();
        LocalDate windowStartDate = today.minusDays(windowHours);

        long total = 0L;
        int recordsInWindow = 0;
        for (ConsumptionRecord consumptionRecord : history) {
            if (consumptionRecord.getDate().isAfter(windowStartDate)) {
                total += consumptionRecord.getBytesConsumed();
                recordsInWindow++;
            }
        }

        LoggingUtil.logDebug(LOG, CLASS_NAME, "calculateConsumptionInWindow",
                "Consumption calculated bucket=%s total=%d bytes from records=%d",
                balance.getBucketId(), total, recordsInWindow);
        return total;
    }

    private LocalDate getToday() {
        LocalDate today = CACHED_TODAY.get();
        if (today == null) {
            today = LocalDate.now();
            CACHED_TODAY.set(today);
        }
        return today;
    }

    public boolean isWithinTimeWindow(String timeWindow) {
        if (timeWindow == null || timeWindow.trim().isEmpty()) {
            throw new IllegalArgumentException("Time window string cannot be null or empty");
        }

        LocalTime[] cached = TIME_WINDOW_CACHE.get(timeWindow);
        if (cached == null) {
            String[] times = timeWindow.split("-", 2);
            if (times.length != 2) {
                LoggingUtil.logError(LOG, CLASS_NAME, "calculateConsumptionInWindow", null,
                        "Invalid time window: %s", timeWindow);
                throw new IllegalArgumentException("Invalid time window format. Expected format: 'HH:mm - HH:mm'");
            }
            cached = new LocalTime[]{parseHourMinute(times[0].trim()), parseHourMinute(times[1].trim())};
            TIME_WINDOW_CACHE.put(timeWindow, cached);
        }

        LocalTime startTime = cached[0];
        LocalTime endTime = cached[1];
        LocalTime currentTime = LocalTime.now();

        if (startTime.isAfter(endTime)) {
            return !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        } else {
            return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        }
    }

    private static LocalTime parseHourMinute(String timeStr) {
        timeStr = timeStr.trim();
        if (timeStr.isEmpty()) throw new IllegalArgumentException("Time string cannot be empty");
        String[] parts = timeStr.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Unable to parse time: " + timeStr +
                    ". Expected format: 'HH:mm'");
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour == 24 && minute == 0) return LocalTime.of(23, 59, 59);
            if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour must be between 0 and 23, got: " + hour);
            if (minute < 0 || minute > 59) throw new IllegalArgumentException("Minute must be between 0 and 59, got: " + minute);
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unable to parse time: " + timeStr +
                    ". Expected format: 'HH:mm'", e);
        }
    }
}