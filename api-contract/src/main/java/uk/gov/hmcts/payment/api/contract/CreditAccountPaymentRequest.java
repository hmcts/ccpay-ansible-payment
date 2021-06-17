package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createCreditAccountPaymentRequestDtoWith")
@Wither
public class CreditAccountPaymentRequest {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotEmpty
    private String description;

    private String ccdCaseNumber;

    private String caseReference;

    @NotNull
    private Service service;

    private CurrencyCode currency;

    @NotEmpty
    private String customerReference;

    @NotEmpty
    private String organisationName;

    @NotEmpty
    private String accountNumber;

    @NotEmpty
    @JsonProperty("site_id")
    private String siteId;

    @NotEmpty
    @Valid
    private List<FeeDto> fees;

    @AssertFalse(message = "Either ccdCaseNumber or caseReference is required.")
    private boolean isEitherOneRequired() {
        return (ccdCaseNumber == null && caseReference == null);
    }

    @AssertFalse(message = "Invalid Site ID (URN) provided for FPL. Accepted values are ABA3")
    private boolean isValidSiteId() {
        String[] validSiteIds = {"ABA3"};
        if(null != service && service.getName().equalsIgnoreCase(Service.FPL.getName())) {
            return siteId != null && !Arrays.asList(validSiteIds).stream().anyMatch(vm -> vm.equalsIgnoreCase(
                siteId));
        } else {
            return false;
        }
    }


    @AssertFalse(message = "Invalid Site ID (URN) provided for IAC. Accepted values are BFA1")
    private boolean isValidSiteIdIAC() {
        String[] validSiteIds = {"BFA1"};
        if(null != service && service.getName().equalsIgnoreCase(Service.IAC.getName())) {
            return siteId != null && !Arrays.asList(validSiteIds).stream().anyMatch(vm -> vm.equalsIgnoreCase(
                siteId));
        } else {
            return false;
        }
    }

    @AssertFalse(message = "Invalid Site ID (URN) provided for Civil. Accepted values are AAA7")
    private boolean isValidSiteIdCivil() {
        String[] validSiteIds = {"AAA7"};
        if(null != service && service.getName().equalsIgnoreCase(Service.CIVIL.getName())) {
            return siteId != null && !Arrays.asList(validSiteIds).stream().anyMatch(vm -> vm.equalsIgnoreCase(
                siteId));
        } else {
            return false;
        }
    }

    @AssertFalse(message = "Invalid Site ID (URN) provided for PROBATE. Accepted values are ABA6")
    private boolean isValidSiteIdProbate() {
        String[] validSiteIds = {"ABA6"};
        if(null != service && service.getName().equalsIgnoreCase(Service.PROBATE.getName())) {
            return siteId != null && !Arrays.asList(validSiteIds).stream().anyMatch(vm -> vm.equalsIgnoreCase(
                siteId));
        } else {
            return false;
        }
    }

}
