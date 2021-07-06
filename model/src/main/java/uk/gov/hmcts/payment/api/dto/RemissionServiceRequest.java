package uk.gov.hmcts.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import uk.gov.hmcts.payment.api.model.PaymentFee;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@Builder(builderMethodName = "remissionServiceRequestWith")
public class RemissionServiceRequest {
    private String paymentGroupReference;
    private String remissionReference;
    private String hwfReference;
    private BigDecimal hwfAmount;
    private String beneficiaryName;
    private String ccdCaseNumber;
    private String caseReference;
    private String siteId;
    private PaymentFee fee;
    private boolean isRetroRemission;
}
