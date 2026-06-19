package com.transactions.pix_processor.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix_processor.domain.port.in.ProcessPixUseCase;
import com.transactions.pix_processor.domain.port.in.ProcessPixResult;
import com.transactions.pix_processor.domain.port.out.DlqPublisherPort;
import com.transactions.pix_processor.domain.port.out.PartnerPixPort;
import com.transactions.pix_processor.domain.port.out.PixTransactionPort;
import com.transactions.pix_processor.domain.port.out.ProcessingAttemptPort;
import com.transactions.pix_processor.domain.port.out.StatusCacheInvalidationPort;
import com.transactions.pix_processor.domain.port.out.TelemetryPort;
import com.transactions.pix_processor.domain.model.PixRequestedEvent;
import com.transactions.pix_processor.domain.model.PixTransaction;
import com.transactions.pix_processor.domain.model.PartnerPixException;
import com.transactions.pix_processor.domain.model.PartnerPixResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PixProcessingService implements ProcessPixUseCase {

    private static final Logger log = LoggerFactory.getLogger(PixProcessingService.class);

    private final ObjectMapper objectMapper;
    private final PixTransactionPort transactionPort;
    private final ProcessingAttemptPort attemptPort;
    private final PartnerPixPort partnerPixPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final StatusCacheInvalidationPort statusCacheInvalidationPort;
    private final TelemetryPort telemetryPort;
    private final int maxProcessingAttempts;

    public PixProcessingService(
            ObjectMapper objectMapper,
            PixTransactionPort transactionPort,
            ProcessingAttemptPort attemptPort,
            PartnerPixPort partnerPixPort,
            DlqPublisherPort dlqPublisherPort,
            StatusCacheInvalidationPort statusCacheInvalidationPort,
            TelemetryPort telemetryPort,
            @Value("${pix.processor.max-processing-attempts:3}") int maxProcessingAttempts
    ) {
        this.objectMapper = objectMapper;
        this.transactionPort = transactionPort;
        this.attemptPort = attemptPort;
        this.partnerPixPort = partnerPixPort;
        this.dlqPublisherPort = dlqPublisherPort;
        this.statusCacheInvalidationPort = statusCacheInvalidationPort;
        this.telemetryPort = telemetryPort;
        this.maxProcessingAttempts = maxProcessingAttempts;
    }

    @Override
    @Transactional
    public Optional<ProcessPixResult> process(String payload) {
        PixRequestedEvent event = readPayload(payload);
        putMdc(event);
        telemetryPort.increment("pix.processor.consumed");

        try {
            return processEvent(event, payload);
        } finally {
            MDC.clear();
        }
    }

    private Optional<ProcessPixResult> processEvent(PixRequestedEvent event, String originalPayload) {
        PixTransaction transaction = transactionPort.findByTransactionId(event.transactionId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.transactionId()));

        if (!transaction.canBeProcessed()) {
            log.info("Consumer idempotency ignored duplicated event transactionId={} status={}",
                    transaction.transactionId(), transaction.status());
            return Optional.empty();
        }

        int firstAttempt = attemptPort.nextAttemptNumber(event.transactionId());

        for (int attempt = firstAttempt; attempt <= maxProcessingAttempts; attempt++) {
            MDC.put("attempt", Integer.toString(attempt));
            String outcome = tryProcessAttempt(event, originalPayload, attempt);
            if (outcome != null) {
                return Optional.of(new ProcessPixResult(
                        event.transactionId(),
                        event.createdAt(),
                        outcome
                ));
            }
        }
        return Optional.empty();
    }

    private String tryProcessAttempt(
            PixRequestedEvent event,
            String originalPayload,
            int attempt
    ) {
        log.info("PIX partner attempt started transactionId={} attempt={} maxAttempts={}",
                event.transactionId(), attempt, maxProcessingAttempts);
        try {
            PartnerPixResponse response = partnerPixPort.send(event);
            Instant completedAt = Instant.now();
            transactionPort.markCompleted(event.transactionId(), response.partnerReferenceId(), completedAt);
            statusCacheInvalidationPort.invalidate(event.transactionId());
            attemptPort.insert(event.transactionId(), attempt, "COMPLETED", null, null);
            telemetryPort.increment("pix.transactions.completed");
            log.info("PIX partner attempt completed transactionId={} attempt={} status=COMPLETED partnerReferenceId={}",
                    event.transactionId(), attempt, response.partnerReferenceId());
            return "completed";
        } catch (CallNotPermittedException e) {
            handleCircuitBreakerOpen(event, originalPayload, attempt, e);
            return "failed";
        } catch (PartnerPixException e) {
            handlePartnerFailure(event, originalPayload, attempt, e);
            return attempt >= maxProcessingAttempts ? "failed" : null;
        }
    }

    private void handlePartnerFailure(
            PixRequestedEvent event,
            String originalPayload,
            int attempt,
            PartnerPixException e
    ) {
        telemetryPort.increment("pix.partner.errors");
        telemetryPort.increment("pix.processor.retries");

        String reason = e.errorCode() + ": " + e.getMessage();
        boolean exhausted = attempt >= maxProcessingAttempts;

        if (exhausted) {
            Instant failedAt = Instant.now();
            attemptPort.insert(event.transactionId(), attempt, "FAILED", e.errorCode(), e.getMessage());
            transactionPort.markFailed(event.transactionId(), reason, failedAt);
            statusCacheInvalidationPort.invalidate(event.transactionId());
            telemetryPort.increment("pix.transactions.failed");
            log.warn("PIX partner attempt failed transactionId={} attempt={} maxAttempts={} status=FAILED errorCode={} errorMessage={}",
                    event.transactionId(), attempt, maxProcessingAttempts, e.errorCode(), e.getMessage());
            publishDlq(event, originalPayload, attempt, e.errorCode(), e.getMessage(), true);
        } else {
            attemptPort.insert(event.transactionId(), attempt, "RETRYING", e.errorCode(), e.getMessage());
            transactionPort.markRetrying(event.transactionId(), reason, Instant.now());
            statusCacheInvalidationPort.invalidate(event.transactionId());
            log.warn("PIX partner attempt failed transactionId={} attempt={} maxAttempts={} status=RETRYING errorCode={} errorMessage={}",
                    event.transactionId(), attempt, maxProcessingAttempts, e.errorCode(), e.getMessage());
        }
    }

    private void handleCircuitBreakerOpen(
            PixRequestedEvent event,
            String originalPayload,
            int attempt,
            CallNotPermittedException e
    ) {
        String errorCode = "PARTNER_CIRCUIT_BREAKER_OPEN";
        String errorMessage = e.getMessage();
        String reason = errorCode + ": " + errorMessage;
        Instant failedAt = Instant.now();

        attemptPort.insert(event.transactionId(), attempt, "FAILED", errorCode, errorMessage);
        transactionPort.markFailed(event.transactionId(), reason, failedAt);
        statusCacheInvalidationPort.invalidate(event.transactionId());
        telemetryPort.increment("pix.partner.errors");
        telemetryPort.increment("pix.transactions.failed");
        log.warn("PIX partner attempt blocked transactionId={} attempt={} maxAttempts={} status=FAILED errorCode={} errorMessage={}",
                event.transactionId(), attempt, maxProcessingAttempts, errorCode, errorMessage);
        publishDlq(event, originalPayload, attempt, errorCode, errorMessage, true);
    }

    private void publishDlq(
            PixRequestedEvent event,
            String originalPayload,
            int attempt,
            String errorCode,
            String errorMessage,
            boolean failedStatusApplied
    ) {
        dlqPublisherPort.publish(new DlqMessage(
                event.transactionId(),
                attempt,
                errorCode,
                errorMessage,
                originalPayload,
                Instant.now(),
                failedStatusApplied
        ));
        telemetryPort.increment("pix.processor.dlq");
        log.warn("PIX sent to DLQ transactionId={} attempts={} failedStatusApplied={} errorCode={}",
                event.transactionId(), attempt, failedStatusApplied, errorCode);
    }

    private PixRequestedEvent readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, PixRequestedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid PixRequested payload", e);
        }
    }

    private void putMdc(PixRequestedEvent event) {
        MDC.put("transactionId", event.transactionId());
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId());
        }
    }
}
