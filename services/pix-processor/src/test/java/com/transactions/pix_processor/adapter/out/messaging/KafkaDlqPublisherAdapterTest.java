package com.transactions.pix_processor.adapter.out.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix_processor.domain.service.DlqMessage;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaDlqPublisherAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldSerializeAndPublishDlqMessage() {
        // arrange
        DlqMessage message = message();
        when(kafkaTemplate.send(
                org.mockito.ArgumentMatchers.eq("pix.dlq"),
                org.mockito.ArgumentMatchers.eq("tx-1"),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        // act
        new KafkaDlqPublisherAdapter(kafkaTemplate, new ObjectMapper().findAndRegisterModules())
                .publish(message);

        // assert
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq("pix.dlq"),
                org.mockito.ArgumentMatchers.eq("tx-1"),
                org.mockito.ArgumentMatchers.contains("\"lastError\":\"PARTNER_ERROR\"")
        );
    }

    @Test
    void shouldWrapSerializationFailure() throws Exception {
        // arrange
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(mapper.writeValueAsString(message()))
                .thenThrow(new JsonProcessingException("broken") { });

        // act
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new KafkaDlqPublisherAdapter(kafkaTemplate, mapper).publish(message())
        );

        // assert
        assertEquals("Could not serialize DLQ message", error.getMessage());
    }

    private DlqMessage message() {
        return new DlqMessage(
                "tx-1", 3, "PARTNER_ERROR", "failed", "{}", Instant.parse("2026-06-19T12:00:00Z"), true
        );
    }
}
