package uk.gov.hmcts.payment.api.service;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class UserAwareDelegatingCreditAccountPaymentServiceTest {

    @Mock
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Mock
    private PaymentStatusRepository paymentStatusRepository;

    @Mock
    private PaymentChannelRepository paymentChannelRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private Payment2Repository paymentRespository;

    @Mock
    private ReferenceUtil referenceUtil;

    @Mock
    private ServiceIdSupplier serviceIdSupplier;
    @Mock
    private UserIdSupplier userIdSupplier;

    @Mock
    private OrderCaseUtil orderCaseUtil;

    @InjectMocks
    private UserAwareDelegatingCreditAccountPaymentService creditAccountPaymentService;

    private final static String PAYMENT_METHOD = "payment by account";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createCreditAccountPaymentTest() throws Exception {
        when(userIdSupplier.get()).thenReturn("aUser");
        when(referenceUtil.getNext("RC")).thenReturn("RC-1234-1234-1234-1111");
        String reference = referenceUtil.getNext("RC");

        List<Payment> payments = Arrays.asList(getPayment(1, reference));
        List<PaymentFee> fees = Arrays.asList(getFee(1));

        List<StatusHistory> statusHistories = Arrays.asList(StatusHistory.statusHistoryWith().status("pending").build());
        payments.stream().forEach(p -> p.setStatusHistories(statusHistories));

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2018-1234567890")
            .payments(payments)
            .fees(fees)
            .build();

        when(paymentStatusRepository.findByNameOrThrow("success")).thenReturn(PaymentStatus.paymentStatusWith().name("success").build());

        when(orderCaseUtil.enhanceWithOrderCaseDetails(any(PaymentFeeLink.class), any(Payment.class))).thenReturn(paymentFeeLink);
        when(paymentFeeLinkRepository.save(paymentFeeLink)).thenReturn(PaymentFeeLink.paymentFeeLinkWith()
            .id(1)
            .paymentReference("2018-1234567890")
            .payments(Arrays.asList(Payment.paymentWith()
                .id(1)
                .reference(reference)
                .amount(new BigDecimal("6000.00"))
                .build()))
            .fees(Arrays.asList(PaymentFee.feeWith()
                .id(1)
                .calculatedAmount(new BigDecimal("6000.00"))
                .code("X0001")
                .version("1")
                .build()))
            .build());


        creditAccountPaymentService.create(payments.get(0), fees, "2018-1234567890");
    }

    @Test
    public void retrieveCreditAccountPayment_ByReferenceTest() throws Exception {
        when(referenceUtil.getNext("RC")).thenReturn("RC-1234-1234-1234-1111");
        String reference = referenceUtil.getNext("RC");

        List<Payment> payments = Arrays.asList(getPayment(1, reference));
        payments.stream().forEach(p -> {
            p.setId(1);
            p.setPaymentChannel(PaymentChannel.paymentChannelWith().name("online").build());
            p.setPaymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build());
            p.setPaymentStatus(PaymentStatus.paymentStatusWith().name("pending").build());
        });
        List<PaymentFee> fees = Arrays.asList(getFee(1));

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .id(1)
            .paymentReference("2018-1234567890")
            .payments(payments)
            .fees(fees)
            .build();

        when(paymentRespository.findByReferenceAndPaymentMethod(reference, PaymentMethod.paymentMethodWith().name(PAYMENT_METHOD).build()))
            .thenReturn(Optional.of(Payment.paymentWith()
                .id(1)
                .reference(reference)
                .amount(new BigDecimal("6000.00"))
                .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
                .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
                .paymentStatus(PaymentStatus.paymentStatusWith().name("pending").build())
                .paymentLink(paymentFeeLink)
                .build()));

        PaymentFeeLink result = creditAccountPaymentService.retrieveByPaymentReference(reference);
        assertNotNull(result);
        assertEquals(result.getPaymentReference(), "2018-1234567890");
        result.getPayments().stream().forEach(p -> {
            assertEquals(p.getReference(), reference);
            assertEquals(p.getAmount(), new BigDecimal("6000.00"));
        });
    }


    @Test(expected = PaymentNotFoundException.class)
    public void retrieveCreditAccountPayment_byIncorrectPaymentReference_shouldThrowExceptionTest() throws Exception {
        when(referenceUtil.getNext("RC")).thenReturn("RC-1234-1234-1234-1111");
        String reference = referenceUtil.getNext("RC");

        List<Payment> payments = Arrays.asList(getPayment(1, reference));
        List<PaymentFee> fees = Arrays.asList(getFee(1));

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .id(1)
            .paymentReference("2018-1234567890")
            .payments(payments)
            .fees(fees)
            .build();

        when(paymentRespository.findByReferenceAndPaymentMethod("RC-1234-1234-1234-1112", PaymentMethod.paymentMethodWith().name(PAYMENT_METHOD).build()))
            .thenThrow(new PaymentNotFoundException());
        creditAccountPaymentService.retrieveByPaymentReference("RC-1234-1234-1234-1112");
    }

    private Payment getPayment(int number, String reference) throws CheckDigitException {

        return Payment.paymentWith()
            .amount(new BigDecimal("6000.00"))
            .reference(reference)
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
            .paymentStatus(PaymentStatus.paymentStatusWith().name("success").build())
            .build();
    }


    private PaymentFee getFee(int number) {
        return PaymentFee.feeWith()
            .calculatedAmount(new BigDecimal("6000.00"))
            .code("X0123")
            .version("1")
            .build();

    }
}
