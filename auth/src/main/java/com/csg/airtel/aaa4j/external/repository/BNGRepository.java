package com.csg.airtel.aaa4j.external.repository;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.Constants;
import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.VendorAttribute;
import com.csg.airtel.aaa4j.domain.model.VendorAttributeConfig;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.csg.airtel.aaa4j.metrics.service.RootCauseMetricsService;         // NEW
import com.csg.airtel.aaa4j.metrics.tracker.RootCauseExceptionTracker;        // NEW
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class BNGRepository {
    private static final Logger LOG = Logger.getLogger(BNGRepository.class);
    private static final String CLASS_NAME = "BNGRepository";

    private final Pool client;
    private final RootCauseMetricsService exceptionMetrics;    // NEW
    private final RootCauseExceptionTracker exceptionTracker;  // NEW — request-scoped

    @Inject
    public BNGRepository(Pool client,
                         RootCauseMetricsService exceptionMetrics,
                         RootCauseExceptionTracker exceptionTracker) {
        this.client = client;
        this.exceptionMetrics = exceptionMetrics;
        this.exceptionTracker = exceptionTracker;
        LoggingUtil.logInfo(LOG, CLASS_NAME, "constructor", "BNGRepository initialized");
    }

    public Uni<List<String>> getActiveNasIps(){
        long startTime = System.currentTimeMillis();
        String traceId = MDC.get(Constants.TRACE_ID);

        LoggingUtil.logInfo(LOG, CLASS_NAME, "getActiveNasIps",
                "Fetching active NAS IPs from database");

        String sql = "SELECT b.NAS_IP_ADDRESS FROM BNG b WHERE b.STATUS = 'Active'";

        return client.preparedQuery(sql).execute()
                .onItem().transform(rows -> {
                    long duration = System.currentTimeMillis() - startTime;
                    List<String> result = processDbResponse(rows);
                    LoggingUtil.logInfo(LOG, CLASS_NAME, "getActiveNasIps",
                            "Query completed in %d ms, retrieved %d active NAS IPs",
                            duration, result.size());
                    return result;
                })
                .onFailure().invoke(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logError(LOG, CLASS_NAME, "getActiveNasIps", e,
                            "Query failed after %d ms", duration);
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "getActiveNasIps");
                })
                .onFailure().transform(this::mapToDatabaseException);
    }

    // -----------------------------------------------------------------------
    // getBarredPlanRule — same pattern
    // -----------------------------------------------------------------------
    public Uni<String> getBarredPlanRule() {
        long startTime = System.currentTimeMillis();

        LoggingUtil.logDebug(LOG, CLASS_NAME, "getBarredPlanRule",
                "Fetching BNG code for barred plan rule from database");

        String sql = """
                SELECT DISTINCT qp.BNG_CODE
                FROM QOS_PROFILE qp
                INNER JOIN BUCKET b ON qp.ID = b.QOS_ID
                INNER JOIN PLAN_TO_BUCKET ptb ON b.BUCKET_ID = ptb.BUCKET_ID
                INNER JOIN PLAN p ON ptb.PLAN_ID = p.PLAN_ID
                WHERE p.PLAN_TYPE = 'global'
                """;

        return client.preparedQuery(sql).execute()
                .onItem().transform(rows -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String result = processDbResponseForBarredPlan(rows);
                    LoggingUtil.logInfo(LOG, CLASS_NAME, "getBarredPlanRule",
                            "Query completed in %d ms, BNG code: %s",
                            duration, result != null ? result : "null");
                    return result;
                })
                .onFailure().invoke(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logError(LOG, CLASS_NAME, "getBarredPlanRule", e,
                            "Query failed after %d ms", duration);
                    // ✅ RECORD ONCE HERE
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "getBarredPlanRule");
                })
                .onFailure().transform(this::mapToDatabaseException);
    }

    // -----------------------------------------------------------------------
    // getActiveNasWithVendorConfigs — same pattern
    // -----------------------------------------------------------------------
    public Uni<List<VendorAttributeConfig>> getActiveNasWithVendorConfigs() {
        long startTime = System.currentTimeMillis();
        String traceId = MDC.get(Constants.TRACE_ID);

        LoggingUtil.logDebug(LOG, CLASS_NAME, "getActiveNasWithVendorConfigs",
                "Fetching active NAS IPs with vendor configurations - traceId: %s", traceId);

        String sql = """
            SELECT 
                b.NAS_IP_ADDRESS,
                b.BNG_TYPE_VENDOR as VENDOR_NAME,
                vc.VENDOR_ID,
                vc.ATTRIBUTE_ID,
                vc.ATTRIBUTE_NAME,
                vc.VALUE_PATH,
                vc.DATA_TYPE,
                vc.ATTRIBUTE_PREFIX
            FROM BNG b
            LEFT JOIN VENDOR_CONFIG_TABLE vc ON b.BNG_TYPE_VENDOR = vc.VENDOR_NAME
            WHERE b.STATUS = 'Active' 
            AND (vc.IS_ACTIVE = 1 OR vc.IS_ACTIVE IS NULL)
            ORDER BY b.NAS_IP_ADDRESS, vc.ATTRIBUTE_ID
            """;

        return client.preparedQuery(sql).execute()
                .onItem().transform(rows -> {
                    long duration = System.currentTimeMillis() - startTime;
                    List<VendorAttributeConfig> result = processVendorConfigResponse(rows);
                    LoggingUtil.logInfo(LOG, CLASS_NAME, "getActiveNasWithVendorConfigs",
                            "Query completed in %d ms, retrieved %d NAS configurations - traceId: %s",
                            duration, result.size(), traceId);
                    return result;
                })
                .onFailure().invoke(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    LoggingUtil.logError(LOG, CLASS_NAME, "getActiveNasWithVendorConfigs", e,
                            "Query failed after %d ms - traceId: %s", duration, traceId);
                    // ✅ RECORD ONCE HERE
                    exceptionMetrics.record(exceptionTracker, e, CLASS_NAME, "getActiveNasWithVendorConfigs");
                })
                .onFailure().transform(this::mapToDatabaseException);
    }

    // -----------------------------------------------------------------------
    // mapToDatabaseException — NO metric recording here anymore, just wrapping
    // -----------------------------------------------------------------------
    private Throwable mapToDatabaseException(Throwable e) {
        if (e instanceof BaseException) {
            return e; // already wrapped upstream
        }
        LoggingUtil.logError(LOG, CLASS_NAME, "mapToDatabaseException", e,
                "Database exception occurred: %s", e.getMessage());

        // ❌ NO exceptionMetrics.record() here — already called in the specific method above
        return new BaseException(
                "Database operation failed: " + e.getMessage(),
                ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.description(),
                Response.Status.INTERNAL_SERVER_ERROR,
                ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.code(),
                e.getStackTrace()
        );
    }

    // -----------------------------------------------------------------------
    // All processDbResponse methods below are UNCHANGED
    // -----------------------------------------------------------------------

    private List<String> processDbResponse(RowSet<Row> rows) {
        if (rows == null || rows.size() == 0) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processDbResponse",
                    "No active NAS IPs found in database");
            return List.of();
        }

        List<String> nasIpList = new ArrayList<>();
        int rowCount = 0;
        int skippedCount = 0;

        for (Row row : rows) {
            rowCount++;
            try {
                String nasIp = row.getString("NAS_IP_ADDRESS");
                if (nasIp != null && !nasIp.trim().isEmpty()) {
                    nasIpList.add(nasIp.trim());
                } else {
                    skippedCount++;
                    LoggingUtil.logWarn(LOG, CLASS_NAME, "processDbResponse",
                            "Found null or empty NAS_IP_ADDRESS in row %d", rowCount);
                }
            } catch (Exception e) {
                skippedCount++;
                LoggingUtil.logError(LOG, CLASS_NAME, "processDbResponse", e,
                        "Error processing row %d for NAS IP", rowCount);
            }
        }

        return nasIpList;
    }

    private String processDbResponseForBarredPlan(RowSet<Row> rows) {
        if (rows == null || rows.size() == 0) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processDbResponseForBarredPlan",
                    "No BNG code found for global plan type");
            return null;
        }

        Row row = rows.iterator().next();
        String bngCode = row.getString("BNG_CODE");

        if (bngCode == null || bngCode.trim().isEmpty()) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processDbResponseForBarredPlan",
                    "BNG code is null or empty");
            return null;
        }

        return bngCode;
    }

    private List<VendorAttributeConfig> processVendorConfigResponse(RowSet<Row> rows) {
        if (rows == null || rows.size() == 0) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, "processVendorConfigResponse",
                    "No active NAS with vendor configs found in database");
            return List.of();
        }

        var configMap = new java.util.LinkedHashMap<String, VendorAttributeConfig>();
        int rowCount = 0;
        int skippedCount = 0;

        for (Row row : rows) {
            rowCount++;
            try {
                String nasIp = row.getString("NAS_IP_ADDRESS");
                String vendorName = row.getString("VENDOR_NAME");

                if (nasIp == null || nasIp.trim().isEmpty()) {
                    skippedCount++;
                    continue;
                }

                nasIp = nasIp.trim();

                VendorAttributeConfig config = configMap.computeIfAbsent(nasIp, ip -> {
                    String vendorId = row.getString("VENDOR_ID");
                    if (vendorId != null) {
                        return new VendorAttributeConfig(ip, vendorName, Integer.parseInt(vendorId));
                    }
                    return null;
                });

                String attributeId = row.getString("ATTRIBUTE_ID");
                if (attributeId != null && config != null) {
                    VendorAttribute attribute = new VendorAttribute(
                            Integer.parseInt(attributeId),
                            row.getString("ATTRIBUTE_NAME"),
                            row.getString("VALUE_PATH"),
                            row.getString("DATA_TYPE"),
                            row.getString("ATTRIBUTE_PREFIX")
                    );
                    config.addAttribute(attribute);
                }

            } catch (Exception e) {
                skippedCount++;
                LoggingUtil.logError(LOG, CLASS_NAME, "processVendorConfigResponse", e,
                        "Error processing row %d", rowCount);
            }
        }

        return new ArrayList<>(configMap.values());
    }
}
