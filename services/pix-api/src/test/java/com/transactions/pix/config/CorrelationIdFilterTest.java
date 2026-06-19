package com.transactions.pix.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPropagateProvidedCorrelationIdAndClearMdc() throws Exception {
        // arrange
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn("corr-123");

        // act
        new CorrelationIdFilter().doFilterInternal(request, response, chain);

        // assert
        verify(response).setHeader(CorrelationIdFilter.HEADER_NAME, "corr-123");
        verify(chain).doFilter(request, response);
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
        verify(request).setAttribute(
                org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.REQUEST_STARTED_AT_ATTRIBUTE),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsBlank() throws Exception {
        // arrange
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(" ");
        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);

        // act
        new CorrelationIdFilter().doFilterInternal(request, response, chain);

        // assert
        verify(response).setHeader(
                org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER_NAME),
                correlationId.capture()
        );
        assertNotNull(correlationId.getValue());
    }

    @Test
    void shouldClearMdcWhenFilterChainFails() throws Exception {
        // arrange
        when(request.getHeader(CorrelationIdFilter.HEADER_NAME)).thenReturn(null);
        org.mockito.Mockito.doThrow(new jakarta.servlet.ServletException("downstream"))
                .when(chain).doFilter(request, response);

        // act
        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () -> new CorrelationIdFilter().doFilterInternal(request, response, chain)
        );

        // assert
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
