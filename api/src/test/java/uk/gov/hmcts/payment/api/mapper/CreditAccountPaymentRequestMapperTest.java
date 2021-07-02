package uk.gov.hmcts.payment.api.mapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CreditAccountPaymentRequestMapperTest {

    @Mock
    CreditAccountDtoMapper creditAccountDtoMapper;

    @InjectMocks
    CreditAccountPaymentRequestMapper creditAccountPaymentRequestMapper = new CreditAccountPaymentRequestMapper();

    CreditAccountPaymentRequest creditAccountPaymentRequest;

    @Before
    public void initiate(){
        List<FeeDto> fees = new ArrayList<>();
        FeeDto fee = FeeDto.feeDtoWith().ccdCaseNumber("ccd-case-number").build();
        fees.add(fee);
        creditAccountPaymentRequest = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("100.00"))
            .description("description")
            .ccdCaseNumber("ccd-case-number-1")
            .caseReference("case-reference")
            .fees(fees)
            .currency(CurrencyCode.GBP).service("CMC").customerReference("reference").organisationName("org-name")
            .accountNumber("acc-number").siteId("site-id").build();
    }

    @Test
    public void testMapPBARequest(){
        Payment payment = creditAccountPaymentRequestMapper.mapPBARequest(creditAccountPaymentRequest);
        assertEquals("ccd-case-number-1",payment.getCcdCaseNumber());
        assertEquals("case-reference",payment.getCaseReference());
    }

    @Test
    public void testMapPBAFeesFromRequest(){
        PaymentFee fee = PaymentFee.feeWith().code("FEE123").feeAmount(new BigDecimal("100.00")).ccdCaseNumber("ccd-case-number").build();
        when(creditAccountDtoMapper.toFee(Mockito.any(FeeDto.class))).thenReturn(fee);
        List<PaymentFee> paymentFees = creditAccountPaymentRequestMapper.mapPBAFeesFromRequest(creditAccountPaymentRequest);
        assertEquals("ccd-case-number",paymentFees.get(0).getCcdCaseNumber());
        assertEquals("FEE123",paymentFees.get(0).getCode());
    }
}
