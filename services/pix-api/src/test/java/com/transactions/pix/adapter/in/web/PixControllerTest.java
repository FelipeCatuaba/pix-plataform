package com.transactions.pix.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactions.pix.adapter.in.web.request.PixRequest;
import com.transactions.pix.adapter.in.web.response.PixResponse;
import com.transactions.pix.adapter.in.web.response.PixStatusResponse;
import com.transactions.pix.config.CorrelationIdFilter;
import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import com.transactions.pix.domain.port.in.PixUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class PixControllerTest {

    @Mock
    private PixUseCase pixUseCase;

    @Mock
    private HttpServletRequest servletRequest;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldCreateTransactionUsingRequestContextTimestamp() {
        // arrange
        Instant startedAt = Instant.parse("2026-06-19T12:00:00Z");
        PixRequest request = new PixRequest("tx-1", BigDecimal.TEN, "key", "invoice");
        PixTransaction transaction = transaction("tx-1", startedAt);
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");
        when(servletRequest.getAttribute(CorrelationIdFilter.REQUEST_STARTED_AT_ATTRIBUTE)).thenReturn(startedAt);
        when(pixUseCase.create(org.mockito.ArgumentMatchers.any())).thenReturn(transaction);

        // act
        PixResponse response = new PixController(pixUseCase).create(request, servletRequest);

        // assert
        assertEquals("tx-1", response.transactionId());
        verify(pixUseCase).create(argThat(command ->
                command.correlationId().equals("corr-1") && command.requestStartedAt().equals(startedAt)
        ));
    }

    @Test
    void shouldUseCurrentTimestampWhenFilterAttributeIsMissing() {
        // arrange
        Instant before = Instant.now();
        PixRequest request = new PixRequest("tx-2", BigDecimal.ONE, "key", null);
        when(servletRequest.getAttribute(CorrelationIdFilter.REQUEST_STARTED_AT_ATTRIBUTE)).thenReturn(null);
        when(pixUseCase.create(org.mockito.ArgumentMatchers.any())).thenReturn(transaction("tx-2", before));

        // act
        new PixController(pixUseCase).create(request, servletRequest);

        // assert
        verify(pixUseCase).create(argThat(command -> !command.requestStartedAt().isBefore(before)));
    }

    @Test
    void shouldReturnStatusByTransactionId() {
        // arrange
        PixTransaction transaction = transaction("tx-3", Instant.now());
        when(pixUseCase.findByTransactionId("tx-3")).thenReturn(transaction);

        // act
        PixStatusResponse response = new PixController(pixUseCase).findByTransactionId("tx-3");

        // assert
        assertEquals("tx-3", response.transactionId());
        verify(pixUseCase).findByTransactionId("tx-3");
    }

    private PixTransaction transaction(String id, Instant timestamp) {
        return new PixTransaction(
                1L, id, BigDecimal.TEN, "key", "invoice", PixTransactionStatus.PROCESSING,
                null, null, timestamp, timestamp
        );
    }
}
