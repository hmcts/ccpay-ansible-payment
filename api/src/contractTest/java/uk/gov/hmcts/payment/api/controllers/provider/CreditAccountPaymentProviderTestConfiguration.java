package uk.gov.hmcts.payment.api.controllers.provider;

import org.ff4j.FF4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.hmcts.payment.api.audit.AuditRepository;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.configuration.security.AuthenticatedServiceIdSupplier;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.mapper.CreditAccountPaymentRequestMapper;
import uk.gov.hmcts.payment.api.mapper.PBAStatusErrorMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportionRepository;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentChannelRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.model.PaymentMethodRepository;
import uk.gov.hmcts.payment.api.model.PaymentProviderRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;
import uk.gov.hmcts.payment.api.model.TelephonyRepository;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.service.CallbackService;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.service.LoggingCreditAccountPaymentService;
import uk.gov.hmcts.payment.api.service.PaymentServiceImpl;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.service.UserAwareDelegatingCreditAccountPaymentService;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.validators.DuplicatePaymentValidator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;


@TestConfiguration
public class CreditAccountPaymentProviderTestConfiguration {

    @Bean
    @Primary
    public PaymentDtoMapper paymentDtoMapper() {
        return new PaymentDtoMapper();
    }


    @Bean
    public UserAwareDelegatingCreditAccountPaymentService userAwareDelegatingCreditAccountPaymentService() {
        return new UserAwareDelegatingCreditAccountPaymentService(paymentFeeLinkRepository(), paymentChannelRepository, paymentMethodRepository,
            paymentProviderRepository, paymentStatusRepository,
            payment2Repository, referenceUtil(), serviceIdSupplier, userIdSupplier, orderCaseUtil);

    }

    @Bean("loggingCreditAccountPaymentService")
    @Primary
    public LoggingCreditAccountPaymentService loggingCreditAccountPaymentService() {
        return new LoggingCreditAccountPaymentService(userIdSupplier, userAwareDelegatingCreditAccountPaymentService());
    }

    @MockBean
    PaymentProviderRepository paymentProviderRepository;

    @MockBean
    PaymentStatusRepository paymentStatusRepository;
    @MockBean
    PaymentChannelRepository paymentChannelRepository;
    @MockBean
    PaymentMethodRepository paymentMethodRepository;
    @MockBean
    Payment2Repository payment2Repository;
    @MockBean
    ServiceIdSupplier serviceIdSupplier;
    @MockBean
    UserIdSupplier userIdSupplier;

    @MockBean
    DuplicatePaymentValidator paymentValidator;
    @MockBean
    FF4j ff4j;
    @MockBean
    FeePayApportionService feePayApportionService;
    @MockBean
    LaunchDarklyFeatureToggler featureToggler;
    @MockBean
    FeePayApportionRepository feePayApportionRepository;
    @MockBean
    PaymentFeeRepository paymentFeeRepository;
    @MockBean
    FeesService feesService;

    @MockBean
    AccountService<AccountDto, String> accountService;
    @MockBean
    DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;
    @MockBean
    CallbackService callbackService;
    @MockBean
    TelephonyRepository telephonyRepository;
    @MockBean
    AuditRepository paymentAuditRepository;
    @MockBean
    ReferenceDataService referenceDataService;
    @MockBean
    AuthTokenGenerator authTokenGenerator;
    @MockBean
    OrderCaseUtil orderCaseUtil;

    @Bean
    @Primary
    public PaymentServiceImpl paymentService() {
        return new PaymentServiceImpl(delegatingPaymentService, payment2Repository, callbackService, paymentStatusRepository, telephonyRepository,
            paymentAuditRepository, feePayApportionService,
            feePayApportionRepository, featureToggler);
    }


    @Bean
    @Primary
    public ReferenceUtil referenceUtil() {
        return new ReferenceUtil();
    }

    @Bean
    @Primary
    public CreditAccountDtoMapper creditAccountDtoMapper() {
        return new CreditAccountDtoMapper();

    }

    @Bean
    @Primary
    public ServiceIdSupplier serviceIdSupplier() {
        return new AuthenticatedServiceIdSupplier();
    }

    @Bean
    @Primary
    public PaymentFeeLinkRepository paymentFeeLinkRepository() {
        return new PayFeeLinkRepositoryStub();
    }

    @Bean
    @Primary
    public PBAStatusErrorMapper pBAStatusErrorMapper() {
        return new PBAStatusErrorMapper();
    }

    @Bean
    @Primary
    CreditAccountPaymentRequestMapper requestMapper() {
        return new CreditAccountPaymentRequestMapper();
    }


}
