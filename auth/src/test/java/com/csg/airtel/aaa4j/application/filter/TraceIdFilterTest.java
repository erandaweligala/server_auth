package com.csg.airtel.aaa4j.application.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private ContainerResponseContext responseContext;

    private TraceIdFilter filter;
    private MultivaluedMap<String, Object> responseHeaders;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new TraceIdFilter();
        responseHeaders = new MultivaluedHashMap<>();
        when(responseContext.getHeaders()).thenReturn(responseHeaders);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testFilterRequestWithExistingTraceId() throws IOException {
        String existingTraceId = "existing-trace-id";
        when(requestContext.getHeaderString("Trace-Id")).thenReturn(existingTraceId);
        when(requestContext.getHeaderString("User-Name")).thenReturn(null);

        filter.filter(requestContext);

        assertEquals(existingTraceId, MDC.get("traceId"));
    }

    @Test
    void testFilterRequestWithoutTraceId() throws IOException {
        when(requestContext.getHeaderString("Trace-Id")).thenReturn(null);
        when(requestContext.getHeaderString("User-Name")).thenReturn(null);

        filter.filter(requestContext);

        String traceId = MDC.get("traceId").toString();
        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
        // Should be a valid UUID format
        assertTrue(traceId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    void testFilterRequestWithBlankTraceId() throws IOException {
        when(requestContext.getHeaderString("Trace-Id")).thenReturn("   ");
        when(requestContext.getHeaderString("User-Name")).thenReturn(null);

        filter.filter(requestContext);

        String traceId = MDC.get("traceId").toString();
        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
    }

    @Test
    void testFilterRequestWithUserName() throws IOException {
        String userName = "testuser";
        when(requestContext.getHeaderString("Trace-Id")).thenReturn("trace-123");
        when(requestContext.getHeaderString("User-Name")).thenReturn(userName);

        filter.filter(requestContext);

        assertEquals("trace-123", MDC.get("traceId"));
        assertEquals(userName, MDC.get("userName"));
    }

    @Test
    void testFilterRequestWithBlankUserName() throws IOException {
        when(requestContext.getHeaderString("Trace-Id")).thenReturn("trace-123");
        when(requestContext.getHeaderString("User-Name")).thenReturn("   ");

        filter.filter(requestContext);

        assertEquals("trace-123", MDC.get("traceId"));
        assertNull(MDC.get("userName"));
    }

    @Test
    void testFilterResponse() throws IOException {
        // Setup MDC
        MDC.put("traceId", "test-trace-id");
        MDC.put("userName", "testuser");

        filter.filter(requestContext, responseContext);

        assertEquals("test-trace-id", responseHeaders.getFirst("Trace-Id"));
        assertEquals("testuser", responseHeaders.getFirst("User-Name"));
        
        // Verify MDC is cleared
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("userName"));
    }


    @Test
    void testCompleteRequestResponseFlow() throws IOException {
        // Request phase
        when(requestContext.getHeaderString("Trace-Id")).thenReturn("incoming-trace");
        when(requestContext.getHeaderString("User-Name")).thenReturn("flowuser");

        filter.filter(requestContext);

        assertEquals("incoming-trace", MDC.get("traceId"));
        assertEquals("flowuser", MDC.get("userName"));

        // Response phase
        filter.filter(requestContext, responseContext);

        assertEquals("incoming-trace", responseHeaders.getFirst("Trace-Id"));
        assertEquals("flowuser", responseHeaders.getFirst("User-Name"));
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("userName"));
    }
}
