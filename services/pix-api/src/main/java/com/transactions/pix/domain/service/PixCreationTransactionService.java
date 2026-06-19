package com.transactions.pix.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.port.in.CreatePixCommand;
import com.transactions.pix.domain.port.out.OutboxEventPort;
import com.transactions.pix.domain.port.out.PixTransactionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PixCreationTransactionService {

    private final PixTransactionPort transactionPort;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    public PixCreationTransactionService(
            PixTransactionPort transactionPort,
            OutboxEventPort outboxEventPort,
            ObjectMapper objectMapper
    ) {
        this.transactionPort = transactionPort;
        this.outboxEventPort = outboxEventPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PixTransaction create(CreatePixCommand command) {
        PixTransaction transaction = PixTransaction.create(
                command.transactionId(),
                command.amount(),
                command.pixKey(),
                command.description(),
                command.requestStartedAt()
        );

        PixTransaction saved = transactionPort.insert(transaction);
        outboxEventPort.append(
                saved.transactionId(),
                "PixRequested",
                toPayload(saved, command.correlationId()),
                saved.createdAt()
        );
        return saved;
    }

    private String toPayload(PixTransaction transaction, String correlationId) {
        try {
            PixRequestedEvent event = new PixRequestedEvent(
                    transaction.transactionId(),
                    transaction.amount(),
                    transaction.pixKey(),
                    transaction.description(),
                    transaction.createdAt(),
                    correlationId
            );
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize PixRequested event", e);
        }
    }
}
