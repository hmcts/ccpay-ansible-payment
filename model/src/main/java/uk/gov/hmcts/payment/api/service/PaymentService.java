package uk.gov.hmcts.payment.api.service;

import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.Reference;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface PaymentService<T, ID> {

    T retrieve(ID id);

    List<Reference> listInitiatedStatusPaymentsReferences();

    List<T> search(PaymentSearchCriteria searchCriteria);

    void updateTelephonyPaymentStatus(String reference, String status, String payload);

    List<Payment> getPayments(Date atStartOfDay, Date atEndOfDay);

    List<FeePayApportion> findByPaymentId(Integer paymentId);

    List<Payment> searchByCriteria(PaymentSearchCriteria searchCriteria);

}
