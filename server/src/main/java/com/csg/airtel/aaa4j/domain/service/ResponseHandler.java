package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.model.RadiusConfig;
import com.csg.airtel.aaa4j.domain.model.coa.CoADisconnectResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.aaa4j.radius.core.attribute.*;
import org.aaa4j.radius.core.attribute.attributes.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

@ApplicationScoped
public class ResponseHandler {

    private static final Logger log = Logger.getLogger(ResponseHandler.class);
    private static final String CLASS_NAME = "ResponseHandler";
    public static final int COA_DISCONNECT_REQ = 40;
    public static final String PROCESS_ACCOUNTING_RESPONSE = "processAccountingResponse";
    public static final String HANDLE_COA_EVENT = "handleCoaEvent";
    public static final String BUILD_ATTRIBUTES = "buildAttributes";

    private final RadiusClientService radiusClientService;
    private final BngRegistry bngRegistry;


    @ConfigProperty(name = "client.address")
    String serverAddress;

    @ConfigProperty(name = "client.coa-port")
    int coaPort;

    @ConfigProperty(name = "radius.accounting.port")
    int accountingPort;

    @ConfigProperty(name = "client.shared-secret")
    String sharedSecret;

    @Inject
    public ResponseHandler(RadiusClientService radiusClientService, BngRegistry bngRegistry) {
        this.radiusClientService = Objects.requireNonNull(radiusClientService,
                "radiusClientService cannot be null");
        this.bngRegistry = Objects.requireNonNull(bngRegistry,
                "bngRegistry cannot be null");
        LoggingUtil.logDebug(log, CLASS_NAME, "constructor", "AccountResponseHandler initialized");
    }
/**
    public Uni<Void> processAccountingResponse(AccountingResponseEvent responseEvent) {
        log.infof("[traceId : %s] Starting processing of accounting response event for sessionId: %s",
                responseEvent.eventId(), responseEvent.sessionId());

        if (log.isDebugEnabled()) {
            log.debugf("Processing accounting response event: eventType=%s, action=%s, sessionId=%s",
                    responseEvent.eventType(),
                    responseEvent.action(),
                    responseEvent.sessionId());
        }

        return Uni.createFrom().item(responseEvent)
                .chain(event -> switch (event.eventType()) {
                    case COA -> handleCoaEvent(event);
                    case CONTINUE -> handlePackageUpgradeEvent(event);
                    case NO_RESPONSE -> handleNoResponse();
                })
                .onFailure().invoke(e ->
                        log.errorf(e, "Error processing accounting response event for sessionId: %s",
                                responseEvent.sessionId())
                );
    }
*/

    public Uni<CoADisconnectResponse> processAccountingResponse(AccountingResponseEvent responseEvent) {
        LoggingUtil.logInfo(log, CLASS_NAME, PROCESS_ACCOUNTING_RESPONSE,
                "Starting processing of accounting COA event for Event Type: %s sessionId: %s",
                responseEvent.eventType().name(), responseEvent.sessionId());

        LoggingUtil.logDebug(log, CLASS_NAME, PROCESS_ACCOUNTING_RESPONSE,
                "Processing accounting response event: eventType=%s, action=%s, sessionId=%s",
                responseEvent.eventType(), responseEvent.action(), responseEvent.sessionId());

        return Uni.createFrom().item(responseEvent)
                .chain(event -> switch (event.eventType()) {
                    case COA -> handleCoaEvent(event);
                    case CONTINUE -> handlePackageUpgradeEvent(event);
                    case NO_RESPONSE -> handleNoResponse();
                })
                .onFailure().invoke(e ->
                        LoggingUtil.logError(log, CLASS_NAME, PROCESS_ACCOUNTING_RESPONSE, e,
                                "Error processing accounting response event for sessionId: %s",
                                responseEvent.sessionId())
                );
    }
/**
    private Uni<Void> handleCoaEvent(AccountingResponseEvent responseEvent) {
        log.info("Handling COA Disconnect event type");

        if (responseEvent.action() == AccountingResponseEvent.ResponseAction.DISCONNECT) {
            log.infof("Initiating COA Disconnect request for sessionId: %s",
                    responseEvent.sessionId());


            String nasIP = responseEvent.qosParameters().get("nasIP");

            return radiusClientService.initiate(
                    buildAttributes(responseEvent.qosParameters()),
                    COA_DISCONNECT_ACK,
                    new RadiusConfig(nasIP, coaPort, sharedSecret)
            );
        } else {
            log.debug("COA action is FUP Apply, COA request");
            return Uni.createFrom().voidItem();
        }
    }
*/
    private Uni<CoADisconnectResponse> handleCoaEvent(AccountingResponseEvent responseEvent) {
        LoggingUtil.logInfo(log, CLASS_NAME, HANDLE_COA_EVENT, "Handling COA Disconnect event type");

        if (responseEvent.action() == AccountingResponseEvent.ResponseAction.DISCONNECT) {
            LoggingUtil.logDebug(log, CLASS_NAME, HANDLE_COA_EVENT,
                    "Initiating COA Disconnect request for sessionId: %s", responseEvent.sessionId());

            String nasIP = responseEvent.qosParameters().get("nasIP");
            LoggingUtil.logInfo(log, CLASS_NAME, HANDLE_COA_EVENT,
                    "Using port  %s secret %s for NAS IP: %s", coaPort,sharedSecret,nasIP);
            return radiusClientService.initiateCoA(
                    buildAttributes(responseEvent.qosParameters()),
                    COA_DISCONNECT_REQ,
                    new RadiusConfig(nasIP, coaPort, sharedSecret)
            );
        } else {
            LoggingUtil.logDebug(log, CLASS_NAME, HANDLE_COA_EVENT, "COA action is FUP Apply, no disconnect needed");
            return Uni.createFrom().item(
                    new CoADisconnectResponse(
                            "SUCCESS",
                            responseEvent.sessionId(),
                            "FUP applied - no disconnect required"
                    )
            );
        }
    }

    private Uni<CoADisconnectResponse> handlePackageUpgradeEvent(AccountingResponseEvent responseEvent) {
        LoggingUtil.logDebug(log, CLASS_NAME, "handlePackageUpgradeEvent",
                "Initiating Package Upgrade for sessionId: %s", responseEvent.sessionId());

        return radiusClientService.initiateCoA(
                buildAccountingAttributes(responseEvent),
                5,
                new RadiusConfig(serverAddress, accountingPort, sharedSecret)
        ).onItem().transform(result -> {
            LoggingUtil.logInfo(log, CLASS_NAME, "handlePackageUpgradeEvent",
                    "Complete Package Upgrade for sessionId: %s", responseEvent.sessionId());
            return result;
        });
    }

    private Uni<CoADisconnectResponse> handleNoResponse() {
        LoggingUtil.logError(log, CLASS_NAME, "handleNoResponse", null,
                "Failed to create AccountingResponse - sessionId is null or blank");
        return Uni.createFrom().item(
                new CoADisconnectResponse(
                        "FAILED",
                        null,
                        "No response required - invalid session data"
                )
        );
    }

    private List<Attribute<?>> buildAccountingAttributes(AccountingResponseEvent responseEvent) {
        List<Attribute<?>> attributes = new ArrayList<>();
        LoggingUtil.logInfo(log, CLASS_NAME, "buildAccountingAttributes",
                "Building AccountingResponse attributes for sessionId: %s", responseEvent.sessionId());

        attributes.add(new AcctSessionId(new TextData(responseEvent.sessionId())));
        attributes.add(new ReplyMessage(new TextData(responseEvent.message())));

        return attributes;
    }

    private List<Attribute<?>> buildAttributes(Map<String, String> qosParameters) {
        List<Attribute<?>> attributes = new ArrayList<>();

        // Build attributes in RFC 5176 recommended order: User-Name, NAS-IP-Address,
        // Framed-IP-Address, Acct-Session-Id. Using explicit ordering instead of
        // HashMap.forEach() which gives non-deterministic order and can cause BNGs
        // to silently drop Disconnect-Requests.
        Map<String, String> params = new LinkedHashMap<>();
        qosParameters.forEach((k, v) -> params.put(k.toLowerCase(), v));

        String username = params.get("username");
        if (username != null && !username.trim().isEmpty()) {
            attributes.add(new UserName(new TextData(username)));
            LoggingUtil.logInfo(log, CLASS_NAME, BUILD_ATTRIBUTES,
                    "Added UserName attribute: %s", username);
        }

        String nasIP = params.get("nasIP");
        if (nasIP != null && !nasIP.trim().isEmpty()) {
            parseIpAddress(nasIP).ifPresent(ip -> {
                attributes.add(new NasIpAddress(new Ipv4AddrData(ip)));
                LoggingUtil.logInfo(log, CLASS_NAME, BUILD_ATTRIBUTES,
                        "Added NasIpAddress attribute: %s", nasIP);
            });
        }

        String framedIP = params.get("framedIP");
        if (framedIP != null && !framedIP.trim().isEmpty()) {
            parseIpAddress(framedIP).ifPresent(ip -> {
                attributes.add(new FramedIpAddress(new Ipv4AddrData(ip)));
                LoggingUtil.logInfo(log, CLASS_NAME, BUILD_ATTRIBUTES,
                        "Added FramedIpAddress attribute: %s", framedIP);
            });
        }

        String sessionId = params.get("sessionId");
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            attributes.add(new AcctSessionId(new TextData(sessionId)));
            LoggingUtil.logInfo(log, CLASS_NAME, BUILD_ATTRIBUTES,
                    "Added AcctSessionId attribute: %s", sessionId);
        }

        params.forEach((key, value) -> {
            if (!key.equals("username") && !key.equals("nasIP") && !key.equals("framedIP") && !key.equals("sessionId")) {
                LoggingUtil.logInfo(log, CLASS_NAME, BUILD_ATTRIBUTES,
                        "Unknown QoS parameter: %s = %s", key, value);
            }
        });

/**
    this config used for CoA testing with BNG VsaData
        String profile = "Home_20Mbps";
        byte[] valueBytes = profile.getBytes(StandardCharsets.UTF_8);
        int vendorAttribute = Integer.parseInt("1");
        VsaData vsaData = new VsaData(6527, vendorAttribute, valueBytes);
        attributes.add(new VendorSpecific(vsaData));
 */
        return attributes;
    }

    /**
     * Parse IP address with proper error handling
     */
    private Optional<Inet4Address> parseIpAddress(String ipString) {
        try {
            InetAddress address = InetAddress.getByName(ipString);
            if (address instanceof Inet4Address inet4Address) {
                return Optional.of(inet4Address);
            } else {
                LoggingUtil.logInfo(log, CLASS_NAME, "parseIpAddress",
                        "Invalid IPv4 address format, expected IPv4 but got IPv6 or invalid format: %s", ipString);
                return Optional.empty();
            }
        } catch (UnknownHostException e) {
            LoggingUtil.logError(log, CLASS_NAME, "parseIpAddress", e,
                    "Failed to parse IP address '%s'. COA request will continue without this attribute.", ipString);
            return Optional.empty();
        }
    }
}