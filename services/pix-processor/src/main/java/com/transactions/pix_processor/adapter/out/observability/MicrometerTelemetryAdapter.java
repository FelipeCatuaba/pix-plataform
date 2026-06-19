package com.transactions.pix_processor.adapter.out.observability;

import com.transactions.pix_processor.domain.port.out.TelemetryPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MicrometerTelemetryAdapter implements TelemetryPort {

    private final MeterRegistry meterRegistry;

    public MicrometerTelemetryAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void increment(String metricName) {
        meterRegistry.counter(metricName).increment();
    }

    @Override
    public void recordDuration(String metricName, Duration duration, String outcome) {
        DistributionSummary.builder(metricName)
                .description(descriptionFor(metricName))
                .baseUnit("seconds")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(0, duration.toNanos() / 1_000_000_000.0));
    }

    private String descriptionFor(String metricName) {
        return switch (metricName) {
            case "pix.transaction.end_to_end.duration.seconds" ->
                    "Tempo da entrada HTTP ate a conclusao e acknowledgment Kafka";
            case "pix.processor.duration.seconds" ->
                    "Tempo do consumo Kafka ate a conclusao e acknowledgment";
            default -> "Duracao de uma etapa do processamento PIX";
        };
    }
}
