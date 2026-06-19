package com.transactions.pix_processor.adapter.out.client;

import com.transactions.pix_processor.domain.port.out.PartnerPixPort;
import com.transactions.pix_processor.domain.model.PartnerPixException;
import com.transactions.pix_processor.domain.model.PartnerPixResponse;
import com.transactions.pix_processor.domain.model.PixRequestedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PartnerPixClient implements PartnerPixPort {

    private final RestClient restClient;
    private final Timer latencyTimer;

    public PartnerPixClient(RestClient partnerRestClient, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.restClient = partnerRestClient;
        this.latencyTimer = Timer.builder("pix.partner.latency").register(meterRegistry);
    }

    @CircuitBreaker(name = "partnerPix")
    @Override
    public PartnerPixResponse send(PixRequestedEvent event) {
        return latencyTimer.record(() -> doSend(event));
    }

    private PartnerPixResponse doSend(PixRequestedEvent event) {
        try {
            PartnerPixResponse response = restClient.post()
                    .uri("/partner/pix")
                    .body(new PartnerPixRequest(
                            event.transactionId(),
                            event.amount(),
                            event.pixKey(),
                            event.description()
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new PartnerPixException(
                                "PARTNER_HTTP_" + clientResponse.getStatusCode().value(),
                                "Partner returned HTTP " + clientResponse.getStatusCode().value()
                        );
                    })
                    .body(PartnerPixResponse.class);

            if (response == null || response.partnerReferenceId() == null || response.partnerReferenceId().isBlank()) {
                throw new PartnerPixException("PARTNER_INVALID_RESPONSE", "Partner response is missing reference id");
            }
            return response;
        } catch (PartnerPixException e) {
            throw e;
        } catch (RestClientException e) {
            throw new PartnerPixException("PARTNER_CLIENT_ERROR", e.getMessage());
        }
    }
}
