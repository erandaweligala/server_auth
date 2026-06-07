package com.csg.airtel.aaa4j.external.client;

import com.csg.airtel.aaa4j.application.config.WebClientProvider;
import com.csg.airtel.aaa4j.common.constant.AuthServiceConstants;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.common.util.TraceIdGenerator;
import com.csg.airtel.aaa4j.domain.model.UserDetails;
import com.csg.airtel.aaa4j.metrics.service.ExternalApiMetricsService;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.protocol.types.Field;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@ApplicationScoped
public class AuthManagementServiceClient {

    public static final String AUTHENTICATE = "authenticate";
    public static final String ERROR = "error";
    private final WebClientProvider webClientProvider;
    private static final Logger logger = Logger.getLogger(AuthManagementServiceClient.class);
    private static final String CLASS_NAME = "AuthManagementServiceClient";

    /**
     * Fail-fast timeout (ms) for blocking on the HTTP response.
     * Blocking on future.get() ties up a worker thread for the whole round trip,
     * so this must stay small to keep throughput high under load. It is set just
     * above the auth service's own internal budget (~100ms) so that, on a slow
     * downstream, the server receives the auth service's fast fallback response
     * instead of tripping its own timeout. At ~286 TPS/pod the worst-case number
     * of simultaneously blocked threads is ~286 x 0.15s ~= 43.
     */
    private static final long RESPONSE_TIMEOUT_MS = 150;
    private final ExternalApiMetricsService externalMetrics;

    @Inject
    public AuthManagementServiceClient(WebClientProvider webClientProvider,
                                       ExternalApiMetricsService externalMetrics) {
        this.webClientProvider = webClientProvider;
        this.externalMetrics = externalMetrics;
    }

    @ConfigProperty(name = "auth.service.url")
    String authServiceUrl;

    public UserDetails authenticateUser(String username, String password, String chapChallenge, String chapPassword, String nasIpAddress, String framedProtocol) throws ExecutionException, InterruptedException {
        WebClient client = webClientProvider.getClient();
        CompletableFuture<UserDetails> future = new CompletableFuture<>();
        long startNs = System.nanoTime();
        int[] statusCodeHolder = {0};

        String traceId = MDC.get(AuthServiceConstants.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generateTraceId();
            MDC.put(AuthServiceConstants.TRACE_ID, traceId);
        }

        JsonObject body = new JsonObject();
        body.put("username", username);
        validateAttributes(password, chapChallenge, chapPassword, nasIpAddress, body, framedProtocol);

        LoggingUtil.logDebug(logger, CLASS_NAME, AUTHENTICATE,
                "Sending authentication request to: %s for user: %s", authServiceUrl, username);

        client.postAbs(authServiceUrl)
                .putHeader(AuthServiceConstants.HEADER_TRACE_ID, traceId)
                .putHeader(AuthServiceConstants.HEADER_USER_NAME, username)
                .putHeader("Content-Type", "application/json")
                .timeout(RESPONSE_TIMEOUT_MS)
                .sendJsonObject(body, ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        int statusCode = response.statusCode();
                        statusCodeHolder[0] = statusCode;
                        responseValidate(username, statusCode, response, future);
                    } else {
                        statusCodeHolder[0] = -1;
                        LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE, ar.cause(),
                                "HTTP request failed");
                        future.completeExceptionally(ar.cause());
                    }
                });

        UserDetails result = null;
        String outcome = "success";
        String exception = "none";

        try {
            result = future.get(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return result;
        } catch (TimeoutException e) {
            outcome = "failure";
            exception = "TimeoutException";
            statusCodeHolder[0] = -1;
            LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE, null,
                    "Authentication request timed out after %d ms for user: %s",
                    RESPONSE_TIMEOUT_MS, username);
            future.cancel(true);
            return null;
        } catch (ExecutionException e) {
            outcome = "failure";
            exception = e.getCause() != null
                    ? e.getCause().getClass().getSimpleName()
                    : "ExecutionException";
            LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE, e,
                    "%s: %s", AuthServiceConstants.MSG_INTERNAL_ERROR, e.getMessage());
            return null;
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            externalMetrics.recordExternalCall(
                    "auth-management-service",   // system/service name
                    "authenticateUser",          // operation
                    "POST",                      // method
                    authServiceUrl,              // path
                    statusCodeHolder[0],         // actual HTTP status, or -1 on error
                    outcome,
                    exception,
                    durationMs
            );
            MDC.remove(AuthServiceConstants.TRACE_ID);
        }
    }

    private void responseValidate(String username, int statusCode, HttpResponse<Buffer> response, CompletableFuture<UserDetails> future) {
        if (statusCode >= 200 && statusCode < 300) {
            JsonObject json = response.bodyAsJsonObject();

            if (json.containsKey(ERROR)) {
                handleErrorResponse(json, future, true);
            } else {
                UserDetails user = json.mapTo(UserDetails.class);
                user.setUserAvailable(true);
                LoggingUtil.logDebug(logger, CLASS_NAME, AUTHENTICATE,
                        "Authentication successful for user: %s", username);
                future.complete(user);
            }
        } else {
            JsonObject json = response.bodyAsJsonObject();

            if (json != null && json.containsKey(ERROR)) {
                handleErrorResponse(json, future, false);
            } else {
                LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE, null,
                        "Authentication failed with status %d: %s",
                        statusCode, response.bodyAsString());
                future.completeExceptionally(new RuntimeException("Authentication failed with status: " + statusCode));
            }
        }
    }

    private static void validateAttributes(String password, String chapChallenge, String chapPassword, String nasIpAddress, JsonObject body, String framedProtocol) {
        if (password != null && !password.isBlank()) {
            body.put("password", password);
        }
        if (chapChallenge != null && !chapChallenge.isBlank()) {
            body.put("chapChallenge", chapChallenge);
        }
        if (chapPassword != null && !chapPassword.isBlank()) {
            body.put("chapPassword", chapPassword);
        }
        if (nasIpAddress != null && !nasIpAddress.isBlank()) {
            body.put("nasIpAddress", nasIpAddress);
        }
        if (framedProtocol != null && !framedProtocol.isBlank()) {
            body.put("framedProtocol", framedProtocol);
        }
    }

    /**
     * Handles error responses from the auth service, reducing duplicated
     * parsing logic between 2xx-with-error and non-2xx-with-error paths.
     */
    private void handleErrorResponse(JsonObject json, CompletableFuture<UserDetails> future, boolean isHttpSuccess) {
        String errorCode = json.getString("code");
        String errorDescription = json.getString("description");
        LoggingUtil.logError(logger, CLASS_NAME, "handleErrorResponse", null,
                "Authentication failed with error: %s", json.getString(ERROR));
        if ("E2001".equals(errorCode)) {
            UserDetails errorUser = new UserDetails();
            if (isHttpSuccess) {
                errorUser.setIsAuthorized(false);
            } else {
                errorUser.setUserAvailable(false);
            }
            future.complete(errorUser);
        } else {
            future.completeExceptionally(new RuntimeException(errorDescription));
        }
    }
}