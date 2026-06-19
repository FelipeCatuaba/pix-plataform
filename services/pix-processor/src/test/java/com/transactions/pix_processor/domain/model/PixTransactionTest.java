package com.transactions.pix_processor.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PixTransactionTest {

    @ParameterizedTest
    @EnumSource(value = PixTransactionStatus.class, names = {"PROCESSING", "RETRYING"})
    void shouldAllowProcessableStatuses(PixTransactionStatus status) {
        // arrange
        PixTransaction transaction = transaction(status);

        // act
        boolean processable = transaction.canBeProcessed();

        // assert
        assertTrue(processable);
    }

    @ParameterizedTest
    @EnumSource(value = PixTransactionStatus.class, names = {"COMPLETED", "FAILED"})
    void shouldRejectTerminalStatuses(PixTransactionStatus status) {
        // arrange
        PixTransaction transaction = transaction(status);

        // act
        boolean processable = transaction.canBeProcessed();

        // assert
        assertFalse(processable);
    }

    private PixTransaction transaction(PixTransactionStatus status) {
        Instant now = Instant.parse("2026-06-19T12:00:00Z");
        return new PixTransaction(
                1L, "tx-1", BigDecimal.TEN, "key", "invoice",
                status, null, null, now, now
        );
    }
}
