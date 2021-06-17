package uk.gov.hmcts.payment.api.service;

import uk.gov.hmcts.payment.api.dto.FeePayApportionCCDCase;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;

import java.util.List;

public interface FeePayApportionService {

    FeePayApportionCCDCase processFeePayApportion(FeePayApportionCCDCase feePayApportionCCDCase);

    void processApportion(Payment payment);

    void updateFeeAmountDue(Payment payment);

    List<FeePayApportion> getFeePayApportionByFeeId(Integer feeId);

}
