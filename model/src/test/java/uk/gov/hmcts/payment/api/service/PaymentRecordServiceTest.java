package uk.gov.hmcts.payment.api.service;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class PaymentRecordServiceTest {

    private static final String USER_ID = "USER_ID";
    private static final String S2S_SERVICE_NAME = "bar-api";

    @Mock
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Mock
    private PaymentStatusRepository paymentStatusRepository;

    @Mock
    private PaymentChannelRepository paymentChannelRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private UserIdSupplier userIdSupplier;
    @Mock
    private ServiceIdSupplier serviceIdSupplier;

    @Spy
    private ReferenceUtil referenceUtil;

    @InjectMocks
    private PaymentRecordServiceImpl paymentRecordService;

    @Captor
    private ArgumentCaptor<PaymentFeeLink> argumentCaptor;

    @Mock
    private OrderCaseUtil orderCaseUtil;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPopulatePaymentDetails() throws Exception {
        Payment payment = getPayment();
        List<PaymentFee> fees = Arrays.asList(getFee());

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2018-10000001")
            .payments(Arrays.asList(payment))
            .fees(fees)
            .build();

        when(orderCaseUtil.enhanceWithOrderCaseDetails(any(PaymentFeeLink.class), any(Payment.class))).thenReturn(paymentFeeLink);
        when(paymentFeeLinkRepository.save(any(PaymentFeeLink.class))).thenReturn(paymentFeeLink);
        when(paymentStatusRepository.findByNameOrThrow("pending")).thenReturn(PaymentStatus.paymentStatusWith().name("pending").build());

        PaymentFeeLink savedPayment = paymentRecordService.recordPayment(payment, fees, "2018-10000001");

        verify(paymentFeeLinkRepository).save(argumentCaptor.capture());
        assertEquals(savedPayment.getPaymentReference(), paymentFeeLink.getPaymentReference());
        savedPayment.getPayments().forEach(p -> {
            assertEquals(p.getAmount(), new BigDecimal("100.11"));
            assertEquals(p.getPaymentStatus().getName(), "pending");
            assertEquals(p.getPaymentChannel().getName(), "digital bar");
            assertEquals(p.getPaymentMethod().getName(), "cheque");
            assertEquals(p.getCaseReference(), "caseReference");
            assertEquals(p.getUserId(), USER_ID);
            assertEquals(p.getS2sServiceName(), S2S_SERVICE_NAME);
        });

        savedPayment.getFees().forEach(f -> {
            assertEquals(f.getCode(), "FEE0001");
            assertEquals(f.getCalculatedAmount(), new BigDecimal("100.11"));
            assertEquals(f.getVolume(), new Integer(1));
            assertEquals(f.getReference(), "caseReference");

        });
    }

    private Payment getPayment() throws CheckDigitException {
        when(paymentMethodRepository.findByNameOrThrow("cheque")).thenReturn(PaymentMethod.paymentMethodWith().name("cheque").build());
        when(paymentChannelRepository.findByNameOrThrow("digital bar")).thenReturn(PaymentChannel.paymentChannelWith().name("digital bar").build());
        when(paymentStatusRepository.findByNameOrThrow("pending")).thenReturn(PaymentStatus.paymentStatusWith().name("pending").build());

        when(userIdSupplier.get()).thenReturn(USER_ID);
        when(serviceIdSupplier.get()).thenReturn(S2S_SERVICE_NAME);

        return Payment.paymentWith()
            .amount(new BigDecimal("100.11"))
            .reference(referenceUtil.getNext("RC"))
            .caseReference("caseReference")
            .externalReference("chequeNumber")
            .giroSlipNo("giro")
            .userId(USER_ID)
            .s2sServiceName(S2S_SERVICE_NAME)
            .paymentMethod(paymentMethodRepository.findByNameOrThrow("cheque"))
            .paymentChannel(paymentChannelRepository.findByNameOrThrow("digital bar"))
            .paymentStatus(paymentStatusRepository.findByNameOrThrow("pending"))
            .build();
    }

    private PaymentFee getFee() {
        return PaymentFee.feeWith()
            .reference("caseReference")
            .code("FEE0001")
            .calculatedAmount(new BigDecimal("100.11"))
            .version("1")
            .volume(1)
            .build();
    }
}
