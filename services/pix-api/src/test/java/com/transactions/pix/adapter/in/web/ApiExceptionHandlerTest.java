package com.transactions.pix.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class ApiExceptionHandlerTest {

    @Test
    void shouldHandleNotFoundUnsupportedMediaAndUnexpectedErrors() {
        // arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiExceptionHandler handler = new ApiExceptionHandler(registry);

        // act
        var notFound = handler.handleNotFound(new ResourceNotFoundException("missing"));
        var unsupported = handler.handleUnsupportedMediaType(mock(HttpMediaTypeNotSupportedException.class));
        var unexpected = handler.handleUnexpected(new IllegalStateException("boom"));

        // assert
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("PIX_NOT_FOUND", notFound.getBody().get("code"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, unexpected.getStatusCode());
        assertEquals(3.0, registry.counter("pix.api.errors").count());
    }

    @Test
    void shouldUseFirstFieldErrorForValidationResponse() {
        // arrange
        BindingResult bindingResult = mock(BindingResult.class);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("pixRequest", "amount", "must be greater than 0")
        ));

        // act
        var response = new ApiExceptionHandler(new SimpleMeterRegistry()).handleValidation(exception);

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("amount must be greater than 0", response.getBody().get("message"));
    }

    @Test
    void shouldUseFallbackWhenValidationHasNoFieldErrors() {
        // arrange
        BindingResult bindingResult = mock(BindingResult.class);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        // act
        var response = new ApiExceptionHandler(new SimpleMeterRegistry()).handleValidation(exception);

        // assert
        assertEquals("Invalid request", response.getBody().get("message"));
    }

    @Test
    void shouldHandleRemainingInvalidRequestTypes() {
        // arrange
        ApiExceptionHandler handler = new ApiExceptionHandler(new SimpleMeterRegistry());
        ConstraintViolationException constraintViolation = new ConstraintViolationException("invalid key", null);

        // act
        var method = handler.handleMethodValidation(mock(HandlerMethodValidationException.class));
        var constraint = handler.handleConstraintViolation(constraintViolation);
        var unreadable = handler.handleUnreadableMessage(mock(HttpMessageNotReadableException.class));

        // assert
        assertEquals("Request parameters failed validation", method.getBody().get("message"));
        assertEquals("invalid key", constraint.getBody().get("message"));
        assertEquals("Malformed JSON request", unreadable.getBody().get("message"));
    }
}
