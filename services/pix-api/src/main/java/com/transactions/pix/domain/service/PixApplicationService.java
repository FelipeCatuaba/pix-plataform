package com.transactions.pix.domain.service;

import com.transactions.pix.adapter.in.web.ResourceNotFoundException;
import com.transactions.pix.domain.port.in.CreatePixCommand;
import com.transactions.pix.domain.port.in.PixUseCase;
import com.transactions.pix.domain.port.out.IdempotencyCachePort;
import com.transactions.pix.domain.port.out.PixTransactionPort;
import com.transactions.pix.domain.port.out.StatusCachePort;
import com.transactions.pix.domain.port.out.TelemetryPort;
import com.transactions.pix.domain.model.PixTransaction;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PixApplicationService implements PixUseCase {

    private final PixTransactionPort transactionPort;
    private final PixCreationTransactionService creationTransactionService;
    private final IdempotencyCachePort idempotencyCachePort;
    private final StatusCachePort statusCachePort;
    private final TelemetryPort telemetryPort;
    private final Duration idempotencyTtl;
    private final Duration statusCacheTtl;

    public PixApplicationService(
            PixTransactionPort transactionPort,
            PixCreationTransactionService creationTransactionService,
            IdempotencyCachePort idempotencyCachePort,
            StatusCachePort statusCachePort,
            TelemetryPort telemetryPort,
            @Value("${pix.idempotency.ttl:24h}") Duration idempotencyTtl,
            @Value("${pix.status-cache.ttl:30s}") Duration statusCacheTtl
    ) {
        this.transactionPort = transactionPort;
        this.creationTransactionService = creationTransactionService;
        this.idempotencyCachePort = idempotencyCachePort;
        this.statusCachePort = statusCachePort;
        this.telemetryPort = telemetryPort;
        this.idempotencyTtl = idempotencyTtl;
        this.statusCacheTtl = statusCacheTtl;
    }

    @Override
    public PixTransaction create(CreatePixCommand command) {
        telemetryPort.increment("pix.api.requests");

        if (idempotencyCachePort.findStatus(command.transactionId()).isPresent()) {
            return findByTransactionId(command.transactionId());
        }

        return transactionPort.findByTransactionId(command.transactionId())
                .orElseGet(() -> createOrLoadAfterConcurrentInsert(command));
    }

    @Override
    public PixTransaction findByTransactionId(String transactionId) {
        return statusCachePort.findByTransactionId(transactionId)
                .or(() -> transactionPort.findByTransactionId(transactionId)
                        .map(transaction -> {
                            statusCachePort.cache(transaction, statusCacheTtl);
                            return transaction;
                        }))
                .orElseThrow(() -> new ResourceNotFoundException("PIX transaction not found: " + transactionId));
    }

    private PixTransaction createOrLoadAfterConcurrentInsert(CreatePixCommand command) {
        PixTransaction result;
        boolean created = false;
        try {
            result = creationTransactionService.create(command);
            created = true;
        } catch (DataIntegrityViolationException e) {
            result = transactionPort.findByTransactionId(command.transactionId())
                    .orElseThrow(() -> e);
        }

        idempotencyCachePort.cacheStatus(result.transactionId(), result.status(), idempotencyTtl);
        statusCachePort.cache(result, statusCacheTtl);
        if (created) {
            telemetryPort.increment("pix.transactions.created");
        }
        return result;
    }
}
