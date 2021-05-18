package uk.gov.hmcts.payment.api.domain.service;

import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.PaymentFee;

import java.util.List;

public interface FeeDomainService {
    List<FeePayApportion> getFeePayApportionsByFee(PaymentFee fee);

    PaymentFee getPaymentFeeById(Integer id);
}
