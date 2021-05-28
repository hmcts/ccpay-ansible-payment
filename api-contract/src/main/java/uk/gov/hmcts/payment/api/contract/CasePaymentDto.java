package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "casePaymentDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class CasePaymentDto {

    private BigDecimal amount;

    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateUpdated;

    private String reference;

    private String caseReference;

    private CurrencyCode currency;

    private String ccdCaseNumber;

    private String paymentReference;

    private String channel;

    private String accountNumber;

    private Date bankedDate;

    private String organisationName;

    private String externalReference;

    private String method;

    private String externalProvider;

    private String status;

    private String siteId;

    private String serviceName;

    private String customerReference;

    private String paymentGroupReference;

    private Date reportedDateOffline;

    private String giroSlipNo;

    private String documentControlNumber;

    private List<FeeDto> fees;

    private List<StatusHistoryDto> statusHistories;

    private List<PaymentAllocationDto> paymentAllocation;
}
