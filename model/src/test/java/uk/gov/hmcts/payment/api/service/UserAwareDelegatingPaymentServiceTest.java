package uk.gov.hmcts.payment.api.service;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.payment.api.audit.AuditRepository;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.dto.PaymentServiceRequest;
import uk.gov.hmcts.payment.api.dto.PciPalPayment;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.external.client.dto.State;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserAwareDelegatingPaymentServiceTest {
    private UserAwareDelegatingPaymentService userAwareDelegatingPaymentService;

    private UserIdSupplier userIdSupplier = mock(UserIdSupplier.class);
    private PaymentFeeLinkRepository paymentFeeLinkRepository = mock(PaymentFeeLinkRepository.class);
    private DelegatingPaymentService<GovPayPayment, String> delegateGovPay = mock(DelegatingPaymentService.class);
    private DelegatingPaymentService<PciPalPayment, String> delegatePciPal = mock(DelegatingPaymentService.class);
    private PaymentChannelRepository paymentChannelRepository = mock(PaymentChannelRepository.class);
    private PaymentMethodRepository paymentMethodRepository = mock(PaymentMethodRepository.class);
    private PaymentProviderRepository paymentProviderRepository = mock(PaymentProviderRepository.class);
    private PaymentStatusRepository paymentStatusRepository = mock(PaymentStatusRepository.class);
    private Payment2Repository paymentRespository = mock(Payment2Repository.class);
    private ReferenceUtil referenceUtil = mock(ReferenceUtil.class);
    private GovPayAuthUtil govPayAuthUtil = mock(GovPayAuthUtil.class);
    private ServiceIdSupplier serviceIdSupplier = mock(ServiceIdSupplier.class);
    private AuditRepository auditRepository = mock(AuditRepository.class);
    private CallbackService callbackService = mock(CallbackService.class);

    private FeePayApportionRepository feePayApportionRepository = mock(FeePayApportionRepository.class);
    private PaymentFeeRepository paymentFeeRepository = mock(PaymentFeeRepository.class);
    private FeePayApportionService feePayApportionService = mock(FeePayApportionService.class);
    private LaunchDarklyFeatureToggler featureToggler = mock(LaunchDarklyFeatureToggler.class);
    private OrderCaseUtil orderCaseUtil = mock(OrderCaseUtil.class);

    private final static String PAYMENT_CHANNEL_TELEPHONY = "telephony";
    private final static String PAYMENT_PROVIDER_PCI_PAL = "pci pal";

    @Before
    public void before() {
        userAwareDelegatingPaymentService = new UserAwareDelegatingPaymentService(userIdSupplier,
            paymentFeeLinkRepository, delegateGovPay, delegatePciPal, paymentChannelRepository, paymentMethodRepository,
            paymentProviderRepository, paymentStatusRepository, paymentRespository, referenceUtil, govPayAuthUtil,
            serviceIdSupplier, auditRepository, callbackService, feePayApportionRepository, paymentFeeRepository, feePayApportionService, featureToggler, orderCaseUtil);
    }

    //calls PCI_PAL service when telephony and pci pal
    @Test
    public void callsPciPalServiceCreateWhenRequest() throws CheckDigitException {
        PaymentServiceRequest paymentServiceRequest = PaymentServiceRequest.paymentServiceRequestWith()
            .provider(PAYMENT_PROVIDER_PCI_PAL)
            .channel(PAYMENT_CHANNEL_TELEPHONY)
            .build();
        PciPalPayment pciPalPaymentExpected = PciPalPayment.pciPalPaymentWith()
            .paymentId("payment-id-1234")
            .state(State.stateWith().status("success").build())
            .build();
        when(delegatePciPal.create(paymentServiceRequest)).thenReturn(pciPalPaymentExpected);
        userAwareDelegatingPaymentService.create(paymentServiceRequest);
        verify((delegatePciPal), times(1)).create(paymentServiceRequest);
    }
}
