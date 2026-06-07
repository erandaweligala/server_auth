package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.RadiusConfig;
import com.csg.airtel.aaa4j.domain.model.coa.CoADisconnectResponse;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.aaa4j.radius.client.clients.UdpRadiusClient;
import org.aaa4j.radius.core.attribute.Attribute;
import org.aaa4j.radius.core.packet.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RadiusClientServiceTest {

    @InjectMocks
    private RadiusClientService radiusClientService;

    // FIX 1: Mock the concrete class, not the interface
    @Mock
    private UdpRadiusClient mockUdpRadiusClient;

    private RadiusConfig radiusConfig;
    private List<Attribute<?>> attributes;

    @BeforeEach
    void setup() {
        radiusConfig = new RadiusConfig("127.0.0.1", 1812, "secret");
        attributes = new ArrayList<>();
    }

    @Test
    void testFullFlow_ACK() throws Exception {
        try (MockedStatic<UdpRadiusClient> mockedBuilder = mockStatic(UdpRadiusClient.class)) {
            setupMockClient(mockedBuilder);

            Packet mockResponse = new Packet(41, new ArrayList<>());
            when(mockUdpRadiusClient.send(any(Packet.class))).thenReturn(mockResponse);

            CoADisconnectResponse response = radiusClientService.initiateCoA(attributes, 40, radiusConfig)
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitItem()
                    .getItem();

            assertEquals("ACK", response.status());
        }
    }


    @Test
    void testProcessResponse_Branches() throws Exception {
        try (MockedStatic<UdpRadiusClient> mockedBuilder = mockStatic(UdpRadiusClient.class)) {
            setupMockClient(mockedBuilder);

            // Test NAK (Code 42)
            when(mockUdpRadiusClient.send(any())).thenReturn(new Packet(42, new ArrayList<>()));
            CoADisconnectResponse nakRes = radiusClientService.initiateCoA(attributes, 40, radiusConfig)
                    .subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem().getItem();
            assertEquals("NAK", nakRes.status());

            // Test Null Response
            when(mockUdpRadiusClient.send(any())).thenReturn(null);
            CoADisconnectResponse nullRes = radiusClientService.initiateCoA(attributes, 40, radiusConfig)
                    .subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem().getItem();
            assertEquals("FAILED", nullRes.status());
        }
    }

    /**
     * Helper to mock the fluent API of UdpRadiusClient.Builder
     */
    private void setupMockClient(MockedStatic<UdpRadiusClient> mockedStatic) {
        UdpRadiusClient.Builder builder = mock(UdpRadiusClient.Builder.class);

        // Ensure builder returns itself for fluent calls
        when(builder.secret(any(byte[].class))).thenReturn(builder);
        when(builder.address(any())).thenReturn(builder);

        // FIX 2: Explicitly return the mockUdpRadiusClient
        when(builder.build()).thenReturn(mockUdpRadiusClient);

        // Setup the static entry point
        mockedStatic.when(UdpRadiusClient::newBuilder).thenReturn(builder);
    }
}