package uk.gov.hmcts.payment.api.domain.service;

import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.payment.api.domain.model.ApproverAction;
import uk.gov.hmcts.payment.api.dto.RefundDto;

import java.util.Map;

public interface RefundDomainService {

    Map create (RefundDto refundDto, String paymentReference, MultiValueMap<String, String> headers);

    Boolean action(String refundReference, ApproverAction approverAction, MultiValueMap<String, String> headers, RefundDto refundDto);

    Boolean update(RefundDto refundDto, String refundReference, MultiValueMap<String, String> headers);
}
