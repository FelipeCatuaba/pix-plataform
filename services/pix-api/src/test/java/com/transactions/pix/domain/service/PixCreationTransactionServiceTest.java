package com.transactions.pix.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import com.transactions.pix.domain.port.in.CreatePixCommand;
import com.transactions.pix.domain.port.out.OutboxEventPort;
import com.transactions.pix.domain.port.out.PixTransactionPort;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PixCreationTransactionServiceTest {

    @Mock
    private PixTransactionPort transactionPort;

    @Mock
    private OutboxEventPort outboxEventPort;

    @Test
    void shouldPersistTransactionAndOutboxEvent() {
        PixTransaction saved = transaction("tx-atomic");
        when(transactionPort.insert(any())).thenReturn(saved);

        PixTransaction result = new PixCreationTransactionService(
                transactionPort,
                outboxEventPort,
                new ObjectMapper().findAndRegisterModules()
        ).create(command("tx-atomic"));

        assertThat(result).isEqualTo(saved);
        verify(transactionPort).insert(any());
        verify(outboxEventPort).append(eq("tx-atomic"), eq("PixRequested"), any(), eq(saved.createdAt()));
    }

    @Test
    void shouldWrapPayloadSerializationFailure() throws Exception {
        // arrange
        PixTransaction saved = transaction("tx-broken");
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(transactionPort.insert(any())).thenReturn(saved);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") { });
        PixCreationTransactionService service =
                new PixCreationTransactionService(transactionPort, outboxEventPort, objectMapper);

        // act + assert
        assertThatThrownBy(() -> service.create(command("tx-broken")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize PixRequested event");
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
