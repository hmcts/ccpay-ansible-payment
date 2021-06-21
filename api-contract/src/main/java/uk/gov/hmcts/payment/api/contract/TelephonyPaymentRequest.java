package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import lombok.experimental.Wither;
import org.apache.commons.lang3.StringUtils;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Language;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@SuppressWarnings("unused")
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(builderMethodName = "createTelephonyPaymentRequestDtoWith")
@Wither
public class TelephonyPaymentRequest {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotEmpty
    private String description;

    private String ccdCaseNumber;

    private String caseReference;

    private CurrencyCode currency;

    private String provider;

    private String channel;

    private String language;

    @JsonProperty("case_type")
    @NotBlank
    private String caseType;

    @Valid
    private List<FeeDto> fees;

    @AssertFalse(message = "Either ccdCaseNumber or caseReference is required.")
    private boolean isEitherOneRequired() {
        return ((ccdCaseNumber == null || ccdCaseNumber.isEmpty()) && (caseReference == null || caseReference.isEmpty()));
    }

    @AssertFalse(message = "Invalid value for language attribute.")
    private boolean isValidLanguage() {
        return !StringUtils.isBlank(language) && !language.equalsIgnoreCase("string")
            && Arrays.stream(Language.values()).noneMatch(language1 ->
            language1.getLanguage().equalsIgnoreCase(language));
    }
}
