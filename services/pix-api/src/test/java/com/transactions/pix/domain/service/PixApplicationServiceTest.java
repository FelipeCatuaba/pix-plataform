package com.transactions.pix.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactions.pix.domain.port.in.CreatePixCommand;
import com.transactions.pix.domain.port.out.IdempotencyCachePort;
import com.transactions.pix.domain.port.out.PixTransactionPort;
import com.transactions.pix.domain.port.out.StatusCachePort;
import com.transactions.pix.domain.port.out.TelemetryPort;
import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PixApplicationServiceTest {

    @Mock
    private PixTransactionPort transactionPort;

    @Mock
    private PixCreationTransactionService creationTransactionService;

    @Mock
    private IdempotencyCachePort idempotencyCachePort;

    @Mock
    private StatusCachePort statusCachePort;

    @Mock
    private TelemetryPort telemetryPort;

    @Test
    void shouldReturnExistingTransactionWithoutCreatingOutboxEvent() {
        PixTransaction existing = transaction("tx-1");
        when(transactionPort.findByTransactionId("tx-1")).thenReturn(Optional.of(existing));

        PixApplicationService service = service();

        PixTransaction result = service.create(command("tx-1"));

        assertThat(result).isEqualTo(existing);
        verify(creationTransactionService, never()).create(any());
    }

    @Test
    void shouldCreateTransactionAndOutboxEvent() {
        PixTransaction saved = transaction("tx-2");
        when(transactionPort.findByTransactionId("tx-2")).thenReturn(Optional.empty());
        when(creationTransactionService.create(any())).thenReturn(saved);

        PixApplicationService service = service();

        PixTransaction result = service.create(command("tx-2"));

        assertThat(result.transactionId()).isEqualTo("tx-2");
        verify(creationTransactionService).create(any());
        verify(idempotencyCachePort).cacheStatus(eq("tx-2"), eq(PixTransactionStatus.PROCESSING), any());
    }

    @Test
    void shouldLoadTransactionAfterConcurrentInsert() {
        PixTransaction existing = transaction("tx-3");
        when(transactionPort.findByTransactionId("tx-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(creationTransactionService.create(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate transactionId"));

        PixTransaction result = service().create(command("tx-3"));

        assertThat(result).isEqualTo(existing);
        verify(idempotencyCachePort).cacheStatus(eq("tx-3"), eq(PixTransactionStatus.PROCESSING), any());
        verify(telemetryPort, never()).increment("pix.transactions.created");
    }

    private PixApplicationService service() {
        return new PixApplicationService(
                transactionPort,
                creationTransactionService,
                idempotencyCachePort,
                statusCachePort,
                telemetryPort,
                Duration.ofHours(24),
                Duration.ofSeconds(30)
        );
    }

    private CreatePixCommand command(String transactionId) {
        return new CreatePixCommand(
                transactionId,
                BigDecimal.valueOf(150.75),
                "cliente@email.com",
                "Pagamento",
                "corr-123",
                Instant.now()
        );
    }

    private PixTransaction transaction(String transactionId) {
        Instant now = Instant.now();
        return new PixTransaction(
                1L,
                transactionId,
                BigDecimal.valueOf(150.75),
                "cliente@email.com",
                "Pagamento",
                PixTransactionStatus.PROCESSING,
                null,
                null,
                now,
                now
        );
    }
}
