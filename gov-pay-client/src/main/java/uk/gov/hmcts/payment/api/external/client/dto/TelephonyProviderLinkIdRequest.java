package uk.gov.hmcts.payment.api.external.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "telephonyProviderLinkIdRequestWith")
public class TelephonyProviderLinkIdRequest {

    private String flowId;
    private InitialValues initialValues;

    @Data
    @JsonInclude(NON_NULL)
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder(builderMethodName = "initialValuesWith")
    public static class InitialValues {
        private String orderId;
        private String amount;
        private String currencyCode;
        private String callbackURL;
        private String returnURL;
    }
}
