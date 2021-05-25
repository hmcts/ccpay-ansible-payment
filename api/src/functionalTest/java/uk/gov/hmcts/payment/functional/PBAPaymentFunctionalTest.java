package uk.gov.hmcts.payment.functional;

import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.functional.config.LaunchDarklyFeature;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.fixture.PaymentFixture;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;
import uk.gov.hmcts.payment.functional.service.PaymentTestService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpStatus.*;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CASE_WORKER_GROUP;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PBAPaymentFunctionalTest {

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

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_TIME_FORMAT_T_HH_MM_SS = "yyyy-MM-dd'T'HH:mm:ss";

    @Before
    public void setUp() {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CASE_WORKER_GROUP, "caseworker-cmc-solicitor").getAuthorisationToken();
            USER_TOKEN_PAYMENT = idamService.createUserWith(CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);

            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void makeAndRetrievePbaPaymentsByProbate() {
        // create a PBA payment
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequestForProbate("90.00", Service.PROBATE);
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService.getPbaPaymentsByAccountNumber(USER_TOKEN, SERVICE_TOKEN, testProps.existingAccountNumber)
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().get(0).getAccountNumber()).isEqualTo(accountNumber);
    }

    @Test
    public void makeAndRetrievePBAPaymentByProbateTestShouldReturnAutoApportionedFees() {
        String accountNumber = testProps.existingAccountNumber;
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        // create card payment
        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest accountPaymentRequest = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .description("New passport application")
            .caseReference("aCaseReference")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber(accountNumber)
            .fees(fees)
            .build();

        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService.getPbaPaymentsByAccountNumber(USER_TOKEN, SERVICE_TOKEN, testProps.existingAccountNumber)
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().get(0).getAccountNumber()).isEqualTo(accountNumber);

        // TEST retrieve payments, remissions and fees by payment-group-reference
        dsl.given().userToken(USER_TOKEN_PAYMENT)
            .s2sToken(SERVICE_TOKEN)
            .when().getPaymentGroups(paymentsResponse.getPayments().get(0).getCcdCaseNumber())
            .then().getPaymentGroups((paymentGroupsResponse -> {
            paymentGroupsResponse.getPaymentGroups().stream()
                .filter(paymentGroupDto -> paymentGroupDto.getPayments().get(0).getReference()
                    .equalsIgnoreCase(paymentsResponse.getPayments().get(0).getReference()))
                .forEach(paymentGroupDto -> {

                    boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
                    if(apportionFeature) {
                        paymentGroupDto.getFees().stream()
                            .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0271"))
                            .forEach(fee -> {
                                assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                            });
                        paymentGroupDto.getFees().stream()
                            .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0272"))
                            .forEach(fee -> {
                                assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                            });
                        paymentGroupDto.getFees().stream()
                            .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0273"))
                            .forEach(fee -> {
                                assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                            });
                    }
                });
        }));
    }

    @Test
    public void makeAndRetrievePbaPaymentsByProbateForSuccessLiberataValidation() {
        // create a PBA payment
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequestForProbateForSuccessLiberataValidation("215.00", Service.PROBATE);
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService.getPbaPaymentsByAccountNumber(USER_TOKEN, SERVICE_TOKEN, testProps.existingAccountNumber)
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().get(0).getAccountNumber()).isEqualTo(accountNumber);

        // Get pba payments by ccdCaseNumber
        PaymentsResponse liberataResponse = paymentTestService.getPbaPaymentsByCCDCaseNumber(SERVICE_TOKEN, accountPaymentRequest.getCcdCaseNumber())
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        assertThat(liberataResponse.getPayments().get(0).getAccountNumber()).isEqualTo(accountNumber);
        assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getApportionedPayment()).isEqualTo("215.00");
        assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getCalculatedAmount()).isEqualTo("215.00");
        assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getMemoLine()).isEqualTo("Personal Application for grant of Probate");
        assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getNaturalAccountCode()).isEqualTo("4481102158");
        assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getJurisdiction1()).isEqualTo("family");
        assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getJurisdiction2()).isEqualTo("probate registry");
    }

    @Test
    public void makeAndRetrievePBAPaymentByProbateTestShouldReturnAutoApportionedFeesForSuccessMultipleFeesLiberataValidation() {
        String accountNumber = testProps.existingAccountNumber;
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        // create card payment
        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest accountPaymentRequest = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .description("New passport application")
            .caseReference("aCaseReference")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber(accountNumber)
            .fees(fees)
            .build();

        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        // Get pba payments by accountNumber
        PaymentsResponse paymentsResponse = paymentTestService.getPbaPaymentsByAccountNumber(USER_TOKEN, SERVICE_TOKEN, testProps.existingAccountNumber)
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().get(0).getAccountNumber()).isEqualTo(accountNumber);

        // Get pba payments by ccdCaseNumber
        PaymentsResponse liberataResponse = paymentTestService.getPbaPaymentsByCCDCaseNumber(SERVICE_TOKEN, accountPaymentRequest.getCcdCaseNumber())
            .then()
            .statusCode(OK.value()).extract().as(PaymentsResponse.class);

        assertThat(liberataResponse.getPayments().get(0).getAccountNumber()).isEqualTo(accountNumber);

        if(liberataResponse.getPayments().get(0).getFees().get(0).getCode().equalsIgnoreCase("FEE0271")) {
            assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getApportionedPayment()).isEqualTo("20.00");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getCalculatedAmount()).isEqualTo("20.00");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getMemoLine()).isEqualTo("RECEIPT OF FEES - Tribunal issue other");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getNaturalAccountCode()).isEqualTo("4481102178");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getJurisdiction1()).isEqualTo("tribunal");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(0).getJurisdiction2()).isEqualTo("property chamber");
        }
        if(liberataResponse.getPayments().get(0).getFees().get(1).getCode().equalsIgnoreCase("FEE0272")) {
            assertThat(liberataResponse.getPayments().get(0).getFees().get(1).getApportionedPayment()).isEqualTo("40.00");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(1).getCalculatedAmount()).isEqualTo("40.00");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(1).getMemoLine()).isEqualTo("RECEIPT OF FEES - Tribunal issue other");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(1).getNaturalAccountCode()).isEqualTo("4481102178");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(1).getJurisdiction1()).isEqualTo("tribunal");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(1).getJurisdiction2()).isEqualTo("property chamber");
        }
        if(liberataResponse.getPayments().get(0).getFees().get(2).getCode().equalsIgnoreCase("FEE0273")) {
            assertThat(liberataResponse.getPayments().get(0).getFees().get(2).getApportionedPayment()).isEqualTo("60.00");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(2).getCalculatedAmount()).isEqualTo("60.00");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(2).getMemoLine()).isEqualTo("RECEIPT OF FEES - Family enforcement other");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(2).getNaturalAccountCode()).isEqualTo("4481102167");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(2).getJurisdiction1()).isEqualTo("family");
            assertThat(liberataResponse.getPayments().get(0).getFees().get(2).getJurisdiction2()).isEqualTo("family court");
        }
    }

    @Test
    public void makeAndRetrievePbaPaymentByFinrem() throws InterruptedException {

        String startDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT);
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequest("90.00", Service.FINREM);
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));
        Thread.sleep(2000);
        String endDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsByServiceBetweenDates(Service.FINREM, startDate, endDate)
            .then().getPayments((paymentsResponse -> {
            Assertions.assertThat(paymentsResponse.getPayments().size()).isEqualTo(1);
        }));
    }

    @Test
    public void makeAndRetrievePbaPaymentByCivilService() throws InterruptedException {

        String startDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT);
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequestForCivil("90.00", Service.CIVIL);
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));
        Thread.sleep(2000);
        String endDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsByServiceBetweenDates(Service.CIVIL, startDate, endDate)
            .then().getPayments((paymentsResponse -> {
            Assertions.assertThat(paymentsResponse.getPayments().size()).isEqualTo(1);
        }));
    }

    @Test
    public void makeAndRetrievePbaPaymentByIACService() throws InterruptedException {

        String startDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT);
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequestForIAC("90.00", Service.IAC);
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        Thread.sleep(2000);
        String endDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsByServiceBetweenDates(Service.IAC, startDate, endDate)
            .then().getPayments((paymentsResponse -> {
            Assertions.assertThat(paymentsResponse.getPayments().size()).isEqualTo(1);
        }));
    }

    @Test
    public void makeAndRetrievePbaPaymentByFPLService() throws InterruptedException {

        String startDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT);
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequestForFPL("90.00", Service.FPL);
        accountPaymentRequest.setAccountNumber(accountNumber);
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        Thread.sleep(2000);
        String endDate = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsByServiceBetweenDates(Service.FPL, startDate, endDate)
            .then().getPayments((paymentsResponse -> {
            Assertions.assertThat(paymentsResponse.getPayments().size()).isEqualTo(1);
        }));
    }

    @Test
    public void shouldRejectDuplicatePayment() {
        String accountNumber = testProps.existingAccountNumber;
        CreditAccountPaymentRequest accountPaymentRequest = PaymentFixture.aPbaPaymentRequestForProbate("550.50", Service.PROBATE);
        accountPaymentRequest.setAccountNumber(accountNumber);
        // when & then
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success"));

        // duplicate payment with same details from same user
        paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(BAD_REQUEST.value())
            .body(equalTo("duplicate payment"));
    }
}
