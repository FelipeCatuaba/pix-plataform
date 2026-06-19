package com.transactions.pix_processor.adapter.out.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.transactions.pix_processor.domain.model.PartnerPixException;
import com.transactions.pix_processor.domain.model.PartnerPixResponse;
import com.transactions.pix_processor.domain.model.PixRequestedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PartnerPixClientTest {

    @Test
    void shouldReturnValidPartnerResponse() {
        // arrange
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://partner/partner/pix"))
                .andRespond(withSuccess(
                        "{\"partnerReferenceId\":\"partner-1\",\"status\":\"APPROVED\"}",
                        MediaType.APPLICATION_JSON
                ));

        // act
        PartnerPixResponse response = fixture.client.send(event());

        // assert
        assertEquals("partner-1", response.partnerReferenceId());
        fixture.server.verify();
    }

    @Test
    void shouldRejectNullBlankAndMissingPartnerReferences() {
        // arrange
        Fixture nullBody = fixture();
        nullBody.server.expect(requestTo("http://partner/partner/pix"))
                .andRespond(withSuccess());

        // act
        PartnerPixException nullError = assertThrows(
                PartnerPixException.class,
                () -> nullBody.client.send(event())
        );

        // assert
        assertEquals("PARTNER_INVALID_RESPONSE", nullError.errorCode());

        // arrange
        Fixture missing = fixture();
        missing.server.expect(requestTo("http://partner/partner/pix"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        // act
        PartnerPixException missingError = assertThrows(
                PartnerPixException.class,
                () -> missing.client.send(event())
        );

        // assert
        assertEquals("PARTNER_INVALID_RESPONSE", missingError.errorCode());

        // arrange
        Fixture blank = fixture();
        blank.server.expect(requestTo("http://partner/partner/pix"))
                .andRespond(withSuccess(
                        "{\"partnerReferenceId\":\" \",\"status\":\"APPROVED\"}",
                        MediaType.APPLICATION_JSON
                ));

        // act
        PartnerPixException blankError = assertThrows(
                PartnerPixException.class,
                () -> blank.client.send(event())
        );

        // assert
        assertEquals("PARTNER_INVALID_RESPONSE", blankError.errorCode());
    }

    @Test
    void shouldTranslatePartnerHttpError() {
        // arrange
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://partner/partner/pix")).andRespond(withServerError());

        // act
        PartnerPixException error = assertThrows(
                PartnerPixException.class,
                () -> fixture.client.send(event())
        );

        // assert
        assertEquals("PARTNER_HTTP_500", error.errorCode());
    }

    @Test
    void shouldTranslateRestClientTransportError() {
        // arrange
        RestClient unavailable = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        PartnerPixClient client = new PartnerPixClient(unavailable, new SimpleMeterRegistry());

        // act
        PartnerPixException error = assertThrows(PartnerPixException.class, () -> client.send(event()));

        // assert
        assertEquals("PARTNER_CLIENT_ERROR", error.errorCode());
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://partner");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                new PartnerPixClient(builder.build(), new SimpleMeterRegistry()),
                server
        );
    }

    private PixRequestedEvent event() {
        return new PixRequestedEvent(
                "tx-1", BigDecimal.TEN, "key", "invoice",
                Instant.parse("2026-06-19T12:00:00Z"), "corr-1"
        );
    }

    private record Fixture(PartnerPixClient client, MockRestServiceServer server) {
    }
}
