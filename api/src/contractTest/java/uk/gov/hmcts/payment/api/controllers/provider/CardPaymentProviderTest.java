package uk.gov.hmcts.payment.api.controllers.provider;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.VersionSelector;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.ff4j.FF4j;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.fees2.register.data.service.FeeService;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.controllers.CardPaymentController;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.external.client.GovPayClient;
import uk.gov.hmcts.payment.api.external.client.dto.CardDetails;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.external.client.dto.Link;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.StatusHistory;
import uk.gov.hmcts.payment.api.service.CardDetailsService;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.service.PciPalPaymentService;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.payment.api.model.PaymentFee.feeWith;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@ExtendWith(SpringExtension.class)
@Provider("payment_cardPayment")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}",host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:80}", consumerVersionSelectors={@VersionSelector(tag ="${PACT_BRANCH_NAME:Dev}")})
@Import(CardPaymentProviderTestConfiguration.class)
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
    LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    DelegatingPaymentService<PaymentFeeLink, String> cardDelegatingPaymentService;

    @Autowired
    Payment2Repository payment2RepositoryMock;

    @Autowired
    GovPayClient govPayClientMock;

    @Autowired
    GovPayAuthUtil govPayAuthUtil;

    @Value("${PACT_BRANCH_NAME}")
    String branchName;

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }


    @BeforeEach
    void before(PactVerificationContext context) {
        System.getProperties().setProperty("pact.verifier.publishResults", "true");
        MockMvcTestTarget testTarget = new MockMvcTestTarget();
        testTarget.setControllers(
            new CardPaymentController(cardDelegatingPaymentService, paymentDtoMapper, cardDetailsService, pciPalPaymentService, ff4j,
                feePayApportionService, featureToggler));
        context.setTarget(testTarget);

    }

    @State({"A payment reference exists"})
    public void toGetCardPaymentDetailsWithSuccess() throws IOException, JSONException {
        when(payment2RepositoryMock.findByReference("654321ABC"))
            .thenReturn(Optional.of(populateCardPaymentToDb("1", "e2kkddts5215h9qqoeuth5c0v", "ccd_gw")));
        when(govPayAuthUtil.getServiceName(null, "ccd_gw")).thenReturn("ccd_gw");
        when(govPayAuthUtil.getServiceToken("ccd_gw")).thenReturn("s2sAuthKey");
        when(govPayClientMock.retrievePayment(anyString(), anyString())).thenReturn(buildGovPaymentDto());


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
        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Initiated").externalStatus("created").build();
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("99.99"))
            .caseReference("Reference" + number)
            .ccdCaseNumber("ccdCaseNumber" + number)
            .description("Test payments statuses for " + number)
            .serviceType(Service.DIVORCE.getName())
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
            .statusHistories(Arrays.asList(statusHistory))
            .build();

        PaymentFee fee = feeWith().calculatedAmount(new BigDecimal("99.99")).version("1").code("FEE000" + number).volume(1).build();

        PaymentFeeLink paymentFeeLink =
            paymentFeeLinkWith().paymentReference("2018-0000000000" + number).payments(Arrays.asList(payment)).fees(Arrays.asList(fee)).build();
        payment.setPaymentLink(paymentFeeLink);
        return payment;
    }
}
