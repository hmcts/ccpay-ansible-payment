package uk.gov.hmcts.payment.functional;

import io.restassured.response.Response;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.payment.api.contract.*;
import uk.gov.hmcts.payment.functional.config.LaunchDarklyFeature;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.fixture.PaymentFixture;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;
import uk.gov.hmcts.payment.functional.service.PaymentTestService;

import java.text.SimpleDateFormat;
import java.util.HashSet;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class CMCCardPaymentPerformanceLiberataTest {

    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private PaymentsTestDsl dsl;

    @Autowired
    private IdamService idamService;
    @Autowired
    private S2sTokenService s2sTokenService;
    @Autowired
    private LaunchDarklyFeature featureToggler;

    @Autowired
    private PaymentTestService paymentTestService;

    private RestTemplate restTemplate;

    @Value("${gov.pay.url}")
    private String govpayUrl;

    @Value("${gov.pay.keys.cmc}")
    private String govpayCmcKey;

    private static String USER_TOKEN;
    private static String USER_TOKEN_PAYMENT;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;

    private static final Logger LOG = LoggerFactory.getLogger(CMCCardPaymentPerformanceLiberataTest.class);

    private static DateTimeZone zoneUTC = DateTimeZone.UTC;

    @Before
    public void setUp() throws Exception {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            USER_TOKEN_PAYMENT = idamService.createUserWith(CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test @Ignore
    public void makeAndRetrieve500CardPaymentsByProbateFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 500;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestProbate("215.00", "PROBATE");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));

        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 500 card payments is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 500 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 500 card payment is : {}",liberataResponseTimeApproach1.getTime());


    }

    @Test
    public void makeAndRetrieve5CardPaymentsByProbateFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestProbate("215.00", "PROBATE");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));

        }

        Thread.sleep(10000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 5 card payments is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 5 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 5 card payment is : {}",liberataResponseTimeApproach1.getTime());


    }

    @Test @Ignore
    public void makeAndRetrieve800CardPaymentsByCmcFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 800;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.aCardPaymentRequest("215.00");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 800 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 800 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 800 card payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrieve5CardPaymentsByCmcFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.aCardPaymentRequest("215.00");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(10000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 5 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 5 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 5 card payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrieve1000CardPaymentsByFinremFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 1000;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestall("215.00", "FINREM");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 1000 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 1000 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 1000 card payemnt is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrieve5CardPaymentsByFinremFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestall("215.00", "FINREM");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(10000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 5 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 5 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 5 card payemnt is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrieve1300CardPaymentsByDivorceFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 1300;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestall("215.00", "DIVORCE");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 1300 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 1300 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 1300 card payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrieve5CardPaymentsByDivorceFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestall("455.00", "DIVORCE");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(10000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, responseTime * 3)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, responseTime)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response of 5 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api 5 card payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api 5 card payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrieveResponseTime30CardPaymentsByProbateFromLiberata() throws InterruptedException {
        // create Card payments
        final Integer PaymentCount = 30;
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        CardPaymentRequest[] cardPaymentRequest = new CardPaymentRequest[PaymentCount];

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            cardPaymentRequest[i] = PaymentFixture.cardPaymentRequestProbate("215.00", "PROBATE");

            paymentTestService.postcardPayment(USER_TOKEN, SERVICE_TOKEN, cardPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Initiated"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);


        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response for 30 card payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 30 card payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 30 card payment is : {}",liberataResponseTimeApproach1.getTime());
    }


}
