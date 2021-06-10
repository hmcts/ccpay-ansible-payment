package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.TimeZone;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "payment2DtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentDto {

    private String id;

    @NotEmpty
    private BigDecimal amount;

    @NotEmpty
    private String description;

    private String reference;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateUpdated;

    private CurrencyCode currency;

    private String ccdCaseNumber;

    private String caseReference;

    private String paymentReference;

    private String channel;

    private String method;

    private String externalProvider;

    private String status;

    private String externalReference;

    @NotEmpty
    private String siteId;

    private String serviceName;

    private String customerReference;

    private String accountNumber;

    private String organisationName;

    private String paymentGroupReference;

    private Date reportedDateOffline;

    private String documentControlNumber;

    private Date bankedDate;

    private String payerName;

    //@JsonUnwrapped
    @NotNull
    private List<FeeDto> fees;

    private List<StatusHistoryDto> statusHistories;

    private List<PaymentAllocationDto> paymentAllocation;

    private String giroSlipNo;

    @JsonProperty("_links")
    private LinksDto links;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    @JsonInclude(NON_NULL)
    public static class LinksDto {
        private LinkDto nextUrl;
        private LinkDto self;
        private LinkDto cancel;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(NON_NULL)
    public static class LinkDto {
        private String href;
        private String method;
    }

    public String toCardPaymentCsv() {
        StringJoiner result = new StringJoiner("\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss zzz");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        for (FeeDto fee : getFees()) {
            StringJoiner sb = new StringJoiner(",")
                .add(getServiceName())
                .add(getPaymentGroupReference())
                .add(getPaymentReference())
                .add(getCcdCaseNumber())
                .add(getCaseReference())
                .add(sdf.format(getDateCreated()))
                .add(sdf.format(getDateUpdated()))
                .add(getStatus())
                .add(getChannel())
                .add(getMethod())
                .add(getAmount() != null ? getAmount().toString() : "0.00")
                .add(getSiteId());

            String memoLineWithQuotes = fee.getMemoLine() != null ? new StringBuffer().append('"').append(fee.getMemoLine()).append('"').toString() : "";
            String naturalAccountCode = fee.getNaturalAccountCode() != null ? fee.getNaturalAccountCode() : "";
            sb.add(fee.getCode())
                .add(fee.getVersion())
                .add(fee.getCalculatedAmount() != null ? fee.getCalculatedAmount().toString() : "0.00")
                .add(memoLineWithQuotes)
                .add(naturalAccountCode)
                .add(fee.getVolume() != null ? fee.getVolume().toString() : "1");

            result.add(sb.toString());
        }

        return result.toString();
    }

    public String toCreditAccountPaymentCsv() {
        StringJoiner result = new StringJoiner("\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss zzz");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        for (FeeDto fee : getFees()) {
            StringJoiner sb = new StringJoiner(",")
                .add(getServiceName())
                .add(getPaymentGroupReference())
                .add(getPaymentReference())
                .add(getCcdCaseNumber())
                .add(getCaseReference())
                .add(getOrganisationName())
                .add(getCustomerReference())
                .add(getAccountNumber())
                .add(sdf.format(getDateCreated()))
                .add(sdf.format(getDateUpdated()))
                .add(getStatus())
                .add(getChannel())
                .add(getMethod())
                .add(getAmount().toString())
                .add(getSiteId());

            String memolineWithQuotes = fee.getMemoLine() != null ? new StringBuffer().append('"').append(fee.getMemoLine()).append('"').toString() : "";
            String naturalAccountCode = fee.getNaturalAccountCode() != null ? fee.getNaturalAccountCode() : "";

            sb.add(fee.getCode())
                .add(fee.getVersion())
                .add(fee.getCalculatedAmount().toString())
                .add(memolineWithQuotes)
                .add(naturalAccountCode)
                .add(fee.getVolume().toString());
            result.add(sb.toString());
        }

        return result.toString();
    }

    public String toPaymentCsv() {
        return toCreditAccountPaymentCsv();
    }
}
