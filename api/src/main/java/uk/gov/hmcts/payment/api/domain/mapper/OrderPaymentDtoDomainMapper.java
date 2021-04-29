package uk.gov.hmcts.payment.api.domain.mapper;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.domain.model.OrderPaymentBo;
import uk.gov.hmcts.payment.api.dto.order.OrderPaymentDto;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;

@Component
public class OrderPaymentDtoDomainMapper {

    @Autowired
    private ReferenceUtil referenceUtil;

    public OrderPaymentBo toDomain(OrderPaymentDto paymentDto) throws CheckDigitException {

        String reference = referenceUtil.getNext("RC");

        return OrderPaymentBo.orderPaymentBoWith()
            .reference(reference)
            .ccdCaseNumber(paymentDto.getCcdCaseNumber())
            .accountNumber(paymentDto.getAccountNumber())
            .amount(paymentDto.getAmount())
            .caseReference(paymentDto.getCaseReference())
            .currency(paymentDto.getCurrency())
            .customerReference(paymentDto.getCustomerReference())
            .description(paymentDto.getDescription())
            .organisationName(paymentDto.getOrganisationName())
            .service(paymentDto.getService())
            .build();
    }
}