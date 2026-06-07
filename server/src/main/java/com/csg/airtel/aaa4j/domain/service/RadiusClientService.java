package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.model.RadiusConfig;
import com.csg.airtel.aaa4j.domain.model.coa.CoADisconnectResponse;
import com.csg.airtel.aaa4j.exception.RadiusClientServiceException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import org.aaa4j.radius.client.RadiusClient;
import org.aaa4j.radius.client.RadiusClientException;
import org.aaa4j.radius.client.clients.UdpRadiusClient;
import org.aaa4j.radius.core.attribute.Attribute;
import org.aaa4j.radius.core.attribute.attributes.AcctSessionId;
import org.aaa4j.radius.core.attribute.attributes.FramedIpAddress;
import org.aaa4j.radius.core.attribute.attributes.NasIpAddress;
import org.aaa4j.radius.core.attribute.attributes.UserName;
import org.aaa4j.radius.core.packet.Packet;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class RadiusClientService {
    private static final Logger logger = Logger.getLogger(RadiusClientService.class);

    private static final int COA_DISCONNECT_REQ = 40;
    private static final int COA_ACK = 41;
    private static final int COA_NAK = 42;
    private static final int RADIUS_HEADER_LENGTH = 20;
    private static final int REQUEST_AUTH_OFFSET = 4;
    private static final int REQUEST_AUTH_LENGTH = 16;
    private static final int DISCONNECT_UDP_TIMEOUT_MS = 5000;

    public static final String INITIATE = "initiate";
    public static final String PROCESS_RESPONSE = "processResponse";
    public static final String SEND_COA_REQUEST = "sendCoaRequest";
    public static final String SEND_DISCONNECT_REQUEST = "sendDisconnectRequest";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Sends a COA (Change of Authorization) request to a RADIUS server reactively.
     */
    public Uni<CoADisconnectResponse> initiateCoA(List<Attribute<?>> attributes, int code, RadiusConfig radiusConfig) {
        LoggingUtil.logDebug(logger, null, INITIATE,
                "Initiating COA request (code=%d) to %s:%d", code, radiusConfig.serverAddress(), radiusConfig.port());

        if (code == COA_DISCONNECT_REQ) {
            return sendDisconnectRequest(attributes, radiusConfig)
                    .chain(this::processResponse)
                    .onFailure(RadiusClientServiceException.class)
                    .invoke(e -> LoggingUtil.logError(logger, null, INITIATE, e,
                            "RADIUS client service error while sending Disconnect-Request"))
                    .onFailure()
                    .invoke(e -> LoggingUtil.logError(logger, null, INITIATE, e,
                            "Unexpected error during Disconnect-Request"));
        }

        return Uni.createFrom().item(() -> createRadiusClient(radiusConfig))
                .chain(radiusClient -> sendCoaRequest(radiusClient, attributes, code))
                .chain(this::processResponse)
                .onFailure(RadiusClientServiceException.class)
                .invoke(e -> LoggingUtil.logError(logger, null, INITIATE, e,
                        "RADIUS client service error while sending COA request"))
                .onFailure(RadiusClientException.class)
                .invoke(e -> LoggingUtil.logError(logger, null, INITIATE, e,
                        "RADIUS client error while sending COA request"))
                .onFailure()
                .invoke(e -> LoggingUtil.logError(logger, null, INITIATE, e,
                        "Unexpected error during COA request"));
    }


    /**
     * Sends a RADIUS Disconnect-Request using a raw UDP socket with the correct
     */
    private Uni<Packet> sendDisconnectRequest(List<Attribute<?>> attributes, RadiusConfig config) {
        return Uni.createFrom().item(Unchecked.supplier(() -> {
            byte[] secret = config.sharedSecret().getBytes(UTF_8);

            byte[] attrsBytes = serializeDisconnectAttributes(attributes);

            // 2. Build the packet header with a zero-filled Request-Authenticator
            int identifier = secureRandom.nextInt(256);
            int totalLength = RADIUS_HEADER_LENGTH + attrsBytes.length;
            byte[] packetBytes = new byte[totalLength];
            packetBytes[0] = (byte) COA_DISCONNECT_REQ;
            packetBytes[1] = (byte) identifier;
            packetBytes[2] = (byte) (totalLength >> 8);
            packetBytes[3] = (byte) (totalLength & 0xFF);

            System.arraycopy(attrsBytes, 0, packetBytes, RADIUS_HEADER_LENGTH, attrsBytes.length);


            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(packetBytes);
            md5.update(secret);
            byte[] auth = md5.digest();
            System.arraycopy(auth, 0, packetBytes, REQUEST_AUTH_OFFSET, REQUEST_AUTH_LENGTH);

            LoggingUtil.logDebug(logger, null, SEND_DISCONNECT_REQUEST,
                    "Sending  Disconnect-Request to %s:%d (id=%d, length=%d bytes, attrs=%d)",
                    config.serverAddress(), config.port(), identifier, totalLength, attributes.size());


            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(DISCONNECT_UDP_TIMEOUT_MS);
                InetAddress bngAddress = InetAddress.getByName(config.serverAddress());
                socket.send(new DatagramPacket(packetBytes, packetBytes.length, bngAddress, config.port()));

                byte[] responseBuffer = new byte[4096];
                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                socket.receive(responsePacket);

                int responseCode = responseBuffer[0] & 0xFF;
                LoggingUtil.logDebug(logger, null, SEND_DISCONNECT_REQUEST,
                        "Received response code=%d from BNG %s", responseCode, config.serverAddress());
                return new Packet(responseCode, Collections.emptyList());
            }
        })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Serializes RADIUS attributes to wire-format bytes (Type-Length-Value) for the
     * four standard attributes used in Disconnect-Request packets.
     *
     * Attribute wire format: [Type:1][Length:1][Value:Length-2]
     */
    private byte[] serializeDisconnectAttributes(List<Attribute<?>> attributes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Attribute<?> attr : attributes) {
            if (attr instanceof UserName un) {
                byte[] value = un.getData().getValue().getBytes(UTF_8);
                out.write(1);                  // Type: User-Name
                out.write(value.length + 2);   // Length
                out.write(value);
            } else if (attr instanceof NasIpAddress nip) {
                byte[] value = ((Inet4Address) nip.getData().getValue()).getAddress();
                out.write(4);                  // Type: NAS-IP-Address
                out.write(6);                  // Length always 6 for IPv4
                out.write(value);
            } else if (attr instanceof FramedIpAddress fip) {
                byte[] value = ((Inet4Address) fip.getData().getValue()).getAddress();
                out.write(8);                  // Type: Framed-IP-Address
                out.write(6);                  // Length always 6 for IPv4
                out.write(value);
            } else if (attr instanceof AcctSessionId sid) {
                byte[] value = sid.getData().getValue().getBytes(UTF_8);
                out.write(44);                 // Type: Acct-Session-Id
                out.write(value.length + 2);   // Length
                out.write(value);
            } else {
                LoggingUtil.logWarn(logger, null, "serializeDisconnectAttributes",
                        "Skipping unsupported attribute for Disconnect-Request serialization: %s",
                        attr.getClass().getSimpleName());
            }
        }
        return out.toByteArray();
    }


    private RadiusClient createRadiusClient(RadiusConfig radiusConfig) {
        return UdpRadiusClient.newBuilder()
                .secret(radiusConfig.sharedSecret().getBytes(UTF_8))
                .address(new InetSocketAddress(radiusConfig.serverAddress(), radiusConfig.port()))
                .build();
    }

    private Uni<Packet> sendCoaRequest(RadiusClient radiusClient, List<Attribute<?>> attributes, int code) {
        return Uni.createFrom().item(() -> {
                    LoggingUtil.logDebug(logger, null, SEND_COA_REQUEST, "Processing for code: %s", code);
                    return new Packet(code, attributes);
                })
                .map(Unchecked.function(coaRequest -> {
                    LoggingUtil.logDebug(logger, null, SEND_COA_REQUEST, "Sending packet with %s attributes", attributes.size());
                    try {
                        return radiusClient.send(coaRequest);
                    } catch (RadiusClientException e) {
                        LoggingUtil.logError(logger, null, SEND_COA_REQUEST, e, "RADIUS client error while sending packet");
                        throw new RadiusClientServiceException(
                                "Failed to send RADIUS packet",
                                ResponseCodeEnum.RADIUS_CLIENT_SEND_FAILURE,
                                e
                        );
                    }
                }))
                .onFailure()
                .invoke(e -> LoggingUtil.logError(logger, null, SEND_COA_REQUEST, e,
                        "Failed to send COA request: %s", e.getMessage()))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<CoADisconnectResponse> processResponse(Packet responsePacket) {
        return Uni.createFrom().item(() -> {
            if (responsePacket == null) {
                logger.warn("Received null response packet");
                return new CoADisconnectResponse(
                        "FAILED",
                        null,
                        "No response received from RADIUS server"
                );
            }

            int responseCode = responsePacket.getCode();
            LoggingUtil.logInfo(logger, null, PROCESS_RESPONSE, "Received RADIUS response with code: %d", responseCode);

            return switch (responseCode) {
                case COA_ACK -> {
                    LoggingUtil.logDebug(logger, null, PROCESS_RESPONSE, "COA request acknowledged successfully");
                    yield new CoADisconnectResponse(
                            "ACK",
                            null,
                            "COA request acknowledged successfully"
                    );
                }
                case COA_NAK -> {
                    LoggingUtil.logWarn(logger, null, PROCESS_RESPONSE, "COA request rejected by RADIUS server");
                    yield new CoADisconnectResponse(
                            "NAK",
                            null,
                            "COA request rejected by RADIUS server"
                    );
                }
                default -> {
                    LoggingUtil.logWarn(logger, null, PROCESS_RESPONSE, "Unexpected response code: %d", responseCode);
                    yield new CoADisconnectResponse(
                            "FAILED",
                            null,
                            "Unexpected response code: " + responseCode
                    );
                }
            };
        });
    }
}
