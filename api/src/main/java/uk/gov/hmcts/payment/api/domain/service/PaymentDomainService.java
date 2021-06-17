package uk.gov.hmcts.payment.api.domain.service;

import uk.gov.hmcts.payment.api.dto.ReconcilePaymentResponse;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentDomainService {

    ReconcilePaymentResponse retrievePayments(Optional<String> startDateTimeString, Optional<String> endDateTimeString, Optional<String> paymentMethodType, Optional<String> serviceType, String pbaNumber, String ccdCaseNumber);

    Payment getPaymentByApportionment(FeePayApportion feePayApportion);

    Payment getPaymentByReference(String reference);

    List<FeePayApportion> getFeePayApportionByPaymentId(Integer id);
}
