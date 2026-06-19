package com.transactions.pix.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisPixCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldReadAndWriteIdempotencyStatus() {
        // arrange
        when(valueOperations.get("pix:idempotency:tx-1")).thenReturn("PROCESSING");
        RedisPixCacheAdapter adapter = adapter(new ObjectMapper().findAndRegisterModules());

        // act
        var status = adapter.findStatus("tx-1");
        adapter.cacheStatus("tx-1", PixTransactionStatus.PROCESSING, Duration.ofMinutes(5));

        // assert
        assertEquals(PixTransactionStatus.PROCESSING, status.orElseThrow());
        verify(valueOperations).set(
                "pix:idempotency:tx-1", "PROCESSING", Duration.ofMinutes(5)
        );
    }

    @Test
    void shouldIgnoreMissingInvalidAndUnavailableIdempotencyValues() {
        // arrange
        when(valueOperations.get("pix:idempotency:missing")).thenReturn(null);
        when(valueOperations.get("pix:idempotency:invalid")).thenReturn("UNKNOWN");
        when(valueOperations.get("pix:idempotency:offline"))
                .thenThrow(new RedisConnectionFailureException("offline"));
        RedisPixCacheAdapter adapter = adapter(new ObjectMapper());

        // act
        boolean missing = adapter.findStatus("missing").isPresent();
        boolean invalid = adapter.findStatus("invalid").isPresent();
        boolean offline = adapter.findStatus("offline").isPresent();

        // assert
        assertFalse(missing);
        assertFalse(invalid);
        assertFalse(offline);
    }

    @Test
    void shouldIgnoreRedisFailureWhileCachingIdempotencyStatus() {
        // arrange
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("offline"))
                .when(valueOperations).set(
                        "pix:idempotency:tx-2", "FAILED", Duration.ofMinutes(1)
                );

        // act
        adapter(new ObjectMapper()).cacheStatus("tx-2", PixTransactionStatus.FAILED, Duration.ofMinutes(1));

        // assert
        verify(valueOperations).set("pix:idempotency:tx-2", "FAILED", Duration.ofMinutes(1));
    }

    @Test
    void shouldReadWriteAndRecoverStatusCache() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        PixTransaction transaction = transaction("tx-3");
        String json = mapper.writeValueAsString(CachedPixStatus.from(transaction));
        when(valueOperations.get("pix:status:tx-3")).thenReturn(json);
        RedisPixCacheAdapter adapter = adapter(mapper);

        // act
        PixTransaction cached = adapter.findByTransactionId("tx-3").orElseThrow();
        adapter.cache(transaction, Duration.ofSeconds(30));

        // assert
        assertEquals("tx-3", cached.transactionId());
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("pix:status:tx-3"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void shouldHandleMissingBrokenAndUnavailableStatusCache() throws Exception {
        // arrange
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(valueOperations.get("pix:status:missing")).thenReturn(null);
        when(valueOperations.get("pix:status:broken")).thenReturn("{}");
        when(mapper.readValue("{}", CachedPixStatus.class))
                .thenThrow(new JsonProcessingException("broken") { });
        when(valueOperations.get("pix:status:offline"))
                .thenThrow(new RedisConnectionFailureException("offline"));
        RedisPixCacheAdapter adapter = adapter(mapper);

        // act
        boolean missing = adapter.findByTransactionId("missing").isPresent();
        boolean broken = adapter.findByTransactionId("broken").isPresent();
        boolean offline = adapter.findByTransactionId("offline").isPresent();

        // assert
        assertFalse(missing);
        assertFalse(broken);
        assertFalse(offline);
        verify(redisTemplate).delete("pix:status:broken");
    }

    @Test
    void shouldWrapSerializationFailureAndIgnoreRedisFailureWhenCachingStatus() throws Exception {
        // arrange
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        PixTransaction transaction = transaction("tx-4");
        when(mapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new JsonProcessingException("broken") { });

        // act
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> adapter(mapper).cache(transaction, Duration.ofSeconds(30))
        );

        // assert
        assertEquals("Could not serialize cached PIX status", error.getMessage());

        // arrange
        ObjectMapper validMapper = new ObjectMapper().findAndRegisterModules();
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("offline"))
                .when(valueOperations).set(
                        org.mockito.ArgumentMatchers.eq("pix:status:tx-4"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))
                );

        // act
        adapter(validMapper).cache(transaction, Duration.ofSeconds(30));

        // assert
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("pix:status:tx-4"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))
        );
    }

    private RedisPixCacheAdapter adapter(ObjectMapper mapper) {
        return new RedisPixCacheAdapter(redisTemplate, mapper);
    }

    private PixTransaction transaction(String id) {
        Instant now = Instant.parse("2026-06-19T12:00:00Z");
        return new PixTransaction(
                1L, id, BigDecimal.TEN, "key", "invoice", PixTransactionStatus.PROCESSING,
                null, null, now, now
        );
    }
}
