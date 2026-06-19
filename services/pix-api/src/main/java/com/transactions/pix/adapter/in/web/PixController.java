package com.transactions.pix.adapter.in.web;

import com.transactions.pix.adapter.in.web.request.PixRequest;
import com.transactions.pix.adapter.in.web.response.PixResponse;
import com.transactions.pix.adapter.in.web.response.PixStatusResponse;
import com.transactions.pix.domain.port.in.CreatePixCommand;
import com.transactions.pix.domain.port.in.PixUseCase;
import com.transactions.pix.config.CorrelationIdFilter;
import com.transactions.pix.domain.model.PixTransaction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pix")
public class PixController {

    private final PixUseCase pixUseCase;

    public PixController(PixUseCase pixUseCase) {
        this.pixUseCase = pixUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PixResponse create(@Valid @RequestBody PixRequest request, HttpServletRequest servletRequest) {
        Instant requestStartedAt = (Instant) servletRequest.getAttribute(
                CorrelationIdFilter.REQUEST_STARTED_AT_ATTRIBUTE
        );
        PixTransaction transaction = pixUseCase.create(new CreatePixCommand(
                request.transactionId(),
                request.amount(),
                request.pixKey(),
                request.description(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                requestStartedAt == null ? Instant.now() : requestStartedAt
        ));
        return PixResponse.from(transaction);
    }

    @GetMapping("/{transactionId}")
    public PixStatusResponse findByTransactionId(@PathVariable String transactionId) {
        PixTransaction transaction = pixUseCase.findByTransactionId(transactionId);
        return PixStatusResponse.from(transaction);
    }
}
