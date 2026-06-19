package com.transactions.pix_processor.domain.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix_processor.domain.port.out.DlqPublisherPort;
import com.transactions.pix_processor.domain.port.out.PartnerPixPort;
import com.transactions.pix_processor.domain.port.out.PixTransactionPort;
import com.transactions.pix_processor.domain.port.out.ProcessingAttemptPort;
import com.transactions.pix_processor.domain.port.out.StatusCacheInvalidationPort;
import com.transactions.pix_processor.domain.port.out.TelemetryPort;
import com.transactions.pix_processor.domain.model.PartnerPixException;
import com.transactions.pix_processor.domain.model.PartnerPixResponse;
import com.transactions.pix_processor.domain.model.PixRequestedEvent;
import com.transactions.pix_processor.domain.model.PixTransaction;
import com.transactions.pix_processor.domain.model.PixTransactionStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PixProcessingServiceTest {

    @Mock
    private PixTransactionPort transactionPort;

    @Mock
    private ProcessingAttemptPort attemptPort;

    @Mock
    private PartnerPixPort partnerPixPort;

    @Mock
    private DlqPublisherPort dlqPublisherPort;

    @Mock
    private StatusCacheInvalidationPort statusCacheInvalidationPort;

    @Mock
    private TelemetryPort telemetryPort;

    @Test
    void shouldCompleteProcessableTransaction() throws Exception {
        PixRequestedEvent event = event("tx-1");
        when(transactionPort.findByTransactionId("tx-1")).thenReturn(Optional.of(transaction("tx-1", PixTransactionStatus.PROCESSING)));
        when(attemptPort.nextAttemptNumber("tx-1")).thenReturn(1);
        when(partnerPixPort.send(event)).thenReturn(new PartnerPixResponse("partner-123", "APPROVED"));

        service(3).process(payload(event));

        verify(transactionPort).markCompleted(eq("tx-1"), eq("partner-123"), any());
        verify(attemptPort).insert("tx-1", 1, "COMPLETED", null, null);
        verify(statusCacheInvalidationPort).invalidate("tx-1");
        verify(dlqPublisherPort, never()).publish(any());
    }

    @Test
    void shouldRetryPartnerFailureAndCompleteBeforeAttemptsAreExhausted() throws Exception {
        PixRequestedEvent event = event("tx-2");
        when(transactionPort.findByTransactionId("tx-2")).thenReturn(Optional.of(transaction("tx-2", PixTransactionStatus.PROCESSING)));
        when(attemptPort.nextAttemptNumber("tx-2")).thenReturn(1);
        when(partnerPixPort.send(event))
                .thenThrow(new PartnerPixException("PARTNER_HTTP_500", "Partner failed"))
                .thenThrow(new PartnerPixException("PARTNER_HTTP_500", "Partner failed"))
                .thenReturn(new PartnerPixResponse("partner-456", "APPROVED"));

        service(3).process(payload(event));

        verify(transactionPort, org.mockito.Mockito.times(2)).markRetrying(eq("tx-2"), any(), any());
        verify(transactionPort).markCompleted(eq("tx-2"), eq("partner-456"), any());
        verify(transactionPort, never()).markFailed(eq("tx-2"), any(), any());
        verify(attemptPort).insert("tx-2", 1, "RETRYING", "PARTNER_HTTP_500", "Partner failed");
        verify(attemptPort).insert("tx-2", 2, "RETRYING", "PARTNER_HTTP_500", "Partner failed");
        verify(attemptPort).insert("tx-2", 3, "COMPLETED", null, null);
        verify(partnerPixPort, org.mockito.Mockito.times(3)).send(event);
        verify(dlqPublisherPort, never()).publish(any(DlqMessage.class));
    }

    @Test
    void shouldMarkFailedAndPublishDlqOnlyWhenAttemptsAreExhausted() throws Exception {
        PixRequestedEvent event = event("tx-3");
        when(transactionPort.findByTransactionId("tx-3")).thenReturn(Optional.of(transaction("tx-3", PixTransactionStatus.PROCESSING)));
        when(attemptPort.nextAttemptNumber("tx-3")).thenReturn(1);
        when(partnerPixPort.send(event)).thenThrow(new PartnerPixException("PARTNER_HTTP_500", "Partner failed"));

        service(3).process(payload(event));

        verify(transactionPort, org.mockito.Mockito.times(2)).markRetrying(eq("tx-3"), any(), any());
        verify(transactionPort).markFailed(eq("tx-3"), any(), any());
        verify(attemptPort).insert("tx-3", 1, "RETRYING", "PARTNER_HTTP_500", "Partner failed");
        verify(attemptPort).insert("tx-3", 2, "RETRYING", "PARTNER_HTTP_500", "Partner failed");
        verify(attemptPort).insert("tx-3", 3, "FAILED", "PARTNER_HTTP_500", "Partner failed");
        verify(statusCacheInvalidationPort, org.mockito.Mockito.times(3)).invalidate("tx-3");
        verify(partnerPixPort, org.mockito.Mockito.times(3)).send(event);
        verify(dlqPublisherPort).publish(any(DlqMessage.class));
    }

    @Test
    void shouldBlockProcessingWhenCircuitBreakerIsOpen() throws Exception {
        PixRequestedEvent event = event("tx-5");
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("partnerPix");
        circuitBreaker.transitionToOpenState();
        when(transactionPort.findByTransactionId("tx-5")).thenReturn(Optional.of(transaction("tx-5", PixTransactionStatus.PROCESSING)));
        when(attemptPort.nextAttemptNumber("tx-5")).thenReturn(1);
        when(partnerPixPort.send(event)).thenThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));

        service(3).process(payload(event));

        verify(partnerPixPort).send(event);
        verify(transactionPort).markFailed(eq("tx-5"), any(), any());
        verify(attemptPort).insert(eq("tx-5"), eq(1), eq("FAILED"), eq("PARTNER_CIRCUIT_BREAKER_OPEN"), any());
        verify(dlqPublisherPort).publish(any(DlqMessage.class));
    }

    @Test
    void shouldIgnoreDuplicatedKafkaMessageWhenTransactionIsAlreadyCompleted() throws Exception {
        PixRequestedEvent event = event("tx-4");
        when(transactionPort.findByTransactionId("tx-4")).thenReturn(Optional.of(transaction("tx-4", PixTransactionStatus.COMPLETED)));

        service(3).process(payload(event));

        verify(partnerPixPort, never()).send(any());
        verify(transactionPort, never()).markRetrying(any(), any(), any());
        verify(transactionPort, never()).markFailed(any(), any(), any());
        verify(attemptPort, never()).insert(any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void shouldRejectInvalidPayload() {
        // arrange
        String invalidPayload = "{invalid";

        // act
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(3).process(invalidPayload)
        );

        // assert
        assertEquals("Invalid PixRequested payload", error.getMessage());
    }

    @Test
    void shouldFailWhenTransactionDoesNotExistAndAcceptMissingCorrelationId() throws Exception {
        // arrange
        PixRequestedEvent event = new PixRequestedEvent(
                "tx-missing", BigDecimal.TEN, "key", "invoice", Instant.now(), null
        );
        when(transactionPort.findByTransactionId("tx-missing")).thenReturn(Optional.empty());

        // act
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(3).process(payload(event))
        );

        // assert
        assertEquals("Transaction not found: tx-missing", error.getMessage());
    }

    private PixProcessingService service(int maxAttempts) {
        return new PixProcessingService(
                objectMapper(),
                transactionPort,
                attemptPort,
                partnerPixPort,
                dlqPublisherPort,
                statusCacheInvalidationPort,
                telemetryPort,
                maxAttempts
        );
    }

    private String payload(PixRequestedEvent event) throws Exception {
        return objectMapper().writeValueAsString(event);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private PixRequestedEvent event(String transactionId) {
        return new PixRequestedEvent(
                transactionId,
                BigDecimal.valueOf(150.75),
                "cliente@email.com",
                "Pagamento",
                Instant.now(),
                "corr-123"
        );
    }

    private PixTransaction transaction(String transactionId, PixTransactionStatus status) {
        Instant now = Instant.now();
        return new PixTransaction(
                1L,
                transactionId,
                BigDecimal.valueOf(150.75),
                "cliente@email.com",
                "Pagamento",
                status,
                null,
                null,
                now,
                now
        );
    }
}
