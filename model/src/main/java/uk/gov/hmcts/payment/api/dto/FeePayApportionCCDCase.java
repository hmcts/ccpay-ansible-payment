package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.Remission;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "feePayApportionCCDCaseWith")
@Data
public class FeePayApportionCCDCase {

    private String ccdCaseNo;

    private List<PaymentFeeLink> feePayGroups;

    private List<PaymentFee> fees;

    private List<Remission> remissions;

    private List<Payment> payments;
}
