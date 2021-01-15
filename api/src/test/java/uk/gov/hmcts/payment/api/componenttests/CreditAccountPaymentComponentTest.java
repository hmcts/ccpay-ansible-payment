package uk.gov.hmcts.payment.api.componenttests;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.v1.componenttests.TestUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CreditAccountPaymentComponentTest extends TestUtil {

    private final static String PAYMENT_REFERENCE_REFEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    @Autowired
    private Payment2Repository paymentRepository;

    @Test
    public void createCreditAccountPaymentTest() throws Exception {
        List<PaymentFee> fees = Arrays.asList(getFee());
        List<Payment> payments = new ArrayList<>(3);
        payments.add(getPayment(1));
        payments.add(getPayment(2));
        payments.add(getPayment(3));

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2018-1234567890")
            .payments(payments).fees(fees).build();

        paymentFeeLinkRepository.save(paymentFeeLink);
    }

    @Test
    public void retrieveCreditAccountPayment_byPaymentGroupReferenceTest() throws Exception {
        List<PaymentFee> fees = Arrays.asList(getFee());
        List<Payment> payments = new ArrayList<>(3);
        payments.add(getPayment(1));
        payments.add(getPayment(2));
        payments.add(getPayment(3));

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2018-1234567891")
            .payments(payments).fees(fees).build();

        paymentFeeLinkRepository.save(paymentFeeLink);

        //retrieve the payment
        PaymentFeeLink result = paymentFeeLinkRepository.findByPaymentReference("2018-1234567891").orElseThrow(PaymentNotFoundException::new);
        assertNotNull(result);
        assertEquals(result.getPayments().size(), 3);
        assertEquals(result.getPaymentReference(), "2018-1234567891");
        assertEquals(result.getFees().size(), 1);
        assertEquals(result.getFees().get(0).getCode(), "X0123");
        assertTrue(result.getPayments().get(1).getReference().matches(PAYMENT_REFERENCE_REFEX));
    }


    private Payment getPayment(int number) {
        return Payment.paymentWith()
            .amount(new BigDecimal("6000.00"))
            .reference("RC-1234-1234-1234-111" + number)
            .description("description_" + number)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .ccdCaseNumber("ccdCaseNo_" + number)
            .caseReference("caseRef_" + number)
            .currency("GBP")
            .siteId("AA_00" + number)
            .serviceType("Probate")
            .customerReference("customerRef_" + number)
            .organisationName("organistation_" + number)
            .pbaNumber("pbaNumber_" + number)
            .build();
    }


    private PaymentFee getFee() {
        return PaymentFee.feeWith()
            .calculatedAmount(new BigDecimal("10000.00"))
            .code("X0123")
            .version("1")
            .build();

    }
}
