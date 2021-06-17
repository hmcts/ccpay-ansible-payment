package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "reconcilePaymentDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class ReconcilePaymentDto {

    private BigDecimal amount;

    private Date dateCreated;

    private Date dateUpdated;

    private String currency;

    private String ccdCaseNumber;

    private String caseReference;

    private String paymentReference;

    private String channel;

    private String method;

    private String externalProvider;

    private String status;

    private String externalReference;

    private String serviceName;

    private String siteId;

    private String organisationName;

    private String accountNumber;

    private String customerReference;

    private String paymentGroupReference;

    private Date bankedDate;

    private Date reportedDateOffline;

    private String giroSlipNo;

    private List<PaymentAllocationDto> paymentAllocation;

    private List<FeeDto> fees;
}
