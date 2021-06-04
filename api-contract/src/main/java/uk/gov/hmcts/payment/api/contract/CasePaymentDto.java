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

    private String paymentReference;

    private String paymentGroupReference;

    private String serviceName;

    private String siteId;

    private BigDecimal amount;

    private String caseReference;

    private String ccdCaseNumber;

    private String accountNumber;

    private String organisationName;

    private String customerReference;

    private String channel;

    private CurrencyCode currency;

    private String status;

    private List<StatusHistoryDto> statusHistories;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateUpdated;

    private String method;

    private String giroSlipNo;

    private String externalProvider;

    private String externalReference;

    private Date reportedDateOffline;

    private List<FeeDto> fees;
//    ----

//    private String description;
//    private String reference;
//    private Date bankedDate;
//    private String documentControlNumber;
//    private List<PaymentAllocationDto> paymentAllocation;
}
