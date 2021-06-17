package uk.gov.hmcts.payment.api.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.StatusHistory;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.payment.api.model.Payment.paymentWith;
@RunWith(SpringRunner.class)
public class ReplayCreditAccountPaymentServiceTest {

    @Mock
    Payment2Repository paymentRespository;

    @InjectMocks
    ReplayCreditAccountPaymentServiceImpl replayCreditAccountPaymentService;


    @Test
    public void testUpdatePaymentStatusByReference(){
        Payment payment = paymentWith().amount(BigDecimal.valueOf(10000).movePointRight(2)).reference("reference1").description("desc1").returnUrl("returnUrl1")
            .ccdCaseNumber("ccdCaseNo1").caseReference("caseRef1").serviceType("cmc").currency("GBP")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .build();
        Mockito.when(paymentRespository.findByReference(any(String.class))).thenReturn(java.util.Optional.ofNullable(payment));
        replayCreditAccountPaymentService.updatePaymentStatusByReference("payment-reference", PaymentStatus.SUCCESS,"message");
        verify(paymentRespository).findByReference("payment-reference");
    }
}
