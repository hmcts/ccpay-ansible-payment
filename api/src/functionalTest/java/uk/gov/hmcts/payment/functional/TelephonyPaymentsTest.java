package uk.gov.hmcts.payment.functional;

import com.mifmif.common.regex.Generex;
import org.apache.commons.validator.routines.UrlValidator;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.PaymentRecordRequest;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class TelephonyPaymentsTest {
    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private PaymentsTestDsl dsl;

    @Autowired
    private IdamService idamService;
    @Autowired
    private S2sTokenService s2sTokenService;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;
    private static final String DATE_TIME_FORMAT_T_HH_MM_SS = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    private static final Logger LOG = LoggerFactory.getLogger(TelephonyPaymentsTest.class);

    @Before
    public void setUp() throws Exception {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void createTelephonyPaymentAndExpectSuccess() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        PaymentRecordRequest paymentRecordRequest = getTelephonyPayment(telRefNumber);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(paymentRecordRequest)
            .then().created(paymentDto -> {
            assertEquals("payment status is properly set", "Success", paymentDto.getStatus());
        });
    }

    @Test
    public void retrieveASuccessfulTelephonyPaymentViaLookup() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        PaymentRecordRequest paymentRecordRequest = getTelephonyPayment(telRefNumber);
        String status = "success";

        String startDateTime = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(paymentRecordRequest)
            .then().created(paymentDto -> {
            String referenceNumber = paymentDto.getReference();
            assertEquals("payment status is properly set", "Success", paymentDto.getStatus());
            //update the status
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().updatePaymentStatus(referenceNumber, status)
                .then().noContent();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage());
            }
            String endDateTime = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when()
                .enableSearch()
                .searchPaymentsByServiceBetweenDates(paymentRecordRequest.getService(), startDateTime, endDateTime)
                .then().got(PaymentsResponse.class, paymentsResponse -> {
                assertTrue("correct payment has been retrieved",
                    paymentsResponse.getPayments().stream()
                        .anyMatch(o -> o.getPaymentReference().equals(referenceNumber)));
                PaymentDto paymentRetrieved = paymentsResponse.getPayments().stream().filter(o -> o.getPaymentReference().equals(referenceNumber)).findFirst().get();
                assertEquals("correct payment reference retrieved", paymentRetrieved.getCaseReference(), paymentRecordRequest.getReference());
                assertEquals("payment status is properly set", "success", paymentRetrieved.getStatus());
            });
        });
    }

    @Test
    public void retrieveAFailedTelephonyPaymentViaLookup() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        PaymentRecordRequest paymentRecordRequest = getTelephonyPayment(telRefNumber);
        String status = "failed";

        String startDateTime = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(paymentRecordRequest)
            .then().created(paymentDto -> {
            String referenceNumber = paymentDto.getReference();
            assertEquals("payment status is properly set", "Success", paymentDto.getStatus());
            //update the status
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().updatePaymentStatus(referenceNumber, status)
                .then().noContent();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage());
            }
            String endDateTime = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when()
                .enableSearch()
                .searchPaymentsByServiceBetweenDates(paymentRecordRequest.getService(), startDateTime, endDateTime)
                .then().got(PaymentsResponse.class, paymentsResponse -> {
                assertTrue("correct payment has been retrieved",
                    paymentsResponse.getPayments().stream()
                        .anyMatch(o -> o.getPaymentReference().equals(referenceNumber)));
                PaymentDto paymentRetrieved = paymentsResponse.getPayments().stream().filter(o -> o.getPaymentReference().equals(referenceNumber)).findFirst().get();

                assertEquals("correct payment reference retrieved", paymentRetrieved.getCaseReference(), paymentRecordRequest.getReference());
                assertEquals("payment status is properly set", "failed", paymentRetrieved.getStatus());
            });
        });
    }

    @Test
    public void retrieveAnErrorneousTelephonyPaymentViaLookup() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        PaymentRecordRequest paymentRecordRequest = getTelephonyPayment(telRefNumber);
        String status = "error";

        String startDateTime = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(paymentRecordRequest)
            .then().created(paymentDto -> {
            String referenceNumber = paymentDto.getReference();
            assertEquals("payment status is properly set", "Success", paymentDto.getStatus());
            //update the status
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().updatePaymentStatus(referenceNumber, status)
                .then().noContent();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage());
            }

            String endDateTime = LocalDateTime.now(DateTimeZone.UTC).toString(DATE_TIME_FORMAT_T_HH_MM_SS);

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when()
                .enableSearch()
                .searchPaymentsByServiceBetweenDates(paymentRecordRequest.getService(), startDateTime, endDateTime)
                .then().got(PaymentsResponse.class, paymentsResponse -> {
                assertTrue("correct payment has been retrieved",
                    paymentsResponse.getPayments().stream()
                        .anyMatch(o -> o.getPaymentReference().equals(referenceNumber)));
                PaymentDto paymentRetrieved = paymentsResponse.getPayments().stream().filter(o -> o.getPaymentReference().equals(referenceNumber)).findFirst().get();
                assertEquals("correct payment reference retrieved", paymentRetrieved.getCaseReference(), paymentRecordRequest.getReference());
                assertEquals("payment status is properly set", "failed", paymentRetrieved.getStatus());
            });
        });
    }

    @Test
    public void createASuccessfulCardPaymentWithChannelTelephonyAndProviderPciPal() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        CardPaymentRequest paymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("100"))
            .description("description")
            .caseReference(telRefNumber)
            .ccdCaseNumber("1234")
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .provider("pci pal")
            .channel("telephony")
            .siteId("siteId")
            .fees(Collections.singletonList(FeeDto.feeDtoWith()
                .code("feeCode")
                .version("1")
                .calculatedAmount(new BigDecimal("100.1"))
                .build()))
            .build();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createCardPayment(paymentRequest)
            .then().created(paymentDto -> {
            assertTrue(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX));
            assertEquals("payment status is properly set", "Initiated", paymentDto.getStatus());
        });
    }

    @Test
    public void retrieveCorrectPciPalUrlWhenCreatingATelephonyCardPayment() {
        CardPaymentRequest paymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("99.99"))
            .description("telephonyPayment")
            .caseReference("caseRef")
            .ccdCaseNumber("1234")
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .provider("pci pal")
            .channel("telephony")
            .siteId("sideId")
            .fees(Collections.singletonList(FeeDto.feeDtoWith()
                .code("feeCode")
                .version("1")
                .calculatedAmount(new BigDecimal("100.1"))
                .build()))
            .channel("telephony")
            .provider("pci pal")
            .build();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createCardPayment(paymentRequest)
            .then().created(paymentDto -> {
            assertTrue(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX));
            assertEquals("payment status is properly set", "Initiated", paymentDto.getStatus());
            String[] schemes = {"https"};
            UrlValidator urlValidator = new UrlValidator(schemes);
            assertNotNull(paymentDto.getLinks().getNextUrl());
            assertTrue(urlValidator.isValid(paymentDto.getLinks().getNextUrl().getHref()));
        });
    }

    private PaymentRecordRequest getTelephonyPayment(String reference) {
        return PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .externalProvider("pci pal")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("telephony").build())
            .amount(new BigDecimal("99.99"))
            .paymentMethod(PaymentMethodType.CARD)
            .reference(reference)
            .service(Service.CMC)
            .currency(CurrencyCode.GBP)
            .externalReference(reference)
            .siteId("AA01")
            .fees(
                Arrays.asList(
                    FeeDto.feeDtoWith()
                        .calculatedAmount(new BigDecimal("99.99"))
                        .code("FEE012345")
                        .reference("ref_1234")
                        .version("1")
                        .volume(1)
                        .build()
                )
            )
            .reportedDateOffline(DateTime.now().toString())
            .build();
    }
}
