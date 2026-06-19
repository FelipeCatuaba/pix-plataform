package com.transactions.pix_processor.domain.model;

public record PartnerPixResponse(
        String partnerReferenceId,
        String status
) {
}
