package uk.gov.hmcts.payment.api.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.payment.api.domain.mapper.OrderDomainDataEntityMapper;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "orderBoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Component
public class OrderBo {
    //-- All CRUD & Validation operations for Orders to be implemented

    private String reference;

    private String ccdCaseNumber;

    private String caseReference;

    private String orgId;

    private String enterpriseServiceName;

    private List<OrderFeeBo> fees;

    private PaymentStatus status;

    private BigDecimal orderBalance;

    @Autowired
    private OrderDomainDataEntityMapper orderDomainDataEntityMapper;

    @Autowired
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Transactional
    public Map createOrder(OrderBo orderBo) {

        PaymentFeeLink paymentFeeLinkAliasOrderEntity = orderDomainDataEntityMapper.toOrderEntity(orderBo);

        PaymentFeeLink orderSavedWithFees = paymentFeeLinkRepository.save(paymentFeeLinkAliasOrderEntity);

        Map<String, Object> orderResponseMap = new HashMap<>();
        orderResponseMap.put("order_reference", orderSavedWithFees.getPaymentReference());

        return orderResponseMap;
    }

}
