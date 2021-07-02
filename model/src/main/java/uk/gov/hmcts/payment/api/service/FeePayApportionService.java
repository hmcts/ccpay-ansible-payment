package uk.gov.hmcts.payment.api.service;

import uk.gov.hmcts.payment.api.dto.FeePayApportionCCDCase;
import uk.gov.hmcts.payment.api.model.Payment;

public interface FeePayApportionService {

    FeePayApportionCCDCase processFeePayApportion(FeePayApportionCCDCase feePayApportionCCDCase);

    void processApportion(Payment payment);

    void updateFeeAmountDue(Payment payment);

}
