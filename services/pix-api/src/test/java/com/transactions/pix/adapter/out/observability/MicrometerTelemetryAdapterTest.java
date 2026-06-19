package com.transactions.pix.adapter.out.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerTelemetryAdapterTest {

    @Test
    void shouldIncrementNamedCounter() {
        // arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTelemetryAdapter adapter = new MicrometerTelemetryAdapter(registry);

        // act
        adapter.increment("pix.api.requests");

        // assert
        assertEquals(1.0, registry.counter("pix.api.requests").count());
    }
}
