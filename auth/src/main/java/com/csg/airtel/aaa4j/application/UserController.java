package com.csg.airtel.aaa4j.application;


import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;


@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserController {

    @GET
    @Path("/data/{username}")
    @CircuitBreaker(
            requestVolumeThreshold = 100,     // Minimum requests before circuit can open
            failureRatio = 0.5,               // 50% failure rate threshold
            delay = 5000,                     // 5 second delay before attempting to close
            successThreshold = 3              // Consecutive successes to close circuit
    )
    @Timeout(5000)                        // 5 second timeout
    @Retry(maxRetries = 2, delay = 100)   // Quick retries with minimal delay
    @Fallback(fallbackMethod = "fallbackResponse") // Fallback method implement on failure access Reject
    public Boolean getUserData(String userName) {
        return true;
    }
    public Boolean fallbackResponse(String userName) {
        return false;
    }
}
