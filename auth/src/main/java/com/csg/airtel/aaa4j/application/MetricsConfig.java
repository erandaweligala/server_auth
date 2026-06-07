package com.csg.airtel.aaa4j.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
                .register(meterRegistry);
    }
}
