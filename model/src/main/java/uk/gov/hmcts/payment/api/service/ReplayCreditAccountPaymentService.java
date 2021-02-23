package uk.gov.hmcts.payment.api.service;

import uk.gov.hmcts.payment.api.model.PaymentStatus;

public interface ReplayCreditAccountPaymentService<T, ID> {

    void updatePaymentStatusByReference(String paymentReference, PaymentStatus paymentStatus, String message);

}
