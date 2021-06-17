package uk.gov.hmcts.payment.api.domain.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.controllers.PaymentReference;
import uk.gov.hmcts.payment.api.domain.model.OrderBo;
import uk.gov.hmcts.payment.api.domain.model.OrderFeeBo;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.dto.order.OrderDto;
import uk.gov.hmcts.payment.api.dto.order.OrderFeeDto;

import java.util.stream.Collectors;

@Component
public class OrderDtoDomainMapper {

    public OrderBo toDomain(OrderDto orderDto, OrganisationalServiceDto organisationalServiceDto){

        String orderReference = PaymentReference.getInstance().getNext();

        return OrderBo.orderBoWith()
            .enterpriseServiceName(organisationalServiceDto.getServiceDescription())
            .orgId(organisationalServiceDto.getServiceCode())
            .ccdCaseNumber(orderDto.getCcdCaseNumber())
            .caseReference(orderDto.getCaseReference())
            .reference(orderReference)
            .fees(orderDto.getFees()
                .stream()
                .map(feeDto -> toFeeDomain(feeDto,orderDto.getCcdCaseNumber())) // Will be removed after get api's work without ccd dependency
                .collect(Collectors.toList()))
            .build();
    }

    public OrderFeeBo toFeeDomain(OrderFeeDto orderFeeDto, String ccdCaseNumber) {
        return OrderFeeBo.orderFeeBoWith()
            .calculatedAmount(orderFeeDto.getCalculatedAmount())
            .amountDue(orderFeeDto.getCalculatedAmount()) //amount due = calculated amount
            .code(orderFeeDto.getCode())
            .ccdCaseNumber(ccdCaseNumber)
            .version(orderFeeDto.getVersion())
            .volume(orderFeeDto.getVolume())
            .build();
    }
}
