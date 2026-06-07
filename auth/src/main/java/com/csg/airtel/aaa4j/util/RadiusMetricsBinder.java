package com.csg.airtel.aaa4j.util;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RadiusMetricsBinder implements MeterBinder {

    private final RadiusStatusService radiusStatusService;  // your code to fetch active session count

    @Inject
    public RadiusMetricsBinder(RadiusStatusService radiusStatusService) {
        this.radiusStatusService = radiusStatusService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("radius_active_sessions", radiusStatusService,
                        RadiusStatusService::getActiveSessionCount)
                .description("Active RADIUS sessions reported by server")
                .register(registry);
    }
}
