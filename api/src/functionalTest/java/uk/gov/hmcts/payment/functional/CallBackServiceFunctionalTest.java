package uk.gov.hmcts.payment.functional;

import org.awaitility.Duration;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.fixture.PaymentFixture;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

//@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class CallBackServiceFunctionalTest {

    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private PaymentsTestDsl dsl;

    @Autowired
    private IdamService idamService;
    @Autowired
    private S2sTokenService s2sTokenService;

    @Value("${gov.pay.url}")
    private String govpayUrl;

    @Value("${gov.pay.keys.cmc}")
    private String govpayCmcKey;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;

    @Before
    public void setUp() {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }


    /**
     * This test verifies E2E flow of serviceCallBack functionality when there is card payment happens.
     * <p><ul>
     * <li>Client POSTs a card payment with serviceCallbackUrl
     * <li>Client GETs card payment
     * <li>Cancel payment by invoking gov-pay REST API directly
     * <li>Trigger status update job scheduler. This will sends a status update message to azure service bus which will be consumed by a azure function.
     * <li>Verify serviceCallBack API has called from Azure function.
     * Currently this test uses a Mock-API for serviceCallBack which is hosted in payment-app itself (Ideally a hosted mock server required)
     * </ul><p>
     */
    //@Test
    public void shouldInvokeCallBackForStatusUpdate() {
        final String[] reference = new String[1];

        String serviceCallBackUrl = testProps.mockCallBackUrl.replaceFirst("https", "http"); // internal urls with http only.

        // Step 1 : create card payment
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .serviceCallBackUrl(serviceCallBackUrl)
            .when().createCardPayment(PaymentFixture.aCardPaymentRequest("30.99"))
            .then().created(savedPayment -> {
            reference[0] = savedPayment.getReference();
            assertEquals("Initiated", savedPayment.getStatus());
        });

        // Step 2 : GET card payment
        PaymentDto paymentDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getCardPayment(reference[0])
            .then().get();

        // Step 3: Cancel payment - trigger govPay status change
        given()
            .header(AUTHORIZATION, "Bearer " + govpayCmcKey)
            .post(govpayUrl + "/" + paymentDto.getExternalReference() +"/cancel")
            .then()
            .statusCode(204);

        // Step 4: invoke job schedule
        dsl.given()
            .s2sToken(SERVICE_TOKEN)
            .when().cardPaymentsStatusUpdateJob()
            .then()
            .ok();

        // Step 5: verify callback invocation from azure functions
        await()
            .pollInterval(Duration.TWO_HUNDRED_MILLISECONDS)
            .atMost(Duration.TWO_SECONDS)
            .until(() -> given().get(testProps.mockCallBackUrl + "/" + reference[0]).getStatusCode() == 200);
    }
}
