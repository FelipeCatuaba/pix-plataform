package com.transactions.pix_processor.adapter.in.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.transactions.pix_processor.domain.port.in.ProcessPixResult;
import com.transactions.pix_processor.domain.port.in.ProcessPixUseCase;
import com.transactions.pix_processor.domain.port.out.TelemetryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class PixRequestedKafkaListenerTest {

    @Mock
    private ProcessPixUseCase processPixUseCase;

    @Mock
    private TelemetryPort telemetryPort;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void shouldRecordDurationsAfterAcknowledgment() {
        when(processPixUseCase.process("payload")).thenReturn(Optional.of(new ProcessPixResult(
                "tx-1",
                Instant.now().minusSeconds(2),
                "completed"
        )));

        PixRequestedKafkaListener listener = new PixRequestedKafkaListener(processPixUseCase, telemetryPort);
        listener.consume(new ConsumerRecord<>("pix.requested", 0, 0, "tx-1", "payload"), acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(telemetryPort).recordDuration(
                eq("pix.transaction.end_to_end.duration.seconds"),
                any(Duration.class),
                eq("completed")
        );
        verify(telemetryPort).recordDuration(
                eq("pix.processor.duration.seconds"),
                any(Duration.class),
                eq("completed")
        );
    }

    @Test
    void shouldAcknowledgeWithoutMetricsWhenProcessingIsIgnored() {
        // arrange
        when(processPixUseCase.process("duplicate")).thenReturn(Optional.empty());
        PixRequestedKafkaListener listener = new PixRequestedKafkaListener(processPixUseCase, telemetryPort);

        // act
        listener.consume(
                new ConsumerRecord<>("pix.requested", 0, 1, "tx-2", "duplicate"),
                acknowledgment
        );

        // assert
        verify(acknowledgment).acknowledge();
        verify(telemetryPort, never()).recordDuration(any(), any(), any());
    }
}
