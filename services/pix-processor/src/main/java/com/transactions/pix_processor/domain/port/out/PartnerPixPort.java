package com.transactions.pix_processor.domain.port.out;

import com.transactions.pix_processor.domain.model.PixRequestedEvent;
import com.transactions.pix_processor.domain.model.PartnerPixResponse;

public interface PartnerPixPort {

    PartnerPixResponse send(PixRequestedEvent event);
}
