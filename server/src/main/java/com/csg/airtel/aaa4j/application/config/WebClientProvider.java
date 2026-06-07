package com.csg.airtel.aaa4j.application.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WebClientProvider {

    private final Vertx vertx;
    private final WebClientConfig config;
    private WebClient webClient;

    @Inject
    public WebClientProvider(Vertx vertx, WebClientConfig config) {
        this.vertx = vertx;
        this.config = config;
    }

    /**
     * Initialize WebClient with HTTP/2-only connection pool settings.
     * Uses h2c (HTTP/2 over cleartext) with prior knowledge since the
     * downstream auth service runs behind Linkerd which supports HTTP/2.
     */
    @PostConstruct
    void init() {
        WebClientOptions options = new WebClientOptions()
                .setShared(true)
                .setTcpNoDelay(true)
                .setMaxWaitQueueSize(5000)
                // Force HTTP/2 only - no HTTP/1.1 fallback
                .setProtocolVersion(HttpVersion.HTTP_2)
                // Use direct h2c (prior knowledge) instead of HTTP/1.1 upgrade
                .setHttp2ClearTextUpgrade(false)
                // Connection timeout
                .setConnectTimeout(config.connectTimeout())
                // Idle timeout - keep connections warm
                .setIdleTimeout(config.idleTimeout())
                // Keep connections alive for reuse
                .setKeepAlive(config.keepAlive())
                // HTTP/2 connection pool
                .setHttp2MaxPoolSize(config.http2MaxPoolSize())
                // HTTP/2 streams per connection (multiplexing)
                .setHttp2MultiplexingLimit(config.http2MultiplexingLimit())
                // Connection keep-alive interval
                .setHttp2KeepAliveTimeout(config.http2KeepAliveTimeout());

        this.webClient = WebClient.create(vertx, options);
    }

    public WebClient getClient() {
        return webClient;
    }
}
