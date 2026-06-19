package com.transactions.pix.adapter.out.messaging;

import com.transactions.pix.adapter.out.persistence.OutboxEvent;
import com.transactions.pix.adapter.out.persistence.OutboxRepositoryAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepositoryAdapter outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter publishedCounter;
    private final DistributionSummary publicationDuration;
    private final AtomicInteger pendingGauge = new AtomicInteger();

    public OutboxPublisher(
            OutboxRepositoryAdapter outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.publishedCounter = Counter.builder("pix.outbox.published").register(meterRegistry);
        this.publicationDuration = DistributionSummary.builder("pix.outbox.duration.seconds")
                .description("Tempo entre a entrada da transacao e a publicacao do Outbox no Kafka")
                .baseUnit("seconds")
                .publishPercentileHistogram()
                .register(meterRegistry);
        Gauge.builder("pix.outbox.pending", pendingGauge, AtomicInteger::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${pix.outbox.publisher-delay:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findPending(50);
        pendingGauge.set(events.size());

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send("pix.requested", event.aggregateId(), event.payload()).get(5, TimeUnit.SECONDS);
                Instant publishedAt = Instant.now();
                outboxRepository.markPublished(event.id(), publishedAt);
                publishedCounter.increment();
                Duration duration = Duration.between(event.createdAt(), publishedAt);
                publicationDuration.record(Math.max(0, duration.toNanos() / 1_000_000_000.0));
                log.info("Published outbox event eventId={} transactionId={} eventType={}",
                        event.id(), event.aggregateId(), event.eventType());
            } catch (Exception e) {
                log.error("Outbox publish failed eventId={} transactionId={} error={}",
                        event.id(), event.aggregateId(), e.getMessage());
            }
        }
    }
}
