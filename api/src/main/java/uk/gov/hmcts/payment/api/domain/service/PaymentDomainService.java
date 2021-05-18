package uk.gov.hmcts.payment.api.domain.service;

import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.reform.ccd.client.model.SearchCriteria;

import java.util.List;

public interface PaymentDomainService {
    Payment getPaymentByApportionment(FeePayApportion feePayApportion);

    Payment getPaymentByReference(String reference);

    List<FeePayApportion> getFeePayApportionByPaymentId(Integer id);
}
