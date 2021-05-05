package uk.gov.hmcts.payment.api.controllers.provider;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.VersionSelector;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import org.ff4j.FF4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.controllers.CreditAccountPaymentController;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.mapper.CreditAccountPaymentRequestMapper;
import uk.gov.hmcts.payment.api.mapper.PBAStatusErrorMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportionRepository;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentChannelRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentMethodRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.service.CreditAccountPaymentService;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.util.AccountStatus;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.validators.DuplicatePaymentValidator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@Provider("payment_creditAccountPayment")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}", host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:80}", consumerVersionSelectors = {
    @VersionSelector(tag = "master")})
@Import(CreditAccountPaymentProviderTestConfiguration.class)
@IgnoreNoPactsToVerify
public class CreditAccountPaymentProviderTest {

    private static final String ACCOUNT_NUMBER_KEY = "accountNumber";
    private static final String ACCOUNT_NAME_KEY = "accountName";
    private static final String AVAILABLE_BALANCE_KEY = "availableBalance";

    @Autowired
    PaymentDtoMapper paymentDtoMapper;
    @Autowired
    CreditAccountPaymentService<PaymentFeeLink, String> creditAccountPaymentService;
    @Autowired
    CreditAccountDtoMapper creditAccountDtoMapper;
    @Autowired
    AccountService<AccountDto, String> accountServiceMock;
    @Autowired
    DuplicatePaymentValidator paymentValidator;
    @Autowired
    FeePayApportionRepository feePayApportionRepository;
    @Autowired
    PaymentFeeRepository paymentFeeRepository;
    @Autowired
    FF4j ff4j;
    @Autowired
    FeePayApportionService feePayApportionService;
    @Autowired
    LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    UserIdSupplier userIdSupplierMock;

    @Autowired
    ServiceIdSupplier serviceIdSupplierMock;

    @Autowired
    PaymentChannelRepository paymentChannelRepositoryMock;

    @Autowired
    PaymentMethodRepository paymentMethodRepositoryMock;

    @Autowired
    PaymentStatusRepository paymentStatusRepositoryMock;

    @Autowired
    PaymentFeeLinkRepository paymentFeeLinkRepositoryMock;

    @Autowired
    PBAStatusErrorMapper pbaStatusErrorMapper;

    @Autowired
    CreditAccountPaymentRequestMapper requestMapper;

    @Autowired
    ReferenceDataService referenceDataService;
    @Autowired
    AuthTokenGenerator authTokenGenerator;
    @Autowired
    PaymentService<PaymentFeeLink, String> paymentService;

    private final static String PAYMENT_CHANNEL_ONLINE = "online";

    private final static String PAYMENT_METHOD = "payment by account";

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @BeforeEach
    void before(PactVerificationContext context) {
        MockMvcTestTarget testTarget = new MockMvcTestTarget();
        testTarget.setControllers(
            new CreditAccountPaymentController(creditAccountPaymentService, creditAccountDtoMapper, accountServiceMock, paymentValidator,
                feePayApportionService, featureToggler, pbaStatusErrorMapper, requestMapper, Arrays.asList("CMC"), paymentService,
                referenceDataService, authTokenGenerator));
        if (context != null) {
            context.setTarget(testTarget);
        }
    }

    @State({"An active account has sufficient funds for a payment"})
    public void toCreateNewCreditAccountPayment(Map<String, Object> paymentMap) {

        setUpMockInteractions(paymentMap, "Payment Status success", "success", AccountStatus.ACTIVE);
    }


    @State({"An active account has insufficient funds for a payment"})
    public void toRefuseCreditAccountPaymentInusfficientFunds(Map<String, Object> paymentMap) {
        setUpMockInteractions(paymentMap, "Payment Status failed", "failed", AccountStatus.ACTIVE);
    }

    @State({"An on hold account requests a payment"})
    public void toRefuseCreditAccountPaymenOnHold(Map<String, Object> paymentMap) {
        setUpMockInteractions(paymentMap, "Payment Status failed", "failed", AccountStatus.ON_HOLD);
    }


    @State({"A deleted account requests a payment"})
    public void toRefuseCreditAccountPaymenDeleted(Map<String, Object> paymentMap) {
        setUpMockInteractions(paymentMap, "Payment Status failed", "failed", AccountStatus.DELETED);
    }


    private void setUpMockInteractions(Map<String, Object> paymentMap, String s, String success, AccountStatus accountStatus) {
        String accountNumber = (String) paymentMap.get(ACCOUNT_NUMBER_KEY);
        String availableBalance = (String) paymentMap.get(AVAILABLE_BALANCE_KEY);
        String accountName = (String) paymentMap.get(ACCOUNT_NAME_KEY);

        when(userIdSupplierMock.get()).thenReturn("userId");
        when(serviceIdSupplierMock.get()).thenReturn("ccd_gw");
        when(paymentChannelRepositoryMock.findByNameOrThrow(PAYMENT_CHANNEL_ONLINE))
            .thenReturn(PaymentChannel.paymentChannelWith().description("Online Channel").name("online").build());
        when(paymentMethodRepositoryMock.findByNameOrThrow(PAYMENT_METHOD))
            .thenReturn(PaymentMethod.paymentMethodWith().description("Payment by account").name("Payment by account").build());
        when(paymentStatusRepositoryMock.findByNameOrThrow(anyString()))
            .thenReturn(PaymentStatus.paymentStatusWith().description(s).name(success).build());
        when(accountServiceMock.retrieve(accountNumber)).thenReturn(AccountDto.accountDtoWith()
            .accountNumber(accountNumber)
            .accountName(accountName)
            .creditLimit(BigDecimal.valueOf(28879))
            .availableBalance(new BigDecimal(availableBalance))
            .status(accountStatus)
            .build());
    }


}
