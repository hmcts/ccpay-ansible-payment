package uk.gov.hmcts.payment.api.domain.service;

import org.ff4j.FF4j;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.ReconcilePaymentResponse;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.util.DateUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class PaymentDomainServiceTest {

    @Mock
    Payment2Repository payment2Repository;
    @Mock
    private PaymentValidator validator;
    @Mock
    private PaymentService<PaymentFeeLink, String> paymentService;
    @Mock
    private LaunchDarklyFeatureToggler featureToggler;
    @Mock
    private PaymentDtoMapper paymentDtoMapper;
    @Mock
    private FF4j ff4j;
    @Mock
    private PaymentFeeRepository paymentFeeRepository;
    @Mock
    private FeesService feesService;

    @InjectMocks
    PaymentDomainServiceImpl paymentDomainService;

    @Test
    public void testGetPaymentByApportionment(){
        Payment mockPayment = Payment.paymentWith()
                                .id(1)
                                .build();
        when(paymentService.getPaymentById(anyInt())).thenReturn(mockPayment);
        FeePayApportion payApportion = FeePayApportion.feePayApportionWith()
                                        .paymentId(1)
                                        .build();
        Payment payment = paymentDomainService.getPaymentByApportionment(payApportion);
        assertThat(payment.getId()).isEqualTo(1);
    }

    @Test
    public void testGetPaymentByReference(){
        Payment mockPayment = Payment.paymentWith()
            .id(1)
            .reference("reference")
            .build();
        when(paymentService.findSavedPayment(anyString())).thenReturn(mockPayment);
        Payment payment = paymentDomainService.getPaymentByReference("reference");
        assertThat(payment.getId()).isEqualTo(1);
        assertThat(payment.getReference()).isEqualTo("reference");
    }



    @Test(expected = PaymentException.class)
    public void testRetrievePaymentsThrowsPaymentSearchNotAvailableException(){
        Optional<String> startDataString = Optional.of("2021-06-02T08:22:42");
        Optional<String> endDataString = Optional.of("2021-06-03T08:22:42");
        Optional<String> paymentMethod = Optional.of("cash");
        Optional<String> serviceType = Optional.of("Divorce");
        String pbaNumber = "pbaNumber";
        String ccdCaseNumber = "ccdCaseNumber";
        when(ff4j.check(anyString())).thenReturn(false);
        paymentDomainService.retrievePayments(startDataString, endDataString,paymentMethod ,serviceType, pbaNumber, ccdCaseNumber);
    }

    @Test
    public void testRetrievePaymentReturnsSuccessfulResponse(){
        Optional<String> startDataString = Optional.of("2021-06-02T08:22:42");
        Optional<String> endDataString = Optional.of("2021-06-03T08:22:42");
        Optional<String> paymentMethod = Optional.of("cash");
        Optional<String> serviceType = Optional.of("Divorce");
        String pbaNumber = "pbaNumber";
        String ccdCaseNumber = "ccdCaseNumber";
        when(ff4j.check(anyString())).thenReturn(true);
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(true);
        PaymentFee paymentFee = PaymentFee.feeWith()
            .id(1)
            .feeAmount(BigDecimal.valueOf(100))
            .calculatedAmount(BigDecimal.valueOf(100))
            .code("FEE002")
            .version("1")
            .ccdCaseNumber("ccd-case-number")
            .reference("reference")
            .volume(1)
            .build();
        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith().fees(Arrays.asList(paymentFee)).ccdCaseNumber("ccdCaseNumber").enterpriseServiceName("Divorce").build();
        List<Payment> mockPayments = Arrays.asList(Payment.paymentWith().paymentStatus(PaymentStatus.CREATED).paymentMethod(PaymentMethod.paymentMethodWith().name("cash").build()).paymentLink(paymentFeeLink).build());
        when(paymentService.searchByCriteria(any(PaymentSearchCriteria.class))).thenReturn(mockPayments);
        ReconcilePaymentResponse reconcilePaymentResponse = paymentDomainService.retrievePayments(startDataString, endDataString,paymentMethod ,serviceType, pbaNumber, ccdCaseNumber);
        assertThat(reconcilePaymentResponse.getPayments().get(0).getCcdCaseNumber()).isEqualTo("ccdCaseNumber");
    }


}
