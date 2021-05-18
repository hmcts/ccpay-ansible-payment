package uk.gov.hmcts.payment.api.domain.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.domain.model.OrderBo;
import uk.gov.hmcts.payment.api.domain.model.OrderFeeBo;
import uk.gov.hmcts.payment.api.model.CaseDetails;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.stream.Collectors;

@Component
public class OrderDomainDataEntityMapper {

    public PaymentFeeLink toOrderEntity(OrderBo orderBo) {

        return PaymentFeeLink.paymentFeeLinkWith()
            .orgId(orderBo.getOrgId())
            .enterpriseServiceName(orderBo.getEnterpriseServiceName())
            .paymentReference(orderBo.getReference())
            .caseDetails(new HashSet<>())
            .ccdCaseNumber(orderBo.getCcdCaseNumber()) // Will be removed after get api's work without ccd dependency
            .fees(orderBo.getFees()
                .stream()
                .map(feeBo -> toPaymentFeeEntity(feeBo)) // Will be removed after get api's work without ccd dependency
                .collect(Collectors.toList()))
            .build();
    }

    public CaseDetails toCaseDetailsEntity(OrderBo orderBo) {

        return CaseDetails.caseDetailsWith()
            .caseReference(orderBo.getCaseReference())
            .ccdCaseNumber(orderBo.getCcdCaseNumber())
            .build();
    }

    public PaymentFee toPaymentFeeEntity(OrderFeeBo orderFeeBo) {

        return PaymentFee.feeWith()
            .calculatedAmount(orderFeeBo.getCalculatedAmount())
            .amountDue(orderFeeBo.getAmountDue())
            .code(orderFeeBo.getCode())
            .ccdCaseNumber(orderFeeBo.getCcdCaseNumber()) // Will be removed after get api's work without ccd dependency
            .version(orderFeeBo.getVersion())
            .volume(orderFeeBo.getVolume())
            .dateCreated(new Timestamp(System.currentTimeMillis()))
            .build();
    }

}
