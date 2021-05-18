package uk.gov.hmcts.payment.api.controllers.provider;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.VersionSelector;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import com.google.common.collect.ImmutableMap;
import org.ff4j.FF4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.fees2.register.data.service.FeeService;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.controllers.CardPaymentController;
import uk.gov.hmcts.payment.api.controllers.PaymentController;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.external.client.GovPayClient;
import uk.gov.hmcts.payment.api.external.client.dto.CardDetails;
import uk.gov.hmcts.payment.api.external.client.dto.CreatePaymentRequest;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.external.client.dto.Link;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;
import uk.gov.hmcts.payment.api.model.StatusHistory;
import uk.gov.hmcts.payment.api.service.CallbackService;
import uk.gov.hmcts.payment.api.service.CardDetailsService;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.service.PaymentServiceImpl;
import uk.gov.hmcts.payment.api.service.PciPalPaymentService;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.util.DateUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayKeyRepository;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.payment.api.model.PaymentFee.feeWith;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@ExtendWith(SpringExtension.class)
@Provider("payment_cardPayment")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}", host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:80}", consumerVersionSelectors = {
    @VersionSelector(tag = "master")})
@Import(CardPaymentProviderTestConfiguration.class)
@IgnoreNoPactsToVerify
public class CardPaymentProviderTest {

    @Autowired
    PaymentDtoMapper paymentDtoMapper;
    @Autowired
    FeeService feeService;
    @Autowired
    CardDetailsService<CardDetails, String> cardDetailsService;
    @Autowired
    PciPalPaymentService pciPalPaymentService;
    @Autowired
    FF4j ff4j;
    @Autowired
    FeePayApportionService feePayApportionService;

    @Autowired
    PaymentFeeRepository paymentFeeRepositoryMock;

    @Autowired
    PaymentFeeLinkRepository paymentFeeLinkRepositoryMock;

    @Autowired
    LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    ReferenceDataService referenceDataService;

    @Autowired
    DelegatingPaymentService<PaymentFeeLink, String> cardDelegatingPaymentService;

    @Autowired
    PaymentServiceImpl paymentService;

    @Autowired
    PaymentValidator paymentValidator;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    Payment2Repository payment2RepositoryMock;

    @Autowired
    PaymentStatusRepository paymentStatusRepositoryMock;

    @Autowired
    CallbackService callbackServiceMock;


    @Autowired
    GovPayClient govPayClientMock;

    @Autowired
    GovPayAuthUtil govPayAuthUtil;

    @Autowired
    ReferenceUtil referenceUtil;

    @Autowired
    GovPayKeyRepository govPayKeyRepositoryMock;

    @Value("${PACT_BRANCH_NAME}")
    String branchName;


    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @BeforeEach
    void before(PactVerificationContext context) {
        System.getProperties().setProperty("pact.verifier.publishResults", "true");
        MockMvcTestTarget testTarget = new MockMvcTestTarget();
        testTarget.setControllers(
            new CardPaymentController(cardDelegatingPaymentService, paymentDtoMapper, cardDetailsService, pciPalPaymentService, ff4j,
                feePayApportionService, featureToggler, referenceDataService),
            new PaymentController(paymentService, paymentStatusRepositoryMock, callbackServiceMock,
                paymentDtoMapper, paymentValidator, ff4j,
                dateUtil, paymentFeeRepositoryMock, featureToggler));
        if (context != null) {
            context.setTarget(testTarget);
        }

    }

    @State({"A payment reference exists"})
    public void toGetCardPaymentDetailsWithSuccess() {
        when(payment2RepositoryMock.findByReference("654321ABC"))
            .thenReturn(Optional.of(populateCardPaymentToDb("1", "e2kkddts5215h9qqoeuth5c0v", "ccd_gw")));
        when(govPayAuthUtil.getServiceName(null, "ccd_gw")).thenReturn("ccd_gw");
        when(govPayAuthUtil.getServiceToken("ccd_gw")).thenReturn("s2sAuthKey");
        when(govPayClientMock.retrievePayment(anyString(), anyString())).thenReturn(buildGovPaymentDto());
    }

    @State({"Payments exist for a case"})
    public void toPaymentsForACasesWithSuccess() {
        when(ff4j.check("payment-search")).thenReturn(Boolean.TRUE);
        PaymentFeeLink paymentLink = populateCardPaymentToDb("1", "e2kkddts5215h9qqoeuth5c0v", "ccd_gw").getPaymentLink();
        when(paymentFeeLinkRepositoryMock.findAll(any(Specification.class))).thenReturn(Arrays.asList(paymentLink));

    }

    @State({"A Payment is posted for a case"})
    public void toPostAPaymentWithSuccess() {

        when(govPayKeyRepositoryMock.getKey(null)).thenReturn("govePayKey");
        when(govPayClientMock.createPayment(anyString(), any(CreatePaymentRequest.class))).thenReturn(buildGovPaymentDto());

        PaymentFeeLink paymentLink = populateCardPaymentToDb("1", "e2kkddts5215h9qqoeuth5c0v", "ccd_gw").getPaymentLink();
        when(paymentFeeLinkRepositoryMock.save(any(PaymentFeeLink.class))).thenReturn(paymentLink);

    }

    private GovPayPayment buildGovPaymentDto() {
        return GovPayPayment.govPaymentWith()
            .amount(10000)
            .state(new uk.gov.hmcts.payment.api.external.client.dto.State("created", false, null, null))
            .description("description")
            .reference("reference")
            .paymentId("paymentId")
            .paymentProvider("sandbox")
            .returnUrl("https://www.google.com")
            .links(GovPayPayment.Links.linksWith().nextUrl(new Link("any", ImmutableMap.of(), "cancelHref", "any")).build())
            .build();

    }

    private Payment populateCardPaymentToDb(String number, String externalReference, String s2sServiceName) {
        //Create a payment in remissionDbBackdoor
        Date now = new Date();
        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Initiated").externalStatus("created").build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("99.99"))
            .caseReference("Reference" + number)
            .ccdCaseNumber("ccdCaseNumber" + number)
            .description("Test payments statuses for " + number)
            .serviceType("Divorce")
            .s2sServiceName(s2sServiceName)
            .currency("GBP")
            .siteId("AA0" + number)
            .userId("USER_ID")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference(externalReference)
            .reference("RC-1519-9028-2432-000" + number)
            .status("submitted")
            .statusHistories(Arrays.asList(statusHistory))
            .dateUpdated(now)
            .dateCreated(now)
            .build();

        PaymentFee fee = feeWith().calculatedAmount(new BigDecimal("99.99")).version("1").code("FEE000" + number).volume(1).build();

        PaymentFeeLink paymentFeeLink =
            paymentFeeLinkWith().paymentReference("2018-0000000000" + number).payments(Arrays.asList(payment)).fees(Arrays.asList(fee)).build();
        payment.setPaymentLink(paymentFeeLink);
        return payment;
    }
}
