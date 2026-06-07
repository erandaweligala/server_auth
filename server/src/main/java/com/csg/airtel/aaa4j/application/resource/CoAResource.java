package com.csg.airtel.aaa4j.application.resource;

import com.csg.airtel.aaa4j.common.constant.AuthServiceConstants;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.service.ResponseHandler;
import com.csg.airtel.aaa4j.exception.BaseException;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;
import org.slf4j.MDC;


@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoAResource {
    private final Logger logger = Logger.getLogger(CoAResource.class);
    private static final String INTERNAL_SERVER_ERROR_JSON = "{\"error\":\"Internal server error\"}";
    private final ResponseHandler responseHandler;

    public CoAResource(ResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
    }

    @POST
    @Path("/coa")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Timeout(3000)
    public Uni<Response> coa(AccountingResponseEvent request) {
        setMdcContext(request);

        return responseHandler.processAccountingResponse(request)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(BaseException.class).recoverWithItem(this::handleBaseException)
                .onFailure().recoverWithItem(this::handleUnexpectedError)
                .eventually(MDC::clear);
    }

    private Response handleBaseException(Throwable e) {
        BaseException be = (BaseException) e;

        return Response.status(be.getHttpStatus())
                .entity(be.getMessage())
                .build();
    }

    private Response handleUnexpectedError(Throwable e) {
        LoggingUtil.logError(logger, null, "coa", e, "Critical failure");
        return Response.serverError().entity(INTERNAL_SERVER_ERROR_JSON).build();
    }

    private void setMdcContext(AccountingResponseEvent request) {
        MDC.put(AuthServiceConstants.TRACE_ID, request.traceId());
        MDC.put(AuthServiceConstants.PARAM_USER_NAME, request.userName());
        MDC.put(AuthServiceConstants.SESSION_ID, request.sessionId());
    }

}
