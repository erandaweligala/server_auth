package com.csg.airtel.aaa4j.domain.producer;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RadiusAccountingProducerTest {

    @Mock
    private Emitter<AccountingRequestDto> emitter;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private RadiusAccountingProducer producer;
    private AutoCloseable closeable;
    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        // Mocking the meter registry to return our mock counter
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        producer = new RadiusAccountingProducer(emitter, meterRegistry);
    }

    @Test
    @DisplayName("Should successfully produce accounting event and trigger Ack")
    void testProduceAccountingEvent_Success() throws Exception {
        // Arrange
        AccountingRequestDto dto = createTestDto("session-123", "10.0.0.1");
        ArgumentCaptor<Message<AccountingRequestDto>> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // Act
        CompletionStage<Void> result = producer.produceAccountingEvent(dto);

        // Assert Emitter was called
        verify(emitter).send(messageCaptor.capture());
        Message<AccountingRequestDto> capturedMessage = messageCaptor.getValue();
        assertEquals(dto, capturedMessage.getPayload());

        // Simulate Kafka ACKing the message
        capturedMessage.ack().toCompletableFuture().get(1, TimeUnit.SECONDS);

        // Verify the stage completed successfully
        assertDoesNotThrow(() -> result.toCompletableFuture().join());
    }

    @Test
    @DisplayName("Should handle Nack from Kafka and increment failure counter")
    void testProduceAccountingEvent_Nack() {
        // Arrange
        AccountingRequestDto dto = createTestDto("session-456", "10.0.0.2");
        ArgumentCaptor<Message<AccountingRequestDto>> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // Act
        CompletionStage<Void> result = producer.produceAccountingEvent(dto);
        verify(emitter).send(messageCaptor.capture());

        // Simulate Kafka NACKing the message
        Message<AccountingRequestDto> capturedMessage = messageCaptor.getValue();
        capturedMessage.nack(new RuntimeException("Kafka Failure"));

        // Assert
        assertTrue(result.toCompletableFuture().isCompletedExceptionally());
        verify(counter, atLeastOnce()).increment();
    }


//    @Test
//    @DisplayName("fireAndForget should send message without returning a future")
//    void testFireAndForgetAccountingEvent_Success() {
//        AccountingRequestDto dto = createTestDto("session-ff", "10.0.0.3");
//        ArgumentCaptor<Message<AccountingRequestDto>> messageCaptor = ArgumentCaptor.forClass(Message.class);
//
//        producer.fireAndForgetAccountingEvent(dto);
//
//        verify(emitter).send(messageCaptor.capture());
//        assertEquals(dto, messageCaptor.getValue().getPayload());
//    }
//
//    @Test
//    @DisplayName("fireAndForget should handle emitter exception without propagating")
//    void testFireAndForgetAccountingEvent_EmitterException() {
//        AccountingRequestDto dto = createTestDto("session-ff-err", "10.0.0.4");
//        doThrow(new RuntimeException("Emitter failure")).when(emitter).send(any(Message.class));
//
//        // Should not throw - errors are caught internally
//        assertDoesNotThrow(() -> producer.fireAndForgetAccountingEvent(dto));
//        verify(counter, atLeastOnce()).increment();
//    }


    @Test
    @DisplayName("Should handle null values in partition key builder")
    void testBuildPartitionKey_NullHandling()  {
        // Testing private buildPartitionKey indirectly via produceAccountingEvent
        AccountingRequestDto dto = createTestDto(null, null);

        producer.produceAccountingEvent(dto);

        ArgumentCaptor<Message<AccountingRequestDto>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).send(messageCaptor.capture());

    }

    private AccountingRequestDto createTestDto(String sessionId, String nasIP) {
        return new AccountingRequestDto(
                "evt-123",
                sessionId,
                nasIP,
                "testUser",
                AccountingRequestDto.ActionType.START,
                100, 200, 3600,
                Instant.now(),
                "port-1",
                "192.168.1.1",
                0, 0, 0,
                "nas-id-1"
        );
    }
}