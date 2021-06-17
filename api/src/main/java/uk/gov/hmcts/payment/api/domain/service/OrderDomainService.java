package uk.gov.hmcts.payment.api.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.payment.api.domain.model.OrderPaymentBo;
import uk.gov.hmcts.payment.api.dto.order.OrderDto;
import uk.gov.hmcts.payment.api.dto.order.OrderPaymentDto;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;

import java.util.List;
import java.util.Map;

public interface OrderDomainService {

    PaymentFeeLink find(String orderReference);

    List<PaymentFeeLink> findByCcdCaseNumber(String ccdCaseNumber);

    Map create(OrderDto orderDto, MultiValueMap<String, String> headers);

    OrderPaymentBo addPayments(PaymentFeeLink order, OrderPaymentDto orderPaymentDto) throws CheckDigitException;

    PaymentFeeLink businessValidationForOrders(PaymentFeeLink order, OrderPaymentDto orderPaymentDto);

    ResponseEntity createIdempotencyRecord(ObjectMapper objectMapper, String idempotencyKey, String orderReference,
                                           String responseJson, ResponseEntity<?> responseEntity, OrderPaymentDto orderPaymentDto) throws JsonProcessingException;

    Boolean isDuplicate(String orderReference);
}
