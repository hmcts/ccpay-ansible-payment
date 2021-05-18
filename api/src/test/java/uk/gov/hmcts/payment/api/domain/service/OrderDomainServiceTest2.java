package uk.gov.hmcts.payment.api.domain.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.payment.api.domain.mapper.OrderDtoDomainMapper;
import uk.gov.hmcts.payment.api.domain.model.OrderBo;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.dto.order.OrderDto;
import uk.gov.hmcts.payment.api.dto.order.OrderFeeDto;
import uk.gov.hmcts.payment.api.service.ReferenceDataServiceImpl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class OrderDomainServiceTest2 {

    @InjectMocks
    private OrderDomainServiceImpl orderDomainService;

    @Mock
    private ReferenceDataServiceImpl referenceDataService;

    @Spy
    private OrderDtoDomainMapper orderDtoDomainMapper;

    @Mock
    private OrderBo orderBo;

    @Before
    public void setup() {

    }

    @Test
    public void createOrderWithValidRequest() throws Exception {

        OrderDto orderDto = OrderDto.orderDtoWith()
            .caseReference("123245677")
            .caseType("ClaimCase")
            .ccdCaseNumber("8689869686968696")
            .fees(Collections.singletonList(getOrderFee()))
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        MultiValueMap<String, String> header = new LinkedMultiValueMap<String, String>();

        when(referenceDataService.getOrganisationalDetail(any(), any())).thenReturn(organisationalServiceDto);

        String orderReference = "2200-1619524583862";
        Map<String, Object> orderResponse = new HashMap<>();
        orderResponse.put("order_reference",orderReference);

        doReturn(orderResponse).when(orderBo).createOrder(any());

        Map orderReferenceResult = orderDomainService.create(orderDto, header);

        assertThat(orderReference).isEqualTo(orderReferenceResult.get("order_reference"));

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
