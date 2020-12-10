package uk.gov.hmcts.payment.api.componenttests;


import org.junit.Test;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.v1.componenttests.TestUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.gov.hmcts.payment.api.model.Payment.paymentWith;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

public class CardPaymentComponentTest extends TestUtil {

    @Test
    public void testSaveOfSinglePaymentWithSingleFee() {
        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference("00000001")
            .payments(Arrays.asList(getPaymentsData().get(0)))
            .fees(Arrays.asList(PaymentsDataUtil.getFeesData().get(2)))
            .build());

        assertNotNull(paymentFeeLink);
        assertEquals(paymentFeeLink.getPayments().size(), 1);
        assertEquals(paymentFeeLink.getFees().size(), 1);
        assertEquals(paymentFeeLink.getPayments().get(0).getAmount(), new BigDecimal(1000000));
        assertEquals(paymentFeeLink.getFees().get(0).getCode(), "X0033");
        paymentFeeLink.getPayments().get(0).getStatusHistories().stream().forEach(h -> {
            assertEquals(h.getExternalStatus(), "created");
            assertEquals(h.getStatus(), "Initiated");
        });
    }


    @Test
    public void testSaveOfSinglePaymentWithMultipleFees() {
        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference("00000002")
            .payments(Arrays.asList(getPaymentsData().get(0)))
            .fees(PaymentsDataUtil.getFeesData())
            .build());

        assertNotNull(paymentFeeLink.getId());
        assertNotNull(paymentFeeLink.getPayments());
        assertNotNull(paymentFeeLink.getFees());
        assertEquals(paymentFeeLink.getPayments().size(), 1);
        assertEquals(paymentFeeLink.getPaymentReference(), "00000002");
        assertEquals(paymentFeeLink.getPayments().get(0).getReference(), "reference1");
        assertEquals(paymentFeeLink.getFees().size(), 4);
    }

    @Test
    public void testSaveOfMutliplePaymentsWithMutlipleFees() {

        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference("00000003")
            .payments(getPaymentsData())
            .fees(PaymentsDataUtil.getFeesData())
            .build());

        assertNotNull(paymentFeeLink.getId());
        assertNotNull(paymentFeeLink.getPayments().get(0).getId());
        assertNotNull(paymentFeeLink.getFees().get(2).getId());
        assertNotNull(paymentFeeLink.getPayments().get(0).getId());
        assertEquals(paymentFeeLink.getPaymentReference(), "00000003");
        assertEquals(paymentFeeLink.getPayments().size(), 3);
        assertEquals(paymentFeeLink.getFees().size(), 4);
    }


    @Test
    public void testRetrieveCardPaymentWithPaymentReference() throws Exception {

        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith().paymentReference("00000004")
            .payments(Arrays.asList(getPaymentsData().get(2)))
            .fees(PaymentsDataUtil.getFeesData())
            .build());

        PaymentFeeLink foundPayment = paymentFeeLinkRepository.findByPaymentReference("00000004").orElseThrow(PaymentNotFoundException::new);


        Payment payment = foundPayment.getPayments().get(0);
        assertNotNull(payment.getId());
        assertEquals(payment.getAmount(), new BigDecimal(3000000));
    }

    @Test(expected = PaymentNotFoundException.class)
    public void testRetrieveCardPaymentWithNonExistingPaymentReferenceShouldThrowException() throws Exception {
        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith().paymentReference("00000005")
            .payments(Arrays.asList(getPaymentsData().get(2)))
            .fees(PaymentsDataUtil.getFeesData())
            .build());

        PaymentFeeLink foundPayment = paymentFeeLinkRepository.findByPaymentReference("00000006").orElseThrow(PaymentNotFoundException::new);
    }


    public static List<Payment> getPaymentsData() {
        List<Payment> payments = new ArrayList<>();
        payments.add(paymentWith().amount(BigDecimal.valueOf(10000).movePointRight(2)).reference("reference1").description("desc1").returnUrl("https://www.moneyclaims.service.gov.uk")
            .paymentStatus(PaymentStatus.CREATED)
            .ccdCaseNumber("ccdCaseNo1").caseReference("caseRef1").serviceType("cmc").currency("GBP")
            .serviceCallbackUrl("http://google.com")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .build());
        payments.add(paymentWith().amount(BigDecimal.valueOf(20000).movePointRight(2)).reference("reference2").description("desc2").returnUrl("https://www.moneyclaims.service.gov.uk")
            .paymentStatus(PaymentStatus.CREATED)
            .serviceCallbackUrl("http://google.com")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .ccdCaseNumber("ccdCaseNo2").caseReference("caseRef2").serviceType("divorce").currency("GBP").build());
        payments.add(paymentWith().amount(BigDecimal.valueOf(30000).movePointRight(2)).reference("reference3").description("desc3").returnUrl("https://www.moneyclaims.service.gov.uk")
            .paymentStatus(PaymentStatus.CREATED)
            .serviceCallbackUrl("http://google.com")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .ccdCaseNumber("ccdCaseNo3").caseReference("caseRef3").serviceType("probate").currency("GBP").build());
        return payments;
    }

}
