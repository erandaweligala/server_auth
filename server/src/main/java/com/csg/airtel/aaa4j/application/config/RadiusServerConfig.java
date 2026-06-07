package com.csg.airtel.aaa4j.application.config;


import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;
@StaticInitSafe
@ConfigMapping(prefix = "radius")
public interface RadiusServerConfig {

    /**
     * Authentication server configuration
     */
    AuthConfig auth();

    /**
     * Accounting server configuration
     */
    AccountingConfig accounting();

    /**
     * Default shared secret (used when BNG is not explicitly configured)
     */
    @WithDefault("sharedsecret")
    String sharedSecret();

    /**
     * Whether to fail application startup if any RADIUS server fails to start
     */
    @WithDefault("true")
    boolean failOnStartupError();

    /**
     * Per-BNG configurations keyed by BNG name/id.
     * When present, each BNG is identified by its source IP and uses its own shared secret.
     */
    Map<String, BngConfig> bngs();

    interface AuthConfig {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("1812")
        int port();

        @WithDefault("0.0.0.0")
        String bindAddress();
    }

    interface AccountingConfig {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("1813")
        int port();

        @WithDefault("127.0.0.1")
        String bindAddress();
    }

    interface BngConfig {
        /** Unique identifier for this BNG */
        String id();

        /** IP address of the BNG device (used for source IP lookup) */
        String address();

        /** RADIUS shared secret for this specific BNG */
        String sharedSecret();

        /** Human-readable description */
        Optional<String> description();

        /** Whether this BNG is active */
        @WithDefault("true")
        boolean enabled();

        /** Vendor identifier (nokia, cisco, huawei, etc.) */
        Optional<String> vendor();

        /** Geographic location or region */
        Optional<String> location();

        /** Maximum concurrent sessions (0 = unlimited) */
        @WithDefault("0")
        int maxSessions();
    }
}