
package uk.gov.hmcts.payment.api.contract;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "payment2DtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RetrieveOrderPaymentDto {


    private String accountNumber;

    private BigDecimal amount;

    private Date bankedDate;

    private String caseReference;

    private String ccdCaseNumber;

    private String channel;

    private CurrencyCode currency;

    private String customerReference;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateUpdated;

    private String description;

    private String documentControlNumber;

    private String externalProvider;

    private String externalReference;

    //@JsonUnwrapped
    @NotNull
    private List<FeeDto> fees;

    private String giroSlipNo;

    private String id;

    private String method;

    private String organisationName;

    private String payerName;

    private List<PaymentAllocationDto> paymentAllocation;

    private String paymentGroupReference;

    private String paymentReference;

    private String reference;

    private Date reportedDateOffline;

    private String serviceName;

    @NotEmpty
    private String siteId;

    private String status;

    private List<StatusHistoryDto> statusHistories;

    @JsonProperty("_links")
    private PaymentDto.LinksDto links;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(NON_NULL)
    public static class LinksDto {
        private PaymentDto.LinkDto nextUrl;
        private PaymentDto.LinkDto self;
        private PaymentDto.LinkDto cancel;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(NON_NULL)
    public static class LinkDto {
        private String href;
        private String method;
    }


}
