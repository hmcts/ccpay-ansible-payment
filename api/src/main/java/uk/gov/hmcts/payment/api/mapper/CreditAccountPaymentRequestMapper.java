package uk.gov.hmcts.payment.api.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CreditAccountPaymentRequestMapper {

    private final static String PAYMENT_CHANNEL_ONLINE = "online";

    @Autowired
    private CreditAccountDtoMapper creditAccountDtoMapper;

    public Payment mapPBARequest(CreditAccountPaymentRequest creditAccountPaymentRequest)
    {
        return Payment.paymentWith()
            .amount(creditAccountPaymentRequest.getAmount())
            .description(creditAccountPaymentRequest.getDescription())
            .ccdCaseNumber(creditAccountPaymentRequest.getCcdCaseNumber())
            .caseReference(creditAccountPaymentRequest.getCaseReference())
            .currency(creditAccountPaymentRequest.getCurrency().getCode())
            .serviceType(creditAccountPaymentRequest.getService())
            .customerReference(creditAccountPaymentRequest.getCustomerReference())
            .organisationName(creditAccountPaymentRequest.getOrganisationName())
            .pbaNumber(creditAccountPaymentRequest.getAccountNumber())
            .siteId(creditAccountPaymentRequest.getSiteId())
            .paymentChannel(PaymentChannel.paymentChannelWith().name(PAYMENT_CHANNEL_ONLINE).build())
            .build();
    }

    public List<PaymentFee> mapPBAFeesFromRequest(CreditAccountPaymentRequest creditAccountPaymentRequest)
    {
        List<PaymentFee> fees = creditAccountPaymentRequest.getFees().stream()
            .map(f -> creditAccountDtoMapper.toFee(f))
            .collect(Collectors.toList());

        fees.stream().forEach(fee -> {
            fee.setCcdCaseNumber((fee.getCcdCaseNumber() == null || fee.getCcdCaseNumber().isEmpty())
                ? creditAccountPaymentRequest.getCcdCaseNumber()
                : fee.getCcdCaseNumber());
        });
        return fees;
    }
}
