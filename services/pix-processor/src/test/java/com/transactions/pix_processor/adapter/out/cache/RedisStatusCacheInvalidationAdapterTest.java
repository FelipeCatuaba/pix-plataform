package com.transactions.pix_processor.adapter.out.cache;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisStatusCacheInvalidationAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldDeleteStatusCacheKey() {
        // arrange
        RedisStatusCacheInvalidationAdapter adapter =
                new RedisStatusCacheInvalidationAdapter(redisTemplate);

        // act
        adapter.invalidate("tx-1");

        // assert
        verify(redisTemplate).delete("pix:status:tx-1");
    }

    @Test
    void shouldIgnoreRedisConnectionFailure() {
        // arrange
        when(redisTemplate.delete("pix:status:tx-2"))
                .thenThrow(new RedisConnectionFailureException("offline"));

        // act
        new RedisStatusCacheInvalidationAdapter(redisTemplate).invalidate("tx-2");

        // assert
        verify(redisTemplate).delete("pix:status:tx-2");
    }
}
