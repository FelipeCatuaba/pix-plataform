package com.transactions.pix.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceNotFoundExceptionTest {

    @Test
    void shouldPreserveBusinessMessage() {
        // arrange
        String message = "PIX transaction not found: tx-404";

        // act
        ResourceNotFoundException exception = new ResourceNotFoundException(message);

        // assert
        assertEquals(message, exception.getMessage());
    }
}
