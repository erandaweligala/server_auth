package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.constant.AuthServiceConstants;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.common.util.TPSRateLimiter;
import com.csg.airtel.aaa4j.common.util.TraceIdGenerator;
import com.csg.airtel.aaa4j.domain.model.UserDetails;
import com.csg.airtel.aaa4j.external.client.AuthManagementServiceClient;
import com.csg.airtel.aaa4j.metrics.radius.RadiusMetered;
import com.csg.airtel.aaa4j.metrics.request.TimedRequest;
import jakarta.enterprise.context.ApplicationScoped;
import org.aaa4j.radius.core.attribute.Attribute;
import org.aaa4j.radius.core.attribute.TextData;
import org.aaa4j.radius.core.attribute.VsaData;
import org.aaa4j.radius.core.attribute.attributes.*;
import org.aaa4j.radius.core.packet.Packet;
import org.aaa4j.radius.core.packet.packets.AccessAccept;
import org.aaa4j.radius.core.packet.packets.AccessReject;
import org.aaa4j.radius.core.packet.packets.AccessRequest;
import org.aaa4j.radius.server.RadiusServer;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class RadiusAuthenticationHandler implements RadiusServer.Handler {
    private static final Logger logger = Logger.getLogger(RadiusAuthenticationHandler.class);
    private static final String CLASS_NAME = "RadiusAuthenticationHandler";
    public static final String BUILD_ACCESS_ACCEPT = "buildAccessAccept";
    public static final String HANDLE_PACKET = "handlePacket";
    public static final String AUTHENTICATE_USER = "authenticateUser";

    final AuthManagementServiceClient authManagementServiceClient;
    final TPSRateLimiter tpsRateLimiter;
    final VendorAttributeBuilder vendorAttributeBuilder;
    final BngRegistry bngRegistry;

    public RadiusAuthenticationHandler(
            AuthManagementServiceClient authManagementServiceClient,
            TPSRateLimiter tpsRateLimiter,
            VendorAttributeBuilder vendorAttributeBuilder,
            BngRegistry bngRegistry) {
        this.authManagementServiceClient = authManagementServiceClient;
        this.tpsRateLimiter = tpsRateLimiter;
        this.vendorAttributeBuilder = vendorAttributeBuilder;
        this.bngRegistry = bngRegistry;
    }

    @Override
    public byte[] handleClient(InetAddress clientAddress) {
        byte[] secret = bngRegistry.getAllowedSharedSecretBytes(clientAddress);
        if (secret == null) {
            LoggingUtil.logWarn(logger, CLASS_NAME, "handleClient",
                    "Dropping authentication connection from unlisted IP: %s",
                    clientAddress.getHostAddress());
        }
        return secret;
    }

    @RadiusMetered
    @TimedRequest(type = "authentication")
    @Override
    public Packet handlePacket(InetAddress clientAddress, Packet requestPacket) {
        String traceId = TraceIdGenerator.generateTraceId();
        MDC.put(AuthServiceConstants.TRACE_ID, traceId);

        Instant startTime = Instant.now();
        // Per-request timing is exported via Micrometer histograms (@TimedRequest);
        // keep the log line at DEBUG to avoid formatting cost on the hot path.
        LoggingUtil.logDebug(logger, CLASS_NAME, HANDLE_PACKET,
                "[RADIUS_TIMING] [AUTHENTICATION] Request started at: %s", startTime);
        Packet resultPacket = null;
        try {
            /** TPS Rate Limiting - Check BEFORE any processing
             LoggingUtil.logInfo(logger, CLASS_NAME, "handlePacket",
             "*** CHECKING TPS LIMIT *** (tpsRateLimiter: %s)",
             tpsRateLimiter != null ? "EXISTS" : "NULL");

             if (tpsRateLimiter != null && !tpsRateLimiter.tryAcquire(traceId)) {
             LoggingUtil.logError(logger, CLASS_NAME, "handlePacket", null,
             "*** REQUEST REJECTED: TPS LIMIT EXCEEDED *** (%d TPS)",
             tpsRateLimiter.getMaxTPS());
             return null;
             }

             LoggingUtil.logInfo(logger, CLASS_NAME, "handlePacket",
             "*** TPS CHECK PASSED *** - Proceeding with authentication");

             */

            LoggingUtil.logDebug(logger, CLASS_NAME, HANDLE_PACKET,
                    "handlePacket() triggered — processing RADIUS request");
            logPacketReceived(clientAddress, requestPacket);

            if (!(requestPacket instanceof AccessRequest)) {
                LoggingUtil.logWarn(logger, CLASS_NAME, HANDLE_PACKET,
                        "Unsupported packet type received: %s",
                        requestPacket.getClass().getSimpleName());
                return null;
            }
            resultPacket = handleAccessRequest((AccessRequest) requestPacket); // assign to variable
            return resultPacket;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LoggingUtil.logError(logger, CLASS_NAME, HANDLE_PACKET, ie,
                    "Packet processing interrupted: %s", ie.getMessage());
            resultPacket = buildAccessReject("Request interrupted");
            return resultPacket;

        } catch (Exception e) {
            LoggingUtil.logError(logger, CLASS_NAME, HANDLE_PACKET, e,
                    "Error while processing packet: %s", e.getMessage());
            resultPacket = buildAccessReject("Internal server error occurred. Please try again later.");
            return resultPacket;
        } finally {
            Instant endTime = Instant.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();
            String authResult = resolveAuthResult(resultPacket);

            LoggingUtil.logDebug(logger, CLASS_NAME, HANDLE_PACKET,
                    "[RADIUS_TIMING] [AUTHENTICATION] Request ended at: %s | Duration: %d ms | Result: %s",
                    endTime, durationMs, authResult);

            MDC.remove(AuthServiceConstants.TRACE_ID);
            MDC.remove(AuthServiceConstants.PARAM_USER_NAME);
        }
    }

    private String resolveAuthResult(Packet packet) {
        if (packet instanceof AccessAccept) return "ACCESS_ACCEPT";
        if (packet instanceof AccessReject) return "ACCESS_REJECT";
        return "NO_RESPONSE";
    }

    private void logPacketReceived( InetAddress clientAddress, Packet packet) {
        LoggingUtil.logDebug(logger, CLASS_NAME, "logPacketReceived",
                "Received packet from %s: %s",
                clientAddress.getHostAddress(), packet.getClass().getSimpleName());
    }

    private Packet handleAccessRequest(AccessRequest requestPacket) throws InterruptedException {
        Optional<UserName> userNameAttr = requestPacket.getAttribute(UserName.class);
        Optional<ChapChallenge> chapChallengeAttribute = requestPacket.getAttribute(ChapChallenge.class);
        Optional<ChapPassword> chapPasswordAttribute = requestPacket.getAttribute(ChapPassword.class);
        Optional<NasIpAddress> nasIpAddressAttribute = requestPacket.getAttribute(NasIpAddress.class);
        Optional<FramedProtocol> framedProtocolAttribute = requestPacket.getAttribute(FramedProtocol.class);

        if (userNameAttr.isEmpty()) {
            LoggingUtil.logWarn(logger, CLASS_NAME, "handleAccessRequest",
                    "Missing required attributes in request");
            return buildAccessReject("Missing required attributes");
        }

        String username = userNameAttr.get().getData().getValue();
        MDC.put(AuthServiceConstants.PARAM_USER_NAME, username);

        String password = extractUserPassword(requestPacket);
        String chapChallenge = chapChallengeAttribute.map(attr -> bytesToHex(attr.getData().getValue())).orElse(null);
        String chapPassword = chapPasswordAttribute.map(attr -> bytesToHex(attr.getData().getValue())).orElse(null);
        String nasIpAddress = nasIpAddressAttribute
                .map(attr -> ((Inet4Address) attr.getData().getValue()).getHostAddress())
                .orElse(null);
        String framedProtocol = framedProtocolAttribute
                .map(attr -> String.valueOf(attr.getData().getValue()))
                .orElse(null);

        LoggingUtil.logDebug(logger, CLASS_NAME, "handleAccessRequest",
                "Authentication request for user: %s, password: %s, nasIpAddress: %s", username, password, nasIpAddress);
        return authenticateUser(username, password, chapChallenge, chapPassword, nasIpAddress, framedProtocol);
    }

    private Packet authenticateUser(String username, String password,
                                    String chapChallenge, String chapPassword, String nasIpAddress, String framedProtocol) throws InterruptedException {

        try{
            UserDetails userDetails = authManagementServiceClient.authenticateUser(username, password, chapChallenge, chapPassword, nasIpAddress, framedProtocol);

            if (userDetails != null && !userDetails.isUserAvailable()) {
                LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE_USER,null,
                        "Authentication failed — user does not exist: %s", username);
                return buildAccessReject("User does not exist");
            }

            if (userDetails != null && !userDetails.getIsActive()) {
                LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE_USER,null,
                        "Authentication failed — user inactive: %s", username);
                return buildAccessReject("User is inactive");
            }

            if (userDetails != null && !userDetails.getIsEnoughBalance()) {
                LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE_USER, null,
                        "Authentication failed — user don't have enough quota: %s", username);
                return buildAccessReject("User don't have enough quota");
            }

            if (userDetails != null && userDetails.getIsAuthorized()) {
                LoggingUtil.logDebug(logger, CLASS_NAME, AUTHENTICATE_USER,
                        "Authentication successful for user: %s", username);
                return buildAccessAccept(userDetails);
            } else if (userDetails != null && !userDetails.getIsAuthorized()) {
                LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE_USER, null,
                        "Authentication failed — invalid credentials: %s", username);
                return buildAccessReject("Invalid username or password");
            }
            return null;
        }catch (ExecutionException e) {
            LoggingUtil.logError(logger, CLASS_NAME, AUTHENTICATE_USER, e,
                    "ExecutionException during authentication");
            return null;
        }
    }

    private String extractUserPassword(Packet packet) {
        return packet.getAttribute(UserPassword.class)
                .map(attr -> new String(attr.getData().getValue(), StandardCharsets.UTF_8))
                .orElse(null);
    }

    @RadiusMetered
    Packet buildAccessReject(String message) {
        return new AccessReject(List.of(
                new MessageAuthenticator(),
                new ReplyMessage(new TextData(message))
        ));
    }

    @RadiusMetered
    Packet buildAccessAccept(UserDetails userDetails) {
        List<Attribute<?>> attributes = new ArrayList<>();
        attributes.add(new MessageAuthenticator());
        attributes.add(new ReplyMessage(new TextData("Welcome, " + userDetails.getUsername() + "!")));
        attributes.add(new UserName(new TextData(userDetails.getUsername())));

        // Add vendor-specific attributes dynamically
        if (userDetails.getVendorId() != null &&
                userDetails.getVendorAttributes() != null &&
                !userDetails.getVendorAttributes().isEmpty()) {

            LoggingUtil.logDebug(logger, CLASS_NAME, BUILD_ACCESS_ACCEPT,
                    "Adding vendor-specific attributes: vendorId=%d, attributeCount=%d",
                    userDetails.getVendorId(), userDetails.getVendorAttributes().size());

            List<Attribute<VsaData>> vendorAttributes = vendorAttributeBuilder.buildVendorAttributes(
                    userDetails.getVendorId(),
                    userDetails.getVendorAttributes()
            );

            attributes.addAll(vendorAttributes);

            LoggingUtil.logDebug(logger, CLASS_NAME, BUILD_ACCESS_ACCEPT,
                    "Added %d vendor-specific attributes to Access-Accept",
                    vendorAttributes.size());
        } else {
            LoggingUtil.logWarn(logger, CLASS_NAME, BUILD_ACCESS_ACCEPT,
                    "No vendor attributes to add: vendorId=%s, vendorAttributesEmpty=%s",
                    userDetails.getVendorId(),
                    userDetails.getVendorAttributes() == null || userDetails.getVendorAttributes().isEmpty());
            return buildAccessReject("No eligible plan found for user");
        }

        return new AccessAccept(attributes);
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }
}