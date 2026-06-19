package com.transactions.pix.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactions.pix.domain.port.out.IdempotencyCachePort;
import com.transactions.pix.domain.port.out.StatusCachePort;
import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPixCacheAdapter implements IdempotencyCachePort, StatusCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisPixCacheAdapter.class);

    private static final String IDEMPOTENCY_KEY_PREFIX = "pix:idempotency:";
    private static final String STATUS_KEY_PREFIX = "pix:status:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPixCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PixTransactionStatus> findStatus(String transactionId) {
        try {
            String value = redisTemplate.opsForValue().get(idempotencyKey(transactionId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(PixTransactionStatus.valueOf(value));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for idempotency cache transactionId={}, falling back to PostgreSQL",
                    transactionId);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid idempotency cache value for transactionId={}, ignoring cache", transactionId);
            return Optional.empty();
        }
    }

    @Override
    public void cacheStatus(String transactionId, PixTransactionStatus status, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(idempotencyKey(transactionId), status.name(), ttl);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for idempotency cache transactionId={}, continuing with PostgreSQL as source of truth",
                    transactionId);
        }
    }

    @Override
    public Optional<PixTransaction> findByTransactionId(String transactionId) {
        try {
            String value = redisTemplate.opsForValue().get(statusKey(transactionId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, CachedPixStatus.class).toTransaction());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for status cache transactionId={}, falling back to PostgreSQL", transactionId);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Invalid cached status for transactionId={}, falling back to PostgreSQL", transactionId);
            redisTemplate.delete(statusKey(transactionId));
            return Optional.empty();
        }
    }

    @Override
    public void cache(PixTransaction transaction, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    statusKey(transaction.transactionId()),
                    objectMapper.writeValueAsString(CachedPixStatus.from(transaction)),
                    ttl
            );
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for status cache transactionId={}, continuing with PostgreSQL as source of truth",
                    transaction.transactionId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize cached PIX status", e);
        }
    }

    private String idempotencyKey(String transactionId) {
        return IDEMPOTENCY_KEY_PREFIX + transactionId;
    }

    private String statusKey(String transactionId) {
        return STATUS_KEY_PREFIX + transactionId;
    }
}
