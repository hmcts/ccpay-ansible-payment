package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "telephonyCardPaymentsResponseWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TelephonyCardPaymentsResponse {

    private String paymentReference;

    private String paymentGroupReference;

    private String ccdCaseNumber;

    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

    @JsonProperty("_links")
    private NextURLDtos links;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(NON_NULL)
    public static class NextURLDtos {
        private NextURLDto nextUrl;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(NON_NULL)
    public static class NextURLDto {
        private String href;
        private String method;
        private String accessToken;
        private String refreshToken;
    }
}
