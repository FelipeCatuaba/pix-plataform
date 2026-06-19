package com.transactions.pix_processor.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix_processor.domain.port.out.DlqPublisherPort;
import com.transactions.pix_processor.domain.service.DlqMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaDlqPublisherAdapter implements DlqPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaDlqPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DlqMessage message) {
        kafkaTemplate.send("pix.dlq", message.transactionId(), toJson(message)).join();
    }

    private String toJson(DlqMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize DLQ message", e);
        }
    }
}
