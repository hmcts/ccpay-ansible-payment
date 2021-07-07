package uk.gov.hmcts.payment.api.dto.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.contract.CasePaymentOrderDto;
import uk.gov.hmcts.payment.api.contract.CasePaymentOrdersDto;
import uk.gov.hmcts.payment.casepaymentorders.client.dto.CasePaymentOrder;
import uk.gov.hmcts.payment.casepaymentorders.client.dto.CpoGetResponse;

import java.util.stream.Collectors;

@Component
public class CasePaymentOrdersMapper {

    public CasePaymentOrdersDto toCasePaymentOrdersDto(CpoGetResponse cpo) {
        return CasePaymentOrdersDto.builder()
            .content(cpo.getContent().stream()
                         .map(this::toCasePaymentOrderDto)
                         .collect(Collectors.toList()))
            .number(cpo.getNumber())
            .size(cpo.getSize())
            .totalElements(cpo.getTotalElements())
            .build();
    }

    private CasePaymentOrderDto toCasePaymentOrderDto(CasePaymentOrder cpo) {
        return CasePaymentOrderDto.builder()
            .id(cpo.getId())
            .createdTimestamp(cpo.getCreatedTimestamp())
            .caseId(cpo.getCaseId())
            .action(cpo.getAction())
            .responsibleParty(cpo.getResponsibleParty())
            .orderReference(cpo.getOrderReference())
            .build();
    }
}
