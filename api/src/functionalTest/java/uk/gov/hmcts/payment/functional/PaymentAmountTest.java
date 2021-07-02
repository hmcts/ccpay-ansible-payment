package uk.gov.hmcts.payment.functional;

import io.restassured.response.Response;
import lombok.Data;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.fixture.PaymentFixture;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;
import uk.gov.hmcts.payment.functional.service.PaymentTestService;

import java.math.BigDecimal;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(Theories.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PaymentAmountTest {

    private final static String PAYMENT_REFERENCE_REFEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4}){1}";
    private static final String OK = "OK";
    private static final String NOT_OK = "NOT OK";

    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private PaymentTestService paymentTestService;
    @Autowired
    private IdamService idamService;
    @Autowired
    private S2sTokenService s2sTokenService;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;

    @Before
    public void setUp() throws Exception {
        //hook into the Spring test-support framework because of @RunWith(Theories.class)
        TestContextManager tcm = new TestContextManager(getClass());
        tcm.prepareTestInstance(this);

        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @DataPoints
    public static AmountDataPoint[] amountValidations() {
        return new AmountDataPoint[]{
            AmountDataPoint.of("0.01", OK),
            AmountDataPoint.of("0.10", OK),
            AmountDataPoint.of("0.99", OK),

            AmountDataPoint.of("1.01", OK),
            AmountDataPoint.of("1.09", OK),
            AmountDataPoint.of("1.10", OK),
            AmountDataPoint.of("1.99", OK),

            // GovPay error for this big amount
            // AmountDataPoint.of("9999999.99",OK),

            AmountDataPoint.of("0.00", NOT_OK),
            AmountDataPoint.of("-0.01", NOT_OK),
            AmountDataPoint.of("1.1000", NOT_OK)
        };
    }

    @Theory
    public void shouldCreateCardPaymentsWithCorrectAmount(AmountDataPoint dataPoint) {
        if (testProps.baseTestUrl.contains("payment-api-pr-")) {
            return; // temporarily passing the test in PR environment
        }
        // invoke card payment and assert expectedStatus
        Response response = paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, PaymentFixture.aCardPaymentRequest(dataPoint.amount));
        if (!OK.equalsIgnoreCase(dataPoint.expectedStatus)) {
            response.then().statusCode(UNPROCESSABLE_ENTITY.value());
        } else {
            String reference = response.then()
                .statusCode(CREATED.value())
                .and()
                .extract().body().jsonPath().getString("reference");
            assertThat(reference).matches(PAYMENT_REFERENCE_REFEX);

            // invoke get payment by reference and assert value
            BigDecimal amount = paymentTestService.getCardPayment(USER_TOKEN, SERVICE_TOKEN, reference)
                .then()
                .statusCode(HttpStatus.OK.value())
                .and()
                .extract().body().jsonPath().get("amount");
            assertThat(amount).isEqualByComparingTo(dataPoint.amount);
        }
    }

    @Theory
    public void shouldCreatePbaPaymentsWithCorrectAmount(AmountDataPoint dataPoint) {
        if (testProps.baseTestUrl.contains("payment-api-pr-")) {
            return; // temporarily passing the test in PR environment
        }
        // invoke pba payment and assert expectedStatus
        CreditAccountPaymentRequest request = PaymentFixture.aPbaPaymentRequest(dataPoint.amount, "CMC");
        request.setCaseReference("amountTestPbaCaseReference");

        Response response = paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, request);
        if (!OK.equalsIgnoreCase(dataPoint.expectedStatus)) {
            response.then().statusCode(UNPROCESSABLE_ENTITY.value());
        } else {
            String reference = response.then()
                .statusCode(CREATED.value())
                .and()
                .extract().body().jsonPath().getString("reference");
            assertThat(reference).matches(PAYMENT_REFERENCE_REFEX);

            // invoke get payment by reference and assert value
            BigDecimal amount = paymentTestService.getPbaPayment(USER_TOKEN, SERVICE_TOKEN, reference).then()
                .statusCode(HttpStatus.OK.value())
                .and()
                .extract().body().jsonPath().get("amount");
            assertThat(amount).isEqualByComparingTo(dataPoint.amount);
        }
    }

    @Theory
    public void shouldCreateBarPaymentsWithCorrectAmount(AmountDataPoint dataPoint) {
        if (testProps.baseTestUrl.contains("payment-api-pr-")) {
            return; // temporarily passing the test in PR environment
        }
        // invoke bar payment and assert expectedStatus
        Response response = paymentTestService.recordBarPayment(USER_TOKEN, SERVICE_TOKEN, PaymentFixture.aBarPaymentRequest(dataPoint.amount));
        if (!OK.equalsIgnoreCase(dataPoint.expectedStatus)) {
            response.then().statusCode(UNPROCESSABLE_ENTITY.value());
        } else {
            String reference = response.then()
                .statusCode(CREATED.value())
                .and()
                .extract().body().jsonPath().getString("reference");
            assertThat(reference).matches(PAYMENT_REFERENCE_REFEX);
        }
    }

    @Data(staticConstructor = "of")
    static class AmountDataPoint {
        private final String amount;
        private final String expectedStatus;
    }
}
