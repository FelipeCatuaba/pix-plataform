package com.transactions.pix_processor.adapter.in.messaging;

import com.transactions.pix_processor.domain.port.in.ProcessPixUseCase;
import com.transactions.pix_processor.domain.port.in.ProcessPixResult;
import com.transactions.pix_processor.domain.port.out.TelemetryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PixRequestedKafkaListener {

    private final ProcessPixUseCase processPixUseCase;
    private final TelemetryPort telemetryPort;

    public PixRequestedKafkaListener(ProcessPixUseCase processPixUseCase, TelemetryPort telemetryPort) {
        this.processPixUseCase = processPixUseCase;
        this.telemetryPort = telemetryPort;
    }

    @KafkaListener(
            topics = "pix.requested",
            groupId = "${pix.processor.group-id:pix-processor}",
            concurrency = "${pix.processor.concurrency:3}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Instant consumedAt = Instant.now();
        Optional<ProcessPixResult> result = processPixUseCase.process(record.value());
        acknowledgment.acknowledge();
        Instant acknowledgedAt = Instant.now();

        result.ifPresent(processed -> {
            telemetryPort.recordDuration(
                    "pix.transaction.end_to_end.duration.seconds",
                    Duration.between(processed.requestStartedAt(), acknowledgedAt),
                    processed.outcome()
            );
            telemetryPort.recordDuration(
                    "pix.processor.duration.seconds",
                    Duration.between(consumedAt, acknowledgedAt),
                    processed.outcome()
            );
        });
    }
}
