package com.transactions.pix_processor.adapter.out.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerTelemetryAdapterTest {

    @Test
    void shouldRecordCountersAndAllDurationDescriptions() {
        // arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTelemetryAdapter adapter = new MicrometerTelemetryAdapter(registry);

        // act
        adapter.increment("pix.processor.consumed");
        adapter.recordDuration("pix.transaction.end_to_end.duration.seconds", Duration.ofSeconds(2), "completed");
        adapter.recordDuration("pix.processor.duration.seconds", Duration.ofSeconds(-1), "failed");
        adapter.recordDuration("pix.custom.duration.seconds", Duration.ofMillis(500), "completed");

        // assert
        assertEquals(1.0, registry.counter("pix.processor.consumed").count());
        assertEquals(
                2.0,
                registry.get("pix.transaction.end_to_end.duration.seconds")
                        .tag("outcome", "completed").summary().totalAmount()
        );
        assertEquals(
                0.0,
                registry.get("pix.processor.duration.seconds").tag("outcome", "failed").summary().totalAmount()
        );
        assertEquals(
                0.5,
                registry.get("pix.custom.duration.seconds").tag("outcome", "completed").summary().totalAmount()
        );
    }
}
