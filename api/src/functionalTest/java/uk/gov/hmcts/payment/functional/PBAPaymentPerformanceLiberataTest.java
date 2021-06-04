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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.util.Service;
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
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CASE_WORKER_GROUP;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PBAPaymentPerformanceLiberataTest {

    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private PaymentsTestDsl dsl;

    @Autowired
    private PaymentTestService paymentTestService;

    @Autowired
    private IdamService idamService;
    @Autowired
    private S2sTokenService s2sTokenService;
    @Autowired
    private LaunchDarklyFeature featureToggler;

    private static String USER_TOKEN;
    private static String USER_TOKEN_PAYMENT;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;

    private static DateTimeZone zoneUTC = DateTimeZone.UTC;
    private static final String DATE_TIME_FORMAT = "dd-MM-yyyy HH:mm:ss";
    private static final Logger LOG = LoggerFactory.getLogger(PBAPaymentPerformanceLiberataTest.class);



   @Before
    public void setUp() {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CASE_WORKER_GROUP, "caseworker-cmc-solicitor").getAuthorisationToken();
            USER_TOKEN_PAYMENT = idamService.createUserWith(CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);

            TOKENS_INITIALIZED = true;
        }
    }

    @Test @Ignore
    public void makeAndRetrievePba500PaymentsByProbateFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 500;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
            String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForProbateForSuccessLiberataValidation("215.00", Service.PROBATE);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 500 PBA payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 500 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 500 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrievePba5PaymentsByProbateFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForProbateForSuccessLiberataValidation("215.00", Service.PROBATE);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrievePba800PaymentsByFinremFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 800;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequest("215.00", Service.FINREM);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 800 PBA payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 800 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 800 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrievePba5PaymentsByFinremFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequest("215.00", Service.FINREM);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrievePba1000PaymentsByDivorceFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 1000;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForDivorce("455.00", Service.DIVORCE);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 1000 PBA payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 1000 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 1000 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrievePba5PaymentsByDivorceFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForDivorce("455.00", Service.DIVORCE);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time

        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrievePba1300PaymentsByCmcFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 1300;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequest("215.00", Service.CMC);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Pending"));
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
        LOG.info("Comparison of old and new api end point response for 1300 PBA payment is same");


//         Get card payments liberate pull response time
        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 1300 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 1300 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test
    public void makeAndRetrievePba5PaymentsByCmcFromLiberata() throws InterruptedException {
        // create a PBA payment
        final Integer PaymentCount = 5;
        final Long responseTime = 30L;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequest("215.00", Service.CMC);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
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
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time
        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());

        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());
    }

    @Test @Ignore
    public void makeAndRetrieveResponseTime30PbaPaymentsByFPLFromLiberata() throws InterruptedException {
        // create PBA payments
        final Integer PaymentCount = 30;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForFPL("215.00", Service.FPL);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response for 30 PBA payment is same");


//         Get card payments liberate pull response time
        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 30 PBA payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 30 PBA payment is : {}",liberataResponseTimeApproach1.getTime());

    }

    @Test
    public void makeAndRetrieveResponseTime5PbaPaymentsByFPLFromLiberata() throws InterruptedException {
        // create PBA payments
        final Integer PaymentCount = 5;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForFPL("215.00", Service.FPL);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time
        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());

    }

    @Test
    public void makeAndRetrieveResponseTime5PbaPaymentsByCivilFromLiberata() throws InterruptedException {
        // create PBA payments
        final Integer PaymentCount = 5;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForCivil("215.00", Service.CIVIL);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time
        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());

    }

    @Test
    public void makeAndRetrieveResponseTime5PbaPaymentsByIACFromLiberata() throws InterruptedException {
        // create PBA payments
        final Integer PaymentCount = 5;
        SimpleDateFormat formatter= new SimpleDateFormat(DATE_TIME_FORMAT);

        CreditAccountPaymentRequest[] accountPaymentRequest = new CreditAccountPaymentRequest[PaymentCount];
        String accountNumber = testProps.existingAccountNumber;

        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusMinutes(5).toDate());

        for(int i=0; i<PaymentCount;i++) {
            accountPaymentRequest[i] = PaymentFixture.aPbaPaymentRequestForIAC("215.00", Service.IAC);
            accountPaymentRequest[i].setAccountNumber(accountNumber);
            paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest[i])
                .then()
                .statusCode(CREATED.value())
                .body("status", equalTo("Success"));
        }

        Thread.sleep(5000);
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = paymentTestService.getLiberatePullPaymentsByStartAndEndDate(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        PaymentsResponse liberataResponseApproach1 = paymentTestService.getLiberatePullPaymentsByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate, 30L)
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        //Comparing the response size of old and new approach
        assertThat(liberataResponseOld.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);
        assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(PaymentCount);

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response for 5 PBA payment is same");


//         Get card payments liberate pull response time
        Response liberataResponseTimeOld = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDate(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeOld.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds old api for 5 PBA payment is : {}",liberataResponseTimeOld.getTime());


        Response liberataResponseTimeApproach1 = paymentTestService.getLiberatePullPaymentsTimeByStartAndEndDateApproach1(SERVICE_TOKEN, startDate,endDate);
        assertThat(liberataResponseTimeApproach1.statusCode()).isEqualTo(200);
        LOG.info("Response time in milliseconds approach 1 api for 5 PBA payment is : {}",liberataResponseTimeApproach1.getTime());

    }
    }


