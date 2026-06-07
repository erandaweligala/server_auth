package com.csg.airtel.aaa4j.application.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for WebClient HTTP/2 connection pool settings.
 * Uses h2c (HTTP/2 over cleartext) with Linkerd L7 load balancing
 * for even request distribution across pods.
 */
@ConfigMapping(prefix = "webclient")
public interface WebClientConfig {

    /**
     * Connection timeout in milliseconds
     */
    @WithDefault("5000")
    int connectTimeout();

    /**
     * Idle timeout in milliseconds - keep connections warm
     */
    @WithDefault("30000")
    int idleTimeout();

    /**
     * Keep connections alive for reuse
     */
    @WithDefault("true")
    boolean keepAlive();

    /**
     * HTTP/2 connection pool size.
     * With Linkerd L7 balancing, each request is distributed evenly
     * regardless of which connection it uses.
     */
    @WithDefault("100")
    int http2MaxPoolSize();

    /**
     * HTTP/2 streams per connection (multiplexing).
     * With 100 connections x 10 streams = 1000 concurrent requests capacity.
     */
    @WithDefault("10")
    int http2MultiplexingLimit();

    /**
     * HTTP/2 connection keep-alive interval in seconds
     */
    @WithDefault("30")
    int http2KeepAliveTimeout();
}
