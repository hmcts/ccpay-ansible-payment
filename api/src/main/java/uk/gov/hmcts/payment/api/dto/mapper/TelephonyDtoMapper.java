package uk.gov.hmcts.payment.api.dto.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.contract.TelephonyCardPaymentsResponse;
import uk.gov.hmcts.payment.api.external.client.dto.TelephonyProviderAuthorisationResponse;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.util.PayStatusToPayHubStatus;

@Component
public class TelephonyDtoMapper {

    public TelephonyCardPaymentsResponse toTelephonyCardPaymentsResponse(PaymentFeeLink paymentFeeLink, Payment payment, TelephonyProviderAuthorisationResponse telephonyProviderAuthorisationResponse) {
        return TelephonyCardPaymentsResponse.telephonyCardPaymentsResponseWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .paymentReference(payment.getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .dateCreated(payment.getDateCreated())
            .links(new TelephonyCardPaymentsResponse.NextURLDtos(new TelephonyCardPaymentsResponse.NextURLDto(telephonyProviderAuthorisationResponse.getNextUrl(), "POST", telephonyProviderAuthorisationResponse.getAccessToken(), telephonyProviderAuthorisationResponse.getRefreshToken())))
            .build();
    }
}
