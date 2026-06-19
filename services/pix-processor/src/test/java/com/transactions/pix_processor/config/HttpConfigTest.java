package com.transactions.pix_processor.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class HttpConfigTest {

    @Test
    void shouldBuildRestClientWithConfiguredBaseUrl() {
        // arrange
        RestClient client = new HttpConfig().partnerRestClient(
                "http://127.0.0.1:1", 20, 20
        );

        // act + assert
        assertThrows(
                ResourceAccessException.class,
                () -> client.get().uri("/health").retrieve().toBodilessEntity()
        );
    }
}
