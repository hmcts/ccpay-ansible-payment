package uk.gov.hmcts.payment.functional;

import org.assertj.core.api.Java6Assertions;
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
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.PaymentRecordRequest;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PaymentBarPerformanceLiberataTest {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final Logger LOG = LoggerFactory.getLogger(PaymentBarPerformanceLiberataTest.class);

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

    private static DateTimeZone zoneUTC = DateTimeZone.UTC;

    @Before
    public void setUp() throws Exception {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }


    @Test
    public void createPaymentRecordAndValidateSearchResults() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusSeconds(30).toDate());

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createTelephonyPayment(getPaymentRecordRequest())
            .then().created(paymentDto -> {
            assertNotNull(paymentDto.getReference());
        });

        // search payment and assert the result

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceName(startDate, endDate, "cash")
            .then().getPayments();

        PaymentsResponse liberataResponseApproach1 = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "cash")
            .then().getPayments();

        //Comparing the response size of old and new approach
        Java6Assertions.assertThat(liberataResponseOld.getPayments().size()).
            isEqualTo(liberataResponseApproach1.getPayments().size());

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        Java6Assertions.assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response BAR Cash payment is same");

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "cash")
            .then().getPayments((paymentsResponse -> {
            LOG.info("paymentsResponse: {}", paymentsResponse.getPayments().size());
            assertThat(paymentsResponse.getPayments().size()).isGreaterThanOrEqualTo(1);
            assertThat(paymentsResponse.getPayments().get(0).getMethod()).isEqualTo("cash");
            assertThat(paymentsResponse.getPayments().get(0).getAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(paymentsResponse.getPayments().get(0).getChannel()).isEqualTo("digital bar");
            assertThat(paymentsResponse.getPayments().get(0).getStatus()).isEqualTo("success");
            assertThat(paymentsResponse.getPayments().get(0).getServiceName()).isEqualTo("Digital Bar");
            assertThat(paymentsResponse.getPayments().get(0).getDateCreated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getDateUpdated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCurrency()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCaseReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getExternalProvider()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getSiteId()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentGroupReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getReportedDateOffline()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getGiroSlipNo()).isEqualTo("312131");
            FeeDto feeDto = paymentsResponse.getPayments().get(0).getFees().get(0);
            assertThat(feeDto.getCode()).isEqualTo("FEE0002");
            assertThat(feeDto.getVersion()).isEqualTo("4");
            assertThat(feeDto.getCalculatedAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(feeDto.getReference()).isNotNull();
            assertThat(feeDto.getReference()).isEqualTo("REF_123");
            assertThat(feeDto.getVolume()).isEqualTo(1);
        }));

    }

    private PaymentRecordRequest getPaymentRecordRequest() {
        return PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .amount(new BigDecimal("550.00"))
            .paymentMethod(PaymentMethodType.CASH)
            .reference("REF_123")
            .externalProvider("middle office provider")
            .service("DIGITAL_BAR")
            .currency(CurrencyCode.GBP)
            .giroSlipNo("312131")
            .reportedDateOffline(DateTime.now().toString())
            .siteId("AA01")
            .fees(
                Arrays.asList(
                    FeeDto.feeDtoWith()
                        .calculatedAmount(new BigDecimal("550.00"))
                        .code("FEE0002")
                        .version("4")
                        .volume(1)
                        .reference("REF_123")
                        .build()
                )
            )
            .build();
    }

    @Test
    public void createBarPostalOrderPaymentRecordAndValidateSearchResults() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusSeconds(30).toDate());

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createTelephonyPayment(getPaymentRecordRequestForPostalOrder())
            .then().created(paymentDto -> {
                LOG.info(paymentDto.getReference());
            assertNotNull(paymentDto.getReference());
        });

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceName(startDate, endDate, "postal_order")
            .then().getPayments();

        PaymentsResponse liberataResponseApproach1 = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "postal_order")
            .then().getPayments();

        //Comparing the response size of old and new approach
        Java6Assertions.assertThat(liberataResponseOld.getPayments().size()).
            isEqualTo(liberataResponseApproach1.getPayments().size());
        LOG.info(""+liberataResponseApproach1.getPayments().size());
        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        Java6Assertions.assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response BAR Postal Order payment is same");

        // search payment and assert the result
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "postal_order")
            .then().getPayments((paymentsResponse -> {
            LOG.info("paymentsResponse: {}", paymentsResponse.getPayments().size());
            assertThat(paymentsResponse.getPayments().size()).isGreaterThanOrEqualTo(1);
            assertThat(paymentsResponse.getPayments().get(0).getMethod()).isEqualTo("postal order");
            assertThat(paymentsResponse.getPayments().get(0).getAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(paymentsResponse.getPayments().get(0).getChannel()).isEqualTo("digital bar");
            assertThat(paymentsResponse.getPayments().get(0).getStatus()).isEqualTo("pending");
            assertThat(paymentsResponse.getPayments().get(0).getServiceName()).isEqualTo("Digital Bar");
            assertThat(paymentsResponse.getPayments().get(0).getDateCreated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getDateUpdated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCurrency()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCaseReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getExternalProvider()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getSiteId()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentGroupReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getReportedDateOffline()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getGiroSlipNo()).isEqualTo("312131");
            FeeDto feeDto = paymentsResponse.getPayments().get(0).getFees().get(0);
            assertThat(feeDto.getCode()).isEqualTo("FEE0002");
            assertThat(feeDto.getVersion()).isEqualTo("4");
            assertThat(feeDto.getCalculatedAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(feeDto.getReference()).isNotNull();
            assertThat(feeDto.getReference()).isEqualTo("REF_123");
            assertThat(feeDto.getMemoLine()).isEqualTo("GOV - App for divorce/nullity of marriage or CP");
            assertThat(feeDto.getNaturalAccountCode()).isEqualTo("4481102159");
            assertThat(feeDto.getJurisdiction1()).isEqualTo("family");
            assertThat(feeDto.getJurisdiction2()).isEqualTo("family court");
            assertThat(feeDto.getVolume()).isEqualTo(1);
        }));

    }

    private PaymentRecordRequest getPaymentRecordRequestForPostalOrder() {
        return PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .amount(new BigDecimal("550.00"))
            .paymentMethod(PaymentMethodType.POSTAL_ORDER)
            .reference("REF_123")
            .externalProvider("middle office provider")
            .service("DIGITAL_BAR")
            .currency(CurrencyCode.GBP)
            .giroSlipNo("312131")
            .reportedDateOffline(DateTime.now().toString())
            .siteId("Y431")
            .fees(
                Arrays.asList(
                    FeeDto.feeDtoWith()
                        .calculatedAmount(new BigDecimal("550.00"))
                        .code("FEE0002")
                        .version("4")
                        .volume(1)
                        .reference("REF_123")
                        .build()
                )
            )
            .build();
    }

    @Test
    public void createBarChequePaymentRecordAndValidateSearchResults() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusSeconds(30).toDate());

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createTelephonyPayment(getPaymentRecordRequestForCheque())
            .then().created(paymentDto -> {
            assertNotNull(paymentDto.getReference());
        });


        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());

        PaymentsResponse liberataResponseOld = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceName(startDate, endDate, "cheque")
            .then().getPayments();

        PaymentsResponse liberataResponseApproach1 = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "cheque")
            .then().getPayments();

        //Comparing the response size of old and new approach
        Java6Assertions.assertThat(liberataResponseOld.getPayments().size()).
            isEqualTo(liberataResponseApproach1.getPayments().size());

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        Java6Assertions.assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response BAR Cheque payment is same");

        // search payment and assert the result
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "cheque")
            .then().getPayments((paymentsResponse -> {
            LOG.info("paymentsResponse: {}", paymentsResponse.getPayments().size());
            assertThat(paymentsResponse.getPayments().size()).isGreaterThanOrEqualTo(1);
            assertThat(paymentsResponse.getPayments().get(0).getMethod()).isEqualTo("cheque");
            assertThat(paymentsResponse.getPayments().get(0).getAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(paymentsResponse.getPayments().get(0).getChannel()).isEqualTo("digital bar");
            assertThat(paymentsResponse.getPayments().get(0).getStatus()).isEqualTo("pending");
            assertThat(paymentsResponse.getPayments().get(0).getServiceName()).isEqualTo("Digital Bar");
            assertThat(paymentsResponse.getPayments().get(0).getDateCreated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getDateUpdated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCurrency()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCaseReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getExternalProvider()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getSiteId()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentGroupReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getReportedDateOffline()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getGiroSlipNo()).isEqualTo("312131");
            FeeDto feeDto = paymentsResponse.getPayments().get(0).getFees().get(0);
            assertThat(feeDto.getCode()).isEqualTo("FEE0002");
            assertThat(feeDto.getVersion()).isEqualTo("4");
            assertThat(feeDto.getCalculatedAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(feeDto.getReference()).isNotNull();
            assertThat(feeDto.getReference()).isEqualTo("REF_123");
            assertThat(feeDto.getMemoLine()).isEqualTo("GOV - App for divorce/nullity of marriage or CP");
            assertThat(feeDto.getNaturalAccountCode()).isEqualTo("4481102159");
            assertThat(feeDto.getJurisdiction1()).isEqualTo("family");
            assertThat(feeDto.getJurisdiction2()).isEqualTo("family court");
            assertThat(feeDto.getVolume()).isEqualTo(1);
        }));

    }

    private PaymentRecordRequest getPaymentRecordRequestForCheque() {
        return PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .amount(new BigDecimal("550.00"))
            .paymentMethod(PaymentMethodType.CHEQUE)
            .reference("REF_123")
            .externalProvider("middle office provider")
            .service("DIGITAL_BAR")
            .currency(CurrencyCode.GBP)
            .giroSlipNo("312131")
            .reportedDateOffline(DateTime.now().toString())
            .siteId("Y431")
            .fees(
                Arrays.asList(
                    FeeDto.feeDtoWith()
                        .calculatedAmount(new BigDecimal("550.00"))
                        .code("FEE0002")
                        .version("4")
                        .volume(1)
                        .reference("REF_123")
                        .build()
                )
            )
            .build();
    }

    @Test
    public void createBarCardPaymentRecordAndValidateSearchResults() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
        String startDate = formatter.format(LocalDateTime.now(zoneUTC).minusSeconds(30).toDate());

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createTelephonyPayment(getPaymentRecordRequestForCard())
            .then().created(paymentDto -> {
            assertNotNull(paymentDto.getReference());
        });


        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String endDate = formatter.format(LocalDateTime.now(zoneUTC).toDate());
        PaymentsResponse liberataResponseOld = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceName(startDate, endDate, "card")
            .then().getPayments();

        PaymentsResponse liberataResponseApproach1 = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "card")
            .then().getPayments();

        //Comparing the response size of old and new approach
        Java6Assertions.assertThat(liberataResponseOld.getPayments().size()).
            isEqualTo(liberataResponseApproach1.getPayments().size());

        //Comparing the response of old and new approach
        Boolean compareResult = new HashSet<>(liberataResponseOld.getPayments()).equals(new HashSet<>(liberataResponseApproach1.getPayments()));
        Java6Assertions.assertThat(compareResult).isEqualTo(true);
        LOG.info("Comparison of old and new api end point response BAR Card payment is same");

        // search payment and assert the result
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().searchPaymentsBetweenDatesPaymentMethodServiceNameApproach1(startDate, endDate, "card")
            .then().getPayments((paymentsResponse -> {
            LOG.info("paymentsResponse: {}", paymentsResponse.getPayments().size());
            assertThat(paymentsResponse.getPayments().size()).isGreaterThanOrEqualTo(1);
            assertThat(paymentsResponse.getPayments().get(0).getMethod()).isEqualTo("card");
            assertThat(paymentsResponse.getPayments().get(0).getAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(paymentsResponse.getPayments().get(0).getChannel()).isEqualTo("digital bar");
            assertThat(paymentsResponse.getPayments().get(0).getStatus()).isEqualTo("success");
            assertThat(paymentsResponse.getPayments().get(0).getServiceName()).isEqualTo("Digital Bar");
            assertThat(paymentsResponse.getPayments().get(0).getDateCreated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getDateUpdated()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCurrency()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getCaseReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getExternalProvider()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getSiteId()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getPaymentGroupReference()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getReportedDateOffline()).isNotNull();
            assertThat(paymentsResponse.getPayments().get(0).getGiroSlipNo()).isNull();
            FeeDto feeDto = paymentsResponse.getPayments().get(0).getFees().get(0);
            assertThat(feeDto.getCode()).isEqualTo("FEE0002");
            assertThat(feeDto.getVersion()).isEqualTo("4");
            assertThat(feeDto.getCalculatedAmount()).isEqualTo(new BigDecimal("550.00"));
            assertThat(feeDto.getReference()).isNotNull();
            assertThat(feeDto.getReference()).isEqualTo("REF_123");
            assertThat(feeDto.getMemoLine()).isEqualTo("GOV - App for divorce/nullity of marriage or CP");
            assertThat(feeDto.getNaturalAccountCode()).isEqualTo("4481102159");
            assertThat(feeDto.getJurisdiction1()).isEqualTo("family");
            assertThat(feeDto.getJurisdiction2()).isEqualTo("family court");
            assertThat(feeDto.getVolume()).isEqualTo(1);
        }));

    }

    private PaymentRecordRequest getPaymentRecordRequestForCard() {
        return PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .amount(new BigDecimal("550.00"))
            .paymentMethod(PaymentMethodType.CARD)
            .reference("REF_123")
            .externalProvider("middle office provider")
            .service("DIGITAL_BAR")
            .currency(CurrencyCode.GBP)
            .reportedDateOffline(DateTime.now().toString())
            .siteId("Y431")
            .fees(
                Arrays.asList(
                    FeeDto.feeDtoWith()
                        .calculatedAmount(new BigDecimal("550.00"))
                        .code("FEE0002")
                        .version("4")
                        .volume(1)
                        .reference("REF_123")
                        .build()
                )
            )
            .build();
    }
}
