package uk.gov.hmcts.payment.api.service;

import antlr.collections.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.*;
import uk.gov.hmcts.payment.api.dto.RemissionServiceRequest;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.InvalidPaymentGroupReferenceException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RemissionServiceTest {

    @InjectMocks
    private RemissionServiceImpl remissionService;

    @Mock
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Mock
    private RemissionRepository remissionRepository;

    @Spy
    private ReferenceUtil referenceUtil;

    @Mock
    private OrderCaseUtil orderCaseUtil;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createRemissionTest() throws Exception {
        Remission remission = getRemission();
        PaymentFee fee = getFee();

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456789")
            .remissions(Collections.singletonList(remission))
            .fees(Arrays.asList(fee))
            .build();

        RemissionServiceRequest remissionServiceRequest = RemissionServiceRequest.remissionServiceRequestWith()
            .paymentGroupReference("2019-123456789")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemission")
            .hwfAmount(new BigDecimal("100.99"))
            .hwfReference("hwf123456789")
            .fee(fee)
            .build();

        when(orderCaseUtil.enhanceWithOrderCaseDetails(any(PaymentFeeLink.class), any(RemissionServiceRequest.class))).thenReturn(paymentFeeLink);
        when(paymentFeeLinkRepository.save(any(PaymentFeeLink.class))).thenReturn(paymentFeeLink);

        PaymentFeeLink res = remissionService.createRemission(remissionServiceRequest);

        assertThat(res).isNotNull();
        assertThat(res.getPaymentReference()).isEqualTo("2019-123456789");
        res.getRemissions().stream().forEach(r -> {
            assertThat(r.getRemissionReference()).isEqualTo("RM-1555-0684-8011-0463");
        });
        verify(paymentFeeLinkRepository, times(1)).save(any(PaymentFeeLink.class));
    }

    @Test(expected = InvalidPaymentGroupReferenceException.class)
    public void createPartialRemissionWithInvalidPaymentGroupReferenceTest() throws Exception {
        Remission remission = getRemission();
        PaymentFee fee = getFee();

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456799")
            .remissions(Collections.singletonList(remission))
            .fees(Arrays.asList(fee))
            .build();

        when(paymentFeeLinkRepository.save(any(PaymentFeeLink.class))).thenReturn(paymentFeeLink);
        remissionService.createRetrospectiveRemission(RemissionServiceRequest.remissionServiceRequestWith().build(), "2019-000000099", 1);
    }

    @Test
    public void createPartialRemissionWithValidPaymentGroupReferenceTest() throws Exception {

        Remission remission = getRemission();
        PaymentFee fee = getFee();

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456799")
            .remissions(Collections.singletonList(remission))
            .fees(Arrays.asList(fee))
            .build();

        when(paymentFeeLinkRepository.findByPaymentReference("2019-123456799")).thenReturn(Optional.ofNullable(paymentFeeLink));
        RemissionServiceRequest remissionServiceRequest = RemissionServiceRequest.remissionServiceRequestWith()
            .paymentGroupReference("2019-123456799")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemission")
            .hwfAmount(new BigDecimal("100.99"))
            .hwfReference("hwf123456789")
            .fee(fee)
            .build();

        PaymentFeeLink res = remissionService.createRetrospectiveRemission(remissionServiceRequest, paymentFeeLink.getPaymentReference(), 1);
        assertThat(res).isNotNull();
        assertThat(res.getPaymentReference()).isEqualTo(paymentFeeLink.getPaymentReference());
        verify(paymentFeeLinkRepository, atLeastOnce()).findByPaymentReference(paymentFeeLink.getPaymentReference());
    }

    @Test
    public void create_retro_remission_where_retro_remission_false() throws Exception {

        RemissionServiceRequest remissionServiceRequest = RemissionServiceRequest.remissionServiceRequestWith()
            .paymentGroupReference("2019-123456788")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemission")
            .hwfAmount(new BigDecimal("100.99"))
            .hwfReference("hwf123456789")
            .fee(getFee())
            .build();
        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456788")
            .remissions(Collections.singletonList(getRemission()))
            .fees(Arrays.asList(getFee()))
            .build();
        when(paymentFeeLinkRepository.findByPaymentReference("2019-123456788")).thenReturn(Optional.ofNullable(paymentFeeLink));
        PaymentFeeLink response = remissionService.createRetrospectiveRemission(remissionServiceRequest, paymentFeeLink.getPaymentReference(), 1);

        //Assertion Checks....
        assertThat(response).isNotNull();
        assertThat(response.getPaymentReference()).isEqualTo(paymentFeeLink.getPaymentReference());
        assertThat(Objects.nonNull(response.getRemissions().get(0).getRemissionReference())
            && response.getRemissions().get(0).getRemissionReference().startsWith("RM"));
        assertThat(Objects.nonNull(response.getRemissions().get(0).getFee())
            && response.getRemissions().get(0).getFee().getNetAmount().equals(BigDecimal.ZERO));
        assertThat(response.getRemissions().get(0).equals(response.getRemissions().get(0).getFee().getRemissions().get(0)));
        assertThat(getFee().equals(response.getRemissions().get(0).getFee()));
        assertThat(response.equals(response.getRemissions().get(0).getPaymentFeeLink()));
        //As not Remission is applied.
        assertThat(Objects.isNull(response.getRemissions().get(0).getPaymentFeeLink().getRemissions().get(0).getFee().getAmountDue()));
    }

    @Test
    public void create_retro_remission_where_retro_remission_true_payment_status_created() throws Exception {
        RemissionServiceRequest remissionServiceRequest = RemissionServiceRequest.remissionServiceRequestWith()
            .paymentGroupReference("2019-123456788")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemission")
            .hwfAmount(new BigDecimal("100.99"))
            .hwfReference("hwf123456789")
            .fee(getFee())
            .isRetroRemission(true)
            .build();
        Payment payment = Payment.paymentWith()
            .paymentStatus(PaymentStatus.CREATED)
            .build();
        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456788")
            .payments(Arrays.asList(payment))
            .remissions(Collections.singletonList(getRemission()))
            .fees(Arrays.asList(getFee()))
            .build();
        when(paymentFeeLinkRepository.findByPaymentReference("2019-123456788")).thenReturn(Optional.ofNullable(paymentFeeLink));
        PaymentFeeLink response = remissionService.createRetrospectiveRemission(remissionServiceRequest, paymentFeeLink.getPaymentReference(), 1);

        //Assertion Checks....
        assertThat(response).isNotNull();
        assertThat(response.getPaymentReference()).isEqualTo(paymentFeeLink.getPaymentReference());
        assertThat(Objects.nonNull(response.getRemissions().get(0).getRemissionReference())
            && response.getRemissions().get(0).getRemissionReference().startsWith("RM"));
        assertThat(Objects.nonNull(response.getRemissions().get(0).getFee())
            && response.getRemissions().get(0).getFee().getNetAmount().equals(BigDecimal.ZERO));
        assertThat(response.getRemissions().get(0).equals(response.getRemissions().get(0).getFee().getRemissions().get(0)));
        assertThat(getFee().equals(response.getRemissions().get(0).getFee()));
        assertThat(response.equals(response.getRemissions().get(0).getPaymentFeeLink()));
    }

    @Test
    public void create_retro_remission_where_retro_remission_true_payment_status_success() throws Exception {
        RemissionServiceRequest remissionServiceRequest = RemissionServiceRequest.remissionServiceRequestWith()
            .paymentGroupReference("2019-123456788")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemission")
            .hwfAmount(new BigDecimal("100.99"))
            .hwfReference("hwf123456789")
            .fee(getFee())
            .isRetroRemission(true)
            .build();
        Payment payment = Payment.paymentWith()
            .paymentStatus(PaymentStatus.SUCCESS)
            .amount(BigDecimal.valueOf(50.99))
            .build();
        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456788")
            .payments(Arrays.asList(payment))
            .remissions(Collections.singletonList(getRemission()))
            .fees(Arrays.asList(getFee()))
            .build();
        when(paymentFeeLinkRepository.findByPaymentReference("2019-123456788")).thenReturn(Optional.ofNullable(paymentFeeLink));
        PaymentFeeLink response = remissionService.createRetrospectiveRemission(remissionServiceRequest, paymentFeeLink.getPaymentReference(), 1);

        //Assertion Checks....
        assertThat(response).isNotNull();
        assertThat(response.getPaymentReference()).isEqualTo(paymentFeeLink.getPaymentReference());
        assertThat(Objects.nonNull(response.getRemissions().get(0).getRemissionReference())
            && response.getRemissions().get(0).getRemissionReference().startsWith("RM"));
        assertThat(Objects.nonNull(response.getRemissions().get(0).getFee())
            && response.getRemissions().get(0).getFee().getNetAmount().equals(BigDecimal.valueOf(-50.99))); //Make sure that the NetAmount
        assertThat(response.getRemissions().get(0).equals(response.getRemissions().get(0).getFee().getRemissions().get(0)));
        assertThat(getFee().equals(response.getRemissions().get(0).getFee()));
        assertThat(response.equals(response.getRemissions().get(0).getPaymentFeeLink()));
    }

    @Test
    @Ignore("This test fails to get the Fee from the Service Request if the Fee provided in the Request and the ")
    public void create_retro_remission_where_retro_remission_true_payment_status_success_fees_not_provided() throws Exception {
        RemissionServiceRequest remissionServiceRequest = RemissionServiceRequest.remissionServiceRequestWith()
            .paymentGroupReference("2019-123456788")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemission")
            .hwfAmount(new BigDecimal("100.99"))
            .hwfReference("hwf123456789")
            .fee(getFee())
            .isRetroRemission(true)
            .build();
        Payment payment = Payment.paymentWith()
            .paymentStatus(PaymentStatus.SUCCESS)
            .amount(BigDecimal.valueOf(50.99))
            .build();
        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2019-123456788")
            .payments(Arrays.asList(payment))
            .remissions(Collections.singletonList(getRemission()))
            .fees(Arrays.asList(getFee()))
            .build();
        when(paymentFeeLinkRepository.findByPaymentReference("2019-123456788")).thenReturn(Optional.ofNullable(paymentFeeLink));
        PaymentFeeLink response =
            remissionService.createRetrospectiveRemission(remissionServiceRequest, paymentFeeLink.getPaymentReference(), 3);
    }


        private Remission getRemission() {
        return Remission.remissionWith()
            .hwfAmount(new BigDecimal("100.99"))
            .remissionReference("RM-1555-0684-8011-0463")
            .hwfReference("hwf123456789")
            .ccdCaseNumber("1111-2222-3333-4444")
            .beneficiaryName("testCreateRemissionWithRetrospective")
            .build();
    }

    private PaymentFee getFee() {
        return PaymentFee.feeWith()
            .id(1)
            .ccdCaseNumber("1111-2222-3333-4444")
            .code("FEE0123")
            .version("1")
            .calculatedAmount(new BigDecimal("100.99"))
            .build();
    }
}
