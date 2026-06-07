package com.csg.airtel.aaa4j.application.listner;

import com.csg.airtel.aaa4j.common.constant.AuthServiceConstants;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.service.ResponseHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


@ApplicationScoped
public class AccountResponseListener {

    private static final Logger logger = Logger.getLogger(AccountResponseListener.class);
    private static final String CLASS_NAME = "AccountResponseListener";
    public static final String CONSUME_WITH_ACK = "consumeWithAck";

    final ResponseHandler accountResponseHandler;

   @Inject
    public AccountResponseListener(ResponseHandler accountResponseHandler) {
        this.accountResponseHandler = accountResponseHandler;
    }

    @Incoming("accounting-resp-events")
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    public CompletionStage<Void> consumeWithAck(Message<AccountingResponseEvent> message) {
        AccountingResponseEvent payload = message.getPayload();
        setMdcContext(payload);

        LoggingUtil.logInfo(logger, CLASS_NAME, CONSUME_WITH_ACK,
                "Account Event Response Received %s", payload.eventType().name());

        // Capture MDC snapshot so async callbacks can restore it on their threads
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();

        // Message is already acked before this method is called
        accountResponseHandler.processAccountingResponse(payload)
                .subscribe().with(
                        result -> {
                            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
                            try {
                                LoggingUtil.logInfo(logger, CLASS_NAME, CONSUME_WITH_ACK,
                                        "Successfully processed event: %s", payload.eventType().name());
                            } finally {
                                MDC.clear();
                            }
                        },
                        throwable -> {
                            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
                            try {
                                LoggingUtil.logError(logger, CLASS_NAME, CONSUME_WITH_ACK, throwable,
                                        "Error processing event: %s", payload.eventType().name());
                            } finally {
                                MDC.clear();
                            }
                        }
                );

        MDC.clear();
        return CompletableFuture.completedFuture(null);
    }

    private void setMdcContext(AccountingResponseEvent event) {
        if (event.traceId() != null) MDC.put(AuthServiceConstants.TRACE_ID, event.traceId());
        if (event.userName() != null) MDC.put(AuthServiceConstants.PARAM_USER_NAME, event.userName());
        if (event.sessionId() != null) MDC.put(AuthServiceConstants.SESSION_ID, event.sessionId());
    }
}
