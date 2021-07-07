package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import org.joda.time.DateTime;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@With
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createPaymentRecordRequestDtoWith")
public class PaymentRecordRequest {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotNull
    private PaymentMethodType paymentMethod;

    @NotEmpty
    @JsonProperty("requestor_reference")
    private String reference;

    @NotNull
    @JsonProperty("requestor")
    private String service;

    private PaymentChannel paymentChannel;

    private PaymentStatus paymentStatus;

    private CurrencyCode currency;

    private String externalReference;

    private String externalProvider;

    private String giroSlipNo;

    @NotNull
    private String reportedDateOffline;

    @NotEmpty
    @JsonProperty("site_id")
    private String siteId;

    @NotEmpty
    @Valid
    private List<FeeDto> fees;

    @JsonIgnore
    @AssertFalse(message = "Invalid payment reported offline date. Date format should be UTC.")
    public boolean isValidReportedDateOffline() {
        if (reportedDateOffline != null) {
            try {
                DateTime.parse(reportedDateOffline);
            } catch (IllegalArgumentException fe) {
                return true;
            }
        }

        return false;
    }
}
