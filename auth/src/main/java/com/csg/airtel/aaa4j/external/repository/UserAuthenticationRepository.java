package com.csg.airtel.aaa4j.external.repository;

import com.csg.airtel.aaa4j.common.util.IdentifierUtil;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.AuthenticationDbDetails;
import com.csg.airtel.aaa4j.domain.model.BucketDetails;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.csg.airtel.aaa4j.exception.BusinessValidationException;
import com.csg.airtel.aaa4j.metrics.db.TimedDb;
import com.csg.airtel.aaa4j.metrics.service.RootCauseMetricsService;      // NEW
import com.csg.airtel.aaa4j.metrics.tracker.RootCauseExceptionTracker;     // NEW
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@ApplicationScoped
@TimedDb(repository = "UserAuthenticationRepository")
public class UserAuthenticationRepository {
    private static final Logger LOG = Logger.getLogger(UserAuthenticationRepository.class);
    private static final String CLASS_NAME = "UserAuthenticationRepository";

    private final Pool client;
    private final RootCauseMetricsService exceptionMetrics;    // NEW
    private final RootCauseExceptionTracker exceptionTracker;  // NEW — @RequestScoped, one per HTTP request
    private final AtomicLong lastAuthDbExecutionTimeMs = new AtomicLong(0);
    @ConfigProperty(name = "db.query.timeout", defaultValue = "5000")
    int queryTimeoutMs;

    @Inject
    public UserAuthenticationRepository(Pool client,
                                        RootCauseMetricsService exceptionMetrics,
                                        RootCauseExceptionTracker exceptionTracker, MeterRegistry meterRegistry) {
        this.client = client;
        this.exceptionMetrics = exceptionMetrics;
        this.exceptionTracker = exceptionTracker;
        Gauge.builder("radius.auth.db.execution.time.ms", lastAuthDbExecutionTimeMs, AtomicLong::get)
                .description("Duration in milliseconds of the most recently completed auth db execution")
                .register(meterRegistry);
    }

    @CircuitBreaker(
            requestVolumeThreshold = 10,
            failureRatio = 0.5,
            delay = 10000,
            successThreshold = 2,
            skipOn = {BusinessValidationException.class}
    )
    @Retry(
            maxRetries = 2,
            delay = 100,
            maxDuration = 10000,
            abortOn = {BusinessValidationException.class}
    )
    public Uni<AuthenticationDbDetails> getDbDetails(String userName, List<String> valuePaths) {
        long startTime = System.currentTimeMillis();

        LoggingUtil.logDebug(LOG, CLASS_NAME, "getDbDetails",
                "Query initiated username=%s valuePaths=%s", userName, valuePaths);

        String selectColumns = buildSelectColumnsFromValuePaths(valuePaths);
        String sql = buildOptimizedQuery(selectColumns, userName);
        Tuple params = buildQueryParams(userName);

        LoggingUtil.logDebug(LOG, CLASS_NAME, "getDbDetails", "SQL prepared username=%s", userName);

        return client.preparedQuery(sql)
                .execute(params)
                .ifNoItem().after(Duration.ofMillis(queryTimeoutMs))
                .fail()
                .onItem().invoke(rows -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logInfo(LOG, CLASS_NAME, "getDbDetails",
                            "Query completed username=%s rowCount=%d [%d ms]",
                            userName, rows.size(), duration);
                    if (duration > 2000) {
                        LoggingUtil.logWarn(LOG, CLASS_NAME, "getDbDetails",
                                "SLOW QUERY username=%s [%d ms]", userName, duration);
                    }
                })
                .onItem().transform(rows -> processDbResponse(rows, userName, valuePaths))
                .onFailure().invoke(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    lastAuthDbExecutionTimeMs.set(duration);
                    LoggingUtil.logError(LOG, CLASS_NAME, "getDbDetails", null,
                            "Query failed username=%s [%d ms]", userName, duration);
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "getDbDetails");
                })
                .onFailure().transform(this::mapToDatabaseException);
    }

    private String buildSelectColumnsFromValuePaths(List<String> valuePaths) {
        final String baseColumns = """
    u.USER_NAME,
    u.STATUS AS USER_STATUS,
    u.PASSWORD,
    u.ENCRYPTION_METHOD,
    b.RULE,
    b.PRIORITY,
    b.BUCKET_ID,
    b.CURRENT_BALANCE,
    s.EXPIRY_DATE,
    s.SERVICE_START_DATE,
    b.TIME_WINDOW,
    s.STATUS AS SERVICE_STATUS,
    b.IS_UNLIMITED
    """;

        if (valuePaths == null || valuePaths.isEmpty()) {
            return baseColumns;
        }

        String dynamicColumns = valuePaths.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .map(this::convertValuePathToColumn)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        return dynamicColumns.isEmpty() ? baseColumns : baseColumns + ", " + dynamicColumns;
    }

    private String convertValuePathToColumn(String valuePath) {
        if (valuePath == null || !valuePath.contains(".")) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "convertValuePathToColumn",
                    "Invalid value path format: %s", valuePath);
            return null;
        }

        String[] parts = valuePath.split("\\.", 2);
        if (parts.length != 2) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "convertValuePathToColumn",
                    "Invalid value path format: %s", valuePath);
            return null;
        }

        String table = parts[0].toLowerCase();
        String column = parts[1].toUpperCase();

        String tableAlias = switch (table) {
            case "bucket_instance" -> "b";
            case "service_instance" -> "s";
            case "aaa_user" -> "u";
            default -> {
                LoggingUtil.logWarn(LOG, CLASS_NAME, "convertValuePathToColumn",
                        "Unknown table prefix: %s in path: %s", table, valuePath);
                yield null;
            }
        };

        if (tableAlias == null) return null;

        String alias = valuePath.replace(".", "_");
        String result = String.format("%s.%s AS \"%s\"", tableAlias, column, alias);
        LoggingUtil.logTrace(LOG, CLASS_NAME, "convertValuePathToColumn",
                "Converted path=%s to column=%s", valuePath, result);
        return result;
    }

    private String buildOptimizedQuery(String selectColumns, String userName) {
        String fromWhereClause;
        if (IdentifierUtil.isMacAddress(userName)) {
            fromWhereClause = """
            INNER JOIN AAA_USER_MAC_ADDRESS m ON m.USER_NAME = au.USER_NAME
            WHERE m.MAC_ADDRESS = ?
            """;
        } else if (IdentifierUtil.isIpAddress(userName)) {
            fromWhereClause = "WHERE au.IPV4 = ?";
        } else {
            fromWhereClause = "WHERE au.USER_NAME = ?";
        }

        return String.format("""
        WITH user_data AS (
            SELECT au.USER_NAME, au.PASSWORD, au.STATUS, au.ENCRYPTION_METHOD, au.group_id, au.IPV4 FROM AAA_USER au
            %s
        )
        SELECT %s
        FROM user_data u
        INNER JOIN SERVICE_INSTANCE s ON s.USERNAME = u.USER_NAME
        INNER JOIN BUCKET_INSTANCE b ON b.service_id = s.ID
        
        UNION ALL
        
        SELECT %s
        FROM user_data u
        INNER JOIN SERVICE_INSTANCE s ON s.USERNAME = u.group_id
        INNER JOIN BUCKET_INSTANCE b ON b.service_id = s.ID
        """, fromWhereClause, selectColumns, selectColumns);
    }

    private Tuple buildQueryParams(String userName) {
        if (IdentifierUtil.isMacAddress(userName)) {
            String mac = userName.replaceAll("[^A-Za-z0-9]", "");
            return Tuple.of(mac);
        }
        return Tuple.of(userName);
    }

    private AuthenticationDbDetails processDbResponse(RowSet<Row> rows,
                                                      String userName,
                                                      List<String> valuePaths) {
        Iterator<Row> it = rows.iterator();
        if (!it.hasNext()) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processDbResponse",
                    "No user found username=%s", userName);
            throw new BusinessValidationException(
                    "No user found with identifier: " + userName,
                    ResponseCodeEnum.USER_NOT_FOUND.code(),
                    ResponseCodeEnum.USER_NOT_FOUND.description(),
                    Response.Status.NOT_FOUND
            );
        }

        AuthenticationDbDetails details = new AuthenticationDbDetails();
        Map<String, String> attributesMap = new HashMap<>();
        List<BucketDetails> bucketDetailsList = new ArrayList<>();

        int rowCount = 0;
        while (it.hasNext()) {
            Row row = it.next();
            rowCount++;
            extractUserDataOnce(details, row);
            BucketDetails bucket = extractBucketDetails(row);
            if (bucket.getBucketId() != null) {
                bucketDetailsList.add(bucket);
            }
            extractVendorAttributes(valuePaths, attributesMap, row);
        }

        details.setAttributes(attributesMap);
        details.setBucketDetails(bucketDetailsList);

        LoggingUtil.logDebug(LOG, CLASS_NAME, "processDbResponse",
                "Processed username=%s rows=%d buckets=%d vendorAttrs=%d",
                userName, rowCount, bucketDetailsList.size(), attributesMap.size());

        return details;
    }

    private void extractUserDataOnce(AuthenticationDbDetails details, Row row) {
        if (details.getUserName() != null) return;
        details.setUserName(row.getString("USER_NAME"));
        details.setPassword(row.getString("PASSWORD"));
        details.setStatus(row.getString("USER_STATUS"));
        details.setEncryptionMethod(row.getValue("ENCRYPTION_METHOD") != null
                ? row.getInteger("ENCRYPTION_METHOD") : null);
    }

    private BucketDetails extractBucketDetails(Row row) {
        return new BucketDetails(
                row.getString("RULE"),
                row.getValue("PRIORITY") != null ? row.getLong("PRIORITY") : null,
                row.getString("BUCKET_ID"),
                row.getValue("CURRENT_BALANCE") != null ? row.getLong("CURRENT_BALANCE") : null,
                row.getString("TIME_WINDOW"),
                row.getLocalDateTime("SERVICE_START_DATE"),
                row.getLocalDateTime("EXPIRY_DATE"),
                row.getString("SERVICE_STATUS"),
                row.getInteger("IS_UNLIMITED")
        );
    }

    private void extractVendorAttributes(List<String> valuePaths,
                                         Map<String, String> attributesMap,
                                         Row row) {
        if (valuePaths == null || valuePaths.isEmpty()) return;

        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            columnIndexMap.put(row.getColumnName(i), i);
        }

        for (String valuePath : valuePaths) {
            if (valuePath == null || valuePath.trim().isEmpty()) continue;
            try {
                String columnAlias = valuePath.replace(".", "_");
                Integer columnIndex = columnIndexMap.get(columnAlias);
                if (columnIndex == null) {
                    LoggingUtil.logWarn(LOG, CLASS_NAME, "extractVendorAttributes",
                            "Column alias not found in map: %s", columnAlias);
                    attributesMap.put(valuePath, null);
                    continue;
                }
                Object value = row.getValue(columnIndex);
                String stringValue = value != null ? value.toString() : null;
                attributesMap.put(valuePath, stringValue);
                LoggingUtil.logTrace(LOG, CLASS_NAME, "extractVendorAttributes",
                        "Extracted vendor attribute: path=%s value=%s", valuePath, stringValue);
            } catch (Exception e) {
                LoggingUtil.logWarn(LOG, CLASS_NAME, "extractVendorAttributes",
                        "Failed to extract attribute for path=%s: %s", valuePath, e.getMessage());
                attributesMap.put(valuePath, null);
            }
        }
    }

    private Throwable mapToDatabaseException(Throwable e) {
        if (e instanceof BaseException) return e;
        if (e instanceof BusinessValidationException) return e;

        LoggingUtil.logError(LOG, CLASS_NAME, "mapToDatabaseException", e,
                "Database exception message=%s", e.getMessage());
        return new BaseException(
                "Database operation failed: " + e.getMessage(),
                ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.description(),
                Response.Status.INTERNAL_SERVER_ERROR,
                ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.code(),
                e.getStackTrace()
        );
    }
}