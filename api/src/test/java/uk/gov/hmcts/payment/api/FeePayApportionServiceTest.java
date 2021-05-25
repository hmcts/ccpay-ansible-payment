package uk.gov.hmcts.payment.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.dto.FeePayApportionCCDCase;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.v1.componenttests.TestUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.payment.api.model.Payment.paymentWith;
import static uk.gov.hmcts.payment.api.model.PaymentFee.feeWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class FeePayApportionServiceTest extends TestUtil {

    @Autowired
    protected FeePayApportionService feePayApportionService;

    private PaymentsDataUtil paymentsDataUtil;

    @MockBean
    private FeePayApportionRepository feePayApportionRepository;

    @MockBean
    private PaymentFeeRepository paymentFeeRepository;

    @Test(expected = PaymentException.class)
    public void updateFeeAmountDueTest() {
        Payment payment = paymentWith().amount(BigDecimal.valueOf(10000).movePointRight(2)).reference("reference1").description("desc1").returnUrl("returnUrl1")
            .ccdCaseNumber("ccdCaseNo1").caseReference("caseRef1").serviceType("cmc").currency("GBP")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .build();
        PaymentFee fee = feeWith().code("FEE0111").version("1").build();

        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith().paymentReference("00000005")
            .payments(Arrays.asList(payment))
            .fees(Arrays.asList(fee))
            .build());
        Mockito.when(feePayApportionRepository.findByPaymentId(Mockito.any())).thenThrow(new RuntimeException("DB Exception"));
        feePayApportionService.updateFeeAmountDue(paymentFeeLink.getPayments().get(0));
    }

    @Test(expected = PaymentException.class)
    public void processApportionTest() {
        Payment payment = paymentWith().amount(BigDecimal.valueOf(10000).movePointRight(2)).reference("reference1").description("desc1").returnUrl("returnUrl1")
            .ccdCaseNumber("ccdCaseNo1").caseReference("caseRef1").serviceType("cmc").currency("GBP")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .build();
        PaymentFee fee = feeWith().code("FEE0111").version("1").build();

        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith().paymentReference("00000005")
            .payments(Arrays.asList(payment))
            .fees(Arrays.asList(fee))
            .build());
        Mockito.when(paymentFeeRepository.findByCcdCaseNumber(Mockito.any())).thenThrow(new RuntimeException("DB Exception"));
        feePayApportionService.processApportion(paymentFeeLink.getPayments().get(0));
    }

    @Test(expected = PaymentException.class)
    public void processFeePayApportionTest() {
        Payment payment = paymentWith().amount(BigDecimal.valueOf(10000).movePointRight(2)).reference("reference1").description("desc1").returnUrl("returnUrl1")
            .ccdCaseNumber("ccdCaseNo1").caseReference("caseRef1").serviceType("cmc").currency("GBP")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .build();
        PaymentFee fee = feeWith().code("FEE0111").version("1").build();

        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith().paymentReference("00000005")
            .payments(Arrays.asList(payment))
            .fees(Arrays.asList(fee))
            .build());

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo("1234234534565678")
            .feePayGroups(Collections.singletonList(paymentFeeLink))
            .payments(Collections.singletonList(paymentFeeLink.getPayments().get(0)))
            .build();
        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);
    }
}
