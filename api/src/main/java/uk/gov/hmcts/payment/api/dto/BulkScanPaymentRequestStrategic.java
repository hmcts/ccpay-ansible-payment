package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import javax.validation.constraints.*;
import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createBulkScanPaymentStrategicWith")
public class BulkScanPaymentRequestStrategic {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotNull
    private PaymentMethodType paymentMethod;

    private String ccdCaseNumber;

    private String exceptionRecord;

    @NotNull
    private PaymentChannel paymentChannel;

    @NotNull
    private PaymentStatus paymentStatus;

    @NotNull
    private CurrencyCode currency;

    private String externalProvider;

    @NotNull
    private String giroSlipNo;

    @NotNull
    private String bankedDate;

    @NotBlank
    private String caseType;

    private String payerName;

    @NotEmpty
    @NotNull
    private String documentControlNumber;


    @NotNull
    private PaymentAllocationDto paymentAllocationDTO;

    @JsonIgnore
    @AssertFalse(message = "Invalid payment banked date. Date format should be UTC.")
    public boolean isValidBankedDate() {
        if (bankedDate != null) {
            try {
                DateTime.parse(bankedDate);
            } catch (IllegalArgumentException fe) {
                return true;
            }
        }
        return false;
    }

    @AssertFalse(message = "Either ccdCaseNumber or exceptionRecord is required.")
    private boolean isEitherOneRequired() {
        return (StringUtils.isEmpty(ccdCaseNumber) && StringUtils.isEmpty(exceptionRecord));
    }



}

