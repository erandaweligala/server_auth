package com.csg.airtel.aaa4j.application;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.Constants;
import com.csg.airtel.aaa4j.domain.service.CacheSchedulerService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

@Path("cache")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CacheController {

    private static final Logger LOG = Logger.getLogger(CacheController.class);
    private static final String CLASS_NAME = "CacheController";

    private final CacheSchedulerService cacheSchedulerService;

    @Inject
    public CacheController(CacheSchedulerService cacheSchedulerService) {
        this.cacheSchedulerService = cacheSchedulerService;
    }

    /**
     * Manually trigger cache update (useful for testing or emergency refresh)
     * POST /cache/update-NAS
     */
    @POST
    @Path("/update-NAS")
    public Uni<Response> refreshCache() {
        String traceId = MDC.get(Constants.TRACE_ID);
        LoggingUtil.logInfo(LOG, CLASS_NAME, "refreshCache",
                "REST request to manually refresh cache - traceId: %s", traceId);

        return Uni.createFrom().voidItem()
                .onItem().invoke(cacheSchedulerService::updateAllCaches)
                .onItem().transform(ignored -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Cache refresh initiated successfully");
                    response.put("traceId", traceId);

                    return Response.accepted(response).build();
                })
                .onFailure().recoverWithItem(failure -> {
                    LoggingUtil.logError(LOG, CLASS_NAME, "authenticate", failure,
                            "Error initiating cache refresh - traceId: %s", traceId);

                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Failed to initiate cache refresh");
                    errorResponse.put("message", failure.getMessage());
                    errorResponse.put("traceId", traceId);

                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(errorResponse)
                            .build();
                });
    }
}