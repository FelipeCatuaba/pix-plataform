package com.transactions.pix.adapter.out.observability;

import com.transactions.pix.domain.port.out.TelemetryPort;
import io.micrometer.core.instrument.MeterRegistry;
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
}
