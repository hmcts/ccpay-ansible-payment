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
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createCardPaymentRequestDtoWith")
@Wither
public class CardPaymentRequest {

    @NotNull
    @DecimalMin("0.01")
    @Positive
    @Digits(integer = 10, fraction = 2, message = "Payment amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @NotEmpty
    private String description;

    private String ccdCaseNumber;

    private String caseReference;

    /*
    Following attribute to be removed once all Services are on-boarded to Enterprise ORG ID
    */
    private String service;

    private CurrencyCode currency;

    private String provider;

    private String channel;

    private String language;

    /*
    Following attribute to be removed once all Services are on-boarded to Enterprise ORG ID
    */
    @JsonProperty("site_id")
    private String siteId;

    @JsonProperty("case_type")
    private String caseType;

    @Valid
    private List<FeeDto> fees;

    @AssertFalse(message = "Either ccdCaseNumber or caseReference is required.")
    private boolean isEitherOneRequired() {
        return ((ccdCaseNumber == null || ccdCaseNumber.isEmpty()) && (caseReference == null || caseReference.isEmpty()));
    }

    @AssertFalse(message = "Either of Site ID or Case Type is mandatory as part of the request.")
    private boolean isEitherIdOrTypeRequired() {
        return ((StringUtils.isNotBlank(caseType) && StringUtils.isNotBlank(siteId)) ||
            (StringUtils.isBlank(caseType) && StringUtils.isBlank(siteId)));
    }

    @AssertFalse(message = "Invalid value for language attribute.")
    private boolean isValidLanguage() {
        return !StringUtils.isBlank(language) && !language.equalsIgnoreCase("string")
            && Arrays.stream(Language.values()).noneMatch(language1 ->
            language1.getLanguage().equalsIgnoreCase(language));
    }
}
