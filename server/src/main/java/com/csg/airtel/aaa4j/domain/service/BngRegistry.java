package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.application.config.RadiusServerConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized registry for BNG (Broadband Network Gateway) configurations.
 * Provides O(1) lookup of per-BNG shared secrets based on source IP address.
 * Falls back to the default shared secret when a BNG is not explicitly configured.
 */
@ApplicationScoped
public class BngRegistry {

    private static final Logger logger = Logger.getLogger(BngRegistry.class);

    private final RadiusServerConfig config;

    /** Key: BNG IP address string, Value: BngConfig */
    private final ConcurrentHashMap<String, RadiusServerConfig.BngConfig> bngsByAddress = new ConcurrentHashMap<>();

    /** Key: BNG id, Value: BngConfig */
    private final ConcurrentHashMap<String, RadiusServerConfig.BngConfig> bngsById = new ConcurrentHashMap<>();

    @Inject
    public BngRegistry(RadiusServerConfig config) {
        this.config = config;
    }

    @PostConstruct
    void init() {
        Map<String, RadiusServerConfig.BngConfig> bngs = config.bngs();

        if (bngs.isEmpty()) {
            logger.info("No BNGs configured - using default shared secret for all clients");
            return;
        }
        logger.infof("Loading %d BNG configuration(s)", bngs.size());

        int active = 0;
        for (RadiusServerConfig.BngConfig bng : bngs.values()) {
            if (!bng.enabled()) {
                logger.infof("Skipping disabled BNG: id=%s, address=%s", bng.id(), bng.address());
                continue;
            }
            bngsByAddress.put(bng.address(), bng);
            bngsById.put(bng.id(), bng);
            logger.infof("Registered BNG: id=%s, address=%s, vendor=%s, location=%s",
                    bng.id(),
                    bng.address(),
                    bng.vendor().orElse("unknown"),
                    bng.location().orElse("unknown"));
            active++;
        }

        logger.infof("BNG registry initialized with %d active BNG(s)", active);
    }

    /**
     * Returns the RADIUS shared secret bytes for the given client IP address.
     * Uses per-BNG secret if configured; otherwise falls back to the default.
     */
    public byte[] getSharedSecretBytes(InetAddress clientAddress) {
        return getSharedSecretBytes(clientAddress.getHostAddress());
    }

    /**
     * Returns the RADIUS shared secret bytes for the given IP address string.
     * Uses per-BNG secret if configured; otherwise falls back to the default.
     */
    public byte[] getSharedSecretBytes(String ipAddress) {
        RadiusServerConfig.BngConfig bng = bngsByAddress.get(ipAddress);
        if (bng != null) {
            return bng.sharedSecret().getBytes(StandardCharsets.UTF_8);
        }
        return config.sharedSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the shared secret string for the given IP address.
     */
    public String getSharedSecret(String ipAddress) {
        RadiusServerConfig.BngConfig bng = bngsByAddress.get(ipAddress);
        return bng != null ? bng.sharedSecret() : config.sharedSecret();
    }

    public Optional<RadiusServerConfig.BngConfig> getBngByAddress(String address) {
        return Optional.ofNullable(bngsByAddress.get(address));
    }

    public Optional<RadiusServerConfig.BngConfig> getBngById(String id) {
        return Optional.ofNullable(bngsById.get(id));
    }

    /**
     * Returns the RADIUS shared secret bytes for the given client IP address,
     * enforcing an IP allowlist when BNGs are configured.
     */
    public byte[] getAllowedSharedSecretBytes(InetAddress clientAddress) {
        return getAllowedSharedSecretBytes(clientAddress.getHostAddress());
    }

    /**
     * Returns the RADIUS shared secret bytes for the given IP address string,
     * enforcing an IP allowlist when BNGs are configured.
     *
     * @return secret bytes for known/permitted IPs, or {@code null} to reject the connection
     */
    public byte[] getAllowedSharedSecretBytes(String ipAddress) {
        if (bngsByAddress.isEmpty()) {
            // No BNGs configured — accept all clients using the default secret
            return config.sharedSecret().getBytes(StandardCharsets.UTF_8);
        }
        RadiusServerConfig.BngConfig bng = bngsByAddress.get(ipAddress);
        if (bng != null) {
            return bng.sharedSecret().getBytes(StandardCharsets.UTF_8);
        }
        // BNGs are configured but this IP is not registered — reject
        logger.warnf("Rejecting connection from unknown BNG IP: %s (not in allowlist)", ipAddress);
        return null;
    }

    public boolean isBngRegistered(String address) {
        return bngsByAddress.containsKey(address);
    }

    public Map<String, RadiusServerConfig.BngConfig> getAllBngs() {
        return Collections.unmodifiableMap(bngsByAddress);
    }

    public int getBngCount() {
        return bngsByAddress.size();
    }
}
