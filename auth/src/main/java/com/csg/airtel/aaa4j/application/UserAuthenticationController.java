package com.csg.airtel.aaa4j.application;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.Constants;
import com.csg.airtel.aaa4j.domain.model.AuthenticationRequest;
import com.csg.airtel.aaa4j.domain.service.UserAuthenticationService;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.csg.airtel.aaa4j.exception.BusinessValidationException;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserAuthenticationController {

    private final UserAuthenticationService userAuthenticationService;
    private static final Logger LOG = Logger.getLogger(UserAuthenticationController.class);
    private static final String CLASS_NAME = "UserAuthenticationController";

    @Inject
    public UserAuthenticationController(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    /**
     * Authenticates a user based on username and password.
     * Applies a circuit breaker to prevent repeated failures if the underlying authentication
     * service becomes unavailable. Logs all activities, uses retry and timeout strategies,
     * and falls back to a safe response on failure.
     *
     * @param request the authentication request
     * @return Uni<Response> containing authenticated UserDetails if found
     */
    @POST
    @Path("/authenticate")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @CircuitBreaker(
            requestVolumeThreshold = 5,   // Start evaluating after 5 requests
            failureRatio = 0.5,           // Open if 50% fail
            delay = 5000,                 // Wait 5s before half-open state
            successThreshold = 2          // Need 2 successes to close
    )
    @Retry(maxRetries = 2, delay = 200)  // Retry twice quickly on failure
    @Timeout(3000)                       // Timeout after 3s
    @Fallback(fallbackMethod = "fallbackAuthenticate")
    public Uni<Response> authenticate(AuthenticationRequest request) {
        LoggingUtil.logInfo(LOG, CLASS_NAME, "authenticate",
                "Authentication request initiated username=%s nasIp=%s",
                request.getUsername(), request.getNasIpAddress());

        long startTime = System.nanoTime();

        return userAuthenticationService.userAuthenticate(request)
                .onItem().transform(user -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    LoggingUtil.logInfo(LOG, CLASS_NAME, "authenticate",
                            "Authentication successful username=%s [%d ms]",
                            request.getUsername(), durationMs);
                    return Response.ok(user).build();
                })
                .onFailure(BaseException.class).recoverWithItem(e -> {
                    BaseException be = (BaseException) e;
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    LoggingUtil.logError(LOG, CLASS_NAME, "authenticate", e,
                            "BaseException username=%s code=%s [%d ms]",
                            request.getUsername(), be.getResponseCode(), durationMs);
                    return Response.status(be.getHttpStatus())
                            .entity(Map.of(
                                    Constants.ERROR, be.getMessage(),
                                    Constants.CODE, be.getResponseCode(),
                                    Constants.DESCRIPTION, be.getDescription()
                            ))
                            .build();
                })
                .onFailure(BusinessValidationException.class).recoverWithItem(e -> {
                    BusinessValidationException be = (BusinessValidationException) e;
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    LoggingUtil.logError(LOG, CLASS_NAME, "authenticate", null,
                            "BaseException username=%s code=%s [%d ms]",
                            request.getUsername(), be.getErrorCode(), durationMs);
                    return Response.status(be.getHttpStatus())
                            .entity(Map.of(
                                    Constants.ERROR, be.getMessage(),
                                    Constants.CODE, be.getErrorCode(),
                                    Constants.DESCRIPTION, be.getDescription()
                            ))
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    LoggingUtil.logError(LOG, CLASS_NAME, "authenticate", e,
                            "Unexpected exception username=%s [%d ms]",
                            request.getUsername(), durationMs);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of(
                                    Constants.ERROR, "Internal server error",
                                    Constants.MESSAGE, e.getMessage()
                            ))
                            .build();
                });
    }

    /**
     * Fallback method triggered when the main authentication method fails,
     * times out, or the circuit breaker is open.
     *
     * @param request the authentication request attempted
     * @return Uni<Response> containing a service unavailable response
     */
    public Uni<Response> fallbackAuthenticate(AuthenticationRequest request) {
        LoggingUtil.logWarn(LOG, CLASS_NAME, "fallbackAuthenticate",
                "Fallback triggered username=%s", request.getUsername());
        return Uni.createFrom().item(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of(
                                "error", "Service temporarily unavailable. Please try again later.",
                                "username", request.getUsername()
                        ))
                        .build()
        );
    }
}