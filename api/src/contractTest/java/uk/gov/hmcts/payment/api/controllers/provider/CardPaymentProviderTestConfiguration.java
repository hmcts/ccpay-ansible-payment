package uk.gov.hmcts.payment.api.controllers.provider;

import org.ff4j.FF4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.payment.api.audit.AuditRepository;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.configuration.security.AuthenticatedServiceIdSupplier;
import uk.gov.hmcts.payment.api.dto.PciPalPayment;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.external.client.GovPayClient;
import uk.gov.hmcts.payment.api.external.client.dto.CardDetails;
import uk.gov.hmcts.payment.api.model.FeePayApportionRepository;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentChannelRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.model.PaymentMethodRepository;
import uk.gov.hmcts.payment.api.model.PaymentProviderRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;
import uk.gov.hmcts.payment.api.model.TelephonyRepository;
import uk.gov.hmcts.payment.api.service.CallbackService;
import uk.gov.hmcts.payment.api.service.CardDetailsService;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.service.PaymentServiceImpl;
import uk.gov.hmcts.payment.api.service.PciPalPaymentService;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.service.UserAwareDelegatingPaymentService;
import uk.gov.hmcts.payment.api.service.govpay.GovPayDelegatingPaymentService;
import uk.gov.hmcts.payment.api.util.DateUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayKeyRepository;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;


@TestConfiguration
public class CardPaymentProviderTestConfiguration {

    @MockBean
    @Qualifier("restTemplatePaymentGroup")
    private RestTemplate restTemplate;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private ReferenceDataService referenceDataServiceImp;

    @Bean
    @Primary
    public PaymentDtoMapper paymentDtoMapper() {
        return new PaymentDtoMapper();
    }

    @Bean
    @Primary
    public UserAwareDelegatingPaymentService delegateUserPay() {
        return new UserAwareDelegatingPaymentService(userIdSupplier,
            paymentFeeLinkRepository, delegateGovPay(),
            delegatePciPal,
            paymentChannelRepository,
            paymentMethodRepository,
            paymentProviderRepository,
            paymentStatusRepository,
            paymentRespository,
            referenceUtil(),
            govPayAuthUtil,
            serviceIdSupplier(),
            auditRepository,
            callbackService,
            feePayApportionRepository,
            paymentFeeRepository,
            feePayApportionService,
            launchDarklyFeatureToggler);
    }

    @Bean
    @Primary
    public PaymentServiceImpl paymentService() {
        return new PaymentServiceImpl(delegateUserPay(),
            paymentRespository,
            callbackService,
            paymentStatusRepository,
            telephonyRepository,
            auditRepository,
            feePayApportionService,
            feePayApportionRepository,
            launchDarklyFeatureToggler);
    }

    @Bean
    @Primary
    public GovPayDelegatingPaymentService delegateGovPay() {
        return new GovPayDelegatingPaymentService(govPayKeyRepository, govPayClient, serviceIdSupplier(), govPayAuthUtil);
    }

    @Bean
    public PaymentValidator paymentValidator() {
        return new PaymentValidator(dateUtil());
    }

    @Bean
    public DateUtil dateUtil() {
        return new DateUtil();
    }

    @MockBean
    public GovPayKeyRepository govPayKeyRepository;

    @MockBean
    public GovPayClient govPayClient;

    @MockBean
    public DelegatingPaymentService<PciPalPayment, String> delegatePciPal;


    @MockBean
    public uk.gov.hmcts.payment.api.reports.FeesService feeService;

    @MockBean
    public uk.gov.hmcts.fees2.register.data.service.FeeService feeService2;

    @MockBean
    public LaunchDarklyFeatureToggler launchDarklyFeatureToggler;
    @MockBean
    public UserIdSupplier userIdSupplier;
    @MockBean
    public PaymentFeeLinkRepository paymentFeeLinkRepository;
    @MockBean
    public PaymentFeeRepository paymentFeeRepository;
    @MockBean
    public PaymentStatusRepository paymentStatusRepository;
    @MockBean
    public PaymentChannelRepository paymentChannelRepository;
    @MockBean
    public PaymentProviderRepository paymentProviderRepository;
    @MockBean
    public PaymentMethodRepository paymentMethodRepository;
    @MockBean
    public Payment2Repository paymentRespository;
    @MockBean
    public CardDetailsService<CardDetails, String> cardDetailsService;
    @MockBean
    public PciPalPaymentService pciPalPaymentService;
    @MockBean
    public FF4j ff4j;
    @MockBean
    FeePayApportionService feePayApportionService;

    @Bean
    @Primary
    public ReferenceUtil referenceUtil() {
        return new ReferenceUtil();
    }

    @MockBean
    public GovPayAuthUtil govPayAuthUtil;

    @Bean
    @Primary
    public ServiceIdSupplier serviceIdSupplier() {
        return new AuthenticatedServiceIdSupplier();
    }

    @MockBean
    public AuditRepository auditRepository;
    @MockBean
    public CallbackService callbackService;
    @MockBean
    public FeePayApportionRepository feePayApportionRepository;
    @MockBean
    public TelephonyRepository telephonyRepository;

}
