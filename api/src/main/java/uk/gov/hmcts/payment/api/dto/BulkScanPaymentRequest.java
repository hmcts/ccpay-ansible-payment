package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import javax.validation.constraints.*;
import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@With
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createBulkScanPaymentWith")
public class BulkScanPaymentRequest {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotNull
    private PaymentMethodType paymentMethod;

    @NotNull
    @JsonProperty("requestor")
    private String service;

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

    @NotEmpty
    @JsonProperty("site_id")
    private String siteId;

    private String payerName;

    @NotEmpty
    @NotNull
    private String documentControlNumber;

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

