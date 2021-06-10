package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;

import javax.validation.constraints.AssertFalse;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "updatePaymentRequestWith")
@Wither
public class UpdatePaymentRequest {

    private String caseReference;

    private String ccdCaseNumber;

    @AssertFalse(message = "Either ccdCaseNumber or caseReference is required, and cannot be empty.")
    private boolean isEitherOneRequired() {
        return ((ccdCaseNumber == null || ccdCaseNumber.isEmpty()) && (caseReference == null || caseReference.isEmpty()));
    }


}
