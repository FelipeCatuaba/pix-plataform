package com.transactions.pix_processor.domain.port.out;

import com.transactions.pix_processor.domain.service.DlqMessage;

public interface DlqPublisherPort {

    void publish(DlqMessage message);
}
