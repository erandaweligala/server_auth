package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import io.smallrye.mutiny.Uni;
import org.aaa4j.radius.core.attribute.Attribute;
import org.aaa4j.radius.core.attribute.attributes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResponseHandlerTest {

    @Mock
    private RadiusClientService radiusClientService;

    @InjectMocks
    private ResponseHandler responseHandler;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Inject @ConfigProperty manually
        setField("serverAddress", "127.0.0.1");
        setField("coaPort", 3799);
        setField("accountingPort", 1813);
        setField("sharedSecret", "secret123");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = ResponseHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(responseHandler, value);
    }


    @Test
    void buildsValidAttributes() throws Exception {
        Map<String, String> qos = new HashMap<>();
        qos.put("username", "alice");
        qos.put("sessionId", "SID123");
        qos.put("nasIP", "192.168.1.1");
        qos.put("framedIP", "10.0.0.55");

        List<Attribute<?>> attrs = invokeBuildAttributes(qos);

        assertEquals(4, attrs.size());
        assertTrue(attrs.stream().anyMatch(a -> a instanceof UserName));
        assertTrue(attrs.stream().anyMatch(a -> a instanceof AcctSessionId));
        assertTrue(attrs.stream().anyMatch(a -> a instanceof NasIpAddress));
        assertTrue(attrs.stream().anyMatch(a -> a instanceof FramedIpAddress));
    }


    // TEST: Invalid IP should be skipped

    @Test
    void invalidIpIsIgnored() throws Exception {
        Map<String, String> qos = Map.of("nasIP", "999.999.999.999");

        List<Attribute<?>> attrs = invokeBuildAttributes(qos);

        assertEquals(0, attrs.size());
    }

    // Helper to call private buildAttributes()
    @SuppressWarnings("unchecked")
    private List<Attribute<?>> invokeBuildAttributes(Map<String, String> qos) throws Exception {
        var method = ResponseHandler.class.getDeclaredMethod("buildAttributes", Map.class);
        method.setAccessible(true);
        return (List<Attribute<?>>) method.invoke(responseHandler, qos);
    }


    @Test
    void onFailureInvokeBranch_isCovered() {
        // Simulate failure by mocking radiusClientService to throw exception
        AccountingResponseEvent event = mock(AccountingResponseEvent.class);
        when(event.eventType()).thenReturn(AccountingResponseEvent.EventType.CONTINUE);
        when(event.sessionId()).thenReturn("S1");
        when(event.message()).thenReturn("OK");


        when(radiusClientService.initiateCoA(anyList(), anyInt(), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Test exception")));

        // Should not throw, logs handled internally
        responseHandler.processAccountingResponse(event)
                .subscribe().with(
                        unused -> fail("Should not succeed"),
                        ex -> assertEquals("Test exception", ex.getMessage())
                );
    }


    @Test
    void buildAttributes_handlesUnknownKeys() throws Exception {
        Map<String, String> qos = Map.of(
                "randomKey", "value"
        );

        var method = ResponseHandler.class.getDeclaredMethod("buildAttributes", Map.class);
        method.setAccessible(true);
        List<?> attrs = (List<?>) method.invoke(responseHandler, qos);

        assertTrue(attrs.isEmpty()); // unknown key ignored
    }

    @Test
    void parseIpAddress_invalidIps() throws Exception {
        var method = ResponseHandler.class.getDeclaredMethod("parseIpAddress", String.class);
        method.setAccessible(true);

        // IPv6 address
        Optional<?> result = (Optional<?>) method.invoke(responseHandler, "abcd::1234");
        assertTrue(result.isEmpty());

        // Invalid IPv4
        result = (Optional<?>) method.invoke(responseHandler, "999.999.999.999");
        assertTrue(result.isEmpty());
    }







}
