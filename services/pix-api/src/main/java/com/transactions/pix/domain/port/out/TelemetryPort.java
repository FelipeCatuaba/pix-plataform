package com.transactions.pix.domain.port.out;

public interface TelemetryPort {

    void increment(String metricName);
}
