package com.transactions.pix_processor.adapter.out.cache;

import com.transactions.pix_processor.domain.port.out.StatusCacheInvalidationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisStatusCacheInvalidationAdapter implements StatusCacheInvalidationPort {

    private static final Logger log = LoggerFactory.getLogger(RedisStatusCacheInvalidationAdapter.class);

    private final StringRedisTemplate redisTemplate;

    public RedisStatusCacheInvalidationAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void invalidate(String transactionId) {
        try {
            redisTemplate.delete("pix:status:" + transactionId);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable while invalidating status cache transactionId={}", transactionId);
        }
    }
}
