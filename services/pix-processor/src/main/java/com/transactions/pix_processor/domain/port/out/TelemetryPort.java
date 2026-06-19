package com.transactions.pix_processor.domain.port.out;

import java.time.Duration;

public interface TelemetryPort {

    void increment(String metricName);

    void recordDuration(String metricName, Duration duration, String outcome);
}
