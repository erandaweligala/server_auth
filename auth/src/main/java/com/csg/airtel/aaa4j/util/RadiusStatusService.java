package com.csg.airtel.aaa4j.util;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RadiusStatusService {

    // This method should return the current number of active sessions
    public int getActiveSessionCount() {
        // Logic for get session count
        return 5;
    }
}