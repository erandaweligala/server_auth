package com.csg.airtel.aaa4j.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

@ApplicationScoped
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    @Inject
    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Counter createCounter(String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(meterRegistry);
    }

    public Timer createTimer(String name, String description) {
        return Timer.builder(name)
                .description(description)
                // Publish a latency histogram with explicit SLO boundaries so the
                // p98<10ms / p100<20ms targets can be verified directly from metrics
                // (a plain Timer only exposes count/sum/max, never percentiles).
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(10),
                        Duration.ofMillis(20))
                .publishPercentiles(0.5, 0.95, 0.98, 0.99, 1.0)
                .register(meterRegistry);
    }
}
