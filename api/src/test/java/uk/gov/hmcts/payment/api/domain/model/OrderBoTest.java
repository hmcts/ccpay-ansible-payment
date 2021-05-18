package uk.gov.hmcts.payment.api.domain.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.domain.mapper.OrderDomainDataEntityMapper;
import uk.gov.hmcts.payment.api.dto.order.OrderFeeDto;
import uk.gov.hmcts.payment.api.model.CaseDetails;
import uk.gov.hmcts.payment.api.model.CaseDetailsRepository;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class OrderBoTest {

    @InjectMocks
    private OrderBo orderBo;

    @Mock
    private CaseDetailsRepository caseDetailsRepository;

    @Mock
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Spy
    private OrderDomainDataEntityMapper orderDomainDataEntityMapper;

    @Test
    public void CreateOrderWithExistingCcdCaseNumber() throws Exception {

        String orderReference = "2200-1619524583862";

        OrderBo orderBoDomain = getOrderBoDomain(orderReference);

        when(caseDetailsRepository.findByCcdCaseNumber(anyString())).thenReturn(getCaseDetails());

        when(paymentFeeLinkRepository.save(any())).thenReturn(getPaymentFeeLink());

        Map<String,Object> orderReferenceResult =  orderBo.createOrder(orderBoDomain);

        assertThat(orderReference).isEqualTo(orderReferenceResult.get("order_reference"));

    }

    @Test
    public void CreateOrderWithNewCcdCaseNumber() throws Exception {

        String orderReference = "2200-1619524583862";

        OrderBo orderBoDomain = getOrderBoDomain(orderReference);

        when(caseDetailsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.empty());

        when(paymentFeeLinkRepository.save(any())).thenReturn(getPaymentFeeLink());

        Map<String,Object> orderReferenceResult =  orderBo.createOrder(orderBoDomain);

        assertThat(orderReference).isEqualTo(orderReferenceResult.get("order_reference"));

    }


    private OrderBo getOrderBoDomain(String orderReference) {
        return OrderBo.orderBoWith()
            .enterpriseServiceName("DIVORCE")
            .orgId("AA001")
            .ccdCaseNumber("8696869686968696")
            .caseReference("rertyuilkjhcxdfgh")
            .reference(orderReference)
            .fees(Collections.singletonList(getOrderFee())
                .stream()
                .map(feeDto -> toFeeDomain(feeDto, "8696869686968696")) // Will be removed after get api's work without ccd dependency
                .collect(Collectors.toList()))
            .build();
    }


    private Optional<CaseDetails> getCaseDetails() {
        return Optional.ofNullable(CaseDetails.caseDetailsWith()
            .caseReference("rertyuilkjhcxdfgh")
            .ccdCaseNumber("8696869686968696")
            .build());
    }


    private PaymentFeeLink getPaymentFeeLink() {
        return PaymentFeeLink.paymentFeeLinkWith()
            .orgId("AA001")
            .enterpriseServiceName("DIVORCE")
            .paymentReference("2200-1619524583862")
            .caseDetails(new HashSet<>())
            .ccdCaseNumber("8696869686968696")
            .fees(Collections.singletonList(getFee()))
            .build();
    }


    private PaymentFee getFee() {
        return PaymentFee.feeWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .build();
    }


    public OrderFeeBo toFeeDomain(OrderFeeDto orderFeeDto, String ccdCaseNumber) {
        return OrderFeeBo.orderFeeBoWith()
            .calculatedAmount(orderFeeDto.getCalculatedAmount())
            .code(orderFeeDto.getCode())
            .ccdCaseNumber(ccdCaseNumber)
            .version(orderFeeDto.getVersion())
            .volume(orderFeeDto.getVolume())
            .build();
    }

    private OrderFeeDto getOrderFee() {
        return OrderFeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .build();
    }
}
