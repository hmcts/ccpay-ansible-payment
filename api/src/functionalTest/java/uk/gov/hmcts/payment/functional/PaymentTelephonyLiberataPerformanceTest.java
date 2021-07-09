package uk.gov.hmcts.payment.functional;


import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Java6Assertions;
import org.joda.time.DateTime;
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
import uk.gov.hmcts.payment.api.contract.TelephonyPaymentRequest;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.TelephonyCallbackDto;
import uk.gov.hmcts.payment.functional.config.LaunchDarklyFeature;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;
import uk.gov.hmcts.payment.functional.service.PaymentTestService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PaymentTelephonyLiberataPerformanceTest {

    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private PaymentsTestDsl dsl;

    @Autowired
    private IdamService idamService;

    @Autowired
    private S2sTokenService s2sTokenService;

    @Autowired
    private PaymentTestService paymentTestService;

    @Autowired
    private LaunchDarklyFeature featureToggler;

    private static String USER_TOKEN;
    private static String USER_TOKEN_PAYMENT;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;
    private static final String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";
    private static final String REMISSION_REFERENCE_REGEX = "^[RM-]{3}(\\w{4}-){3}(\\w{4})";
    private static final Logger LOG = LoggerFactory.getLogger(PaymentRecordFunctionalTest.class);

   @Before
    public void setUp() throws Exception {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            USER_TOKEN_PAYMENT = idamService.createUserWith(CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void telephonyPaymentLiberataValidation() throws Exception {

        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("550.00"))
            .ccdCaseNumber(ccdCaseNumber)
            .version("4")
            .code("FEE0002")
            .description("Application for a third party debt order")
            .build();

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(new BigDecimal("550"))
            .ccdCaseNumber(ccdCaseNumber)
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .caseType("DIVORCE")
            .build();

        PaymentGroupDto groupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(feeDto)).build();

        AtomicReference<String> paymentReference = new AtomicReference<>();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(groupDto)
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            assertThat(paymentGroupFeeDto).isNotNull();

            String paymentGroupReference = paymentGroupFeeDto.getPaymentGroupReference();

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().createTelephonyCardPayment(telephonyPaymentRequest, paymentGroupReference)
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                assertThat(paymentDto).isNotNull();
                assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();
                assertThat(paymentDto.getStatus()).isEqualTo("Initiated");
                paymentReference.set(paymentDto.getReference());
            });
            // pci-pal callback
             TelephonyCallbackDto callbackDto = TelephonyCallbackDto.telephonyCallbackWith()
                .orderReference(paymentReference.get())
                .orderAmount("550")
                .transactionResult("SUCCESS")
                .build();

             dsl.given().s2sToken(SERVICE_TOKEN)
                .when().telephonyCallback(callbackDto)
                .then().noContent();

            // Get pba payments by ccdCaseNumber
            PaymentsResponse liberataResponseOld = paymentTestService.getPbaPaymentsByCCDCaseNumber(SERVICE_TOKEN, telephonyPaymentRequest.getCcdCaseNumber())
                .then()
                .statusCode(OK.value()).extract().as(PaymentsResponse.class);

            PaymentsResponse liberataResponseApproach1 = paymentTestService.getPbaPaymentsByCCDCaseNumberApproach1(SERVICE_TOKEN, telephonyPaymentRequest.getCcdCaseNumber())
                .then()
                .statusCode(OK.value()).extract().as(PaymentsResponse.class);

            //Get size and compare
             Java6Assertions.assertThat(liberataResponseApproach1.getPayments().size()).isGreaterThanOrEqualTo(1);

            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getPaymentReference()).isNotNull();
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getAmount()).isEqualTo(new BigDecimal("550.00"));
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getDateCreated()).isNotNull();
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getDateUpdated()).isNotNull();
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getCurrency()).isNotNull();
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getCcdCaseNumber()).isEqualTo(ccdCaseNumber);
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getChannel()).isEqualTo("telephony");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getMethod()).isEqualTo("card");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getExternalProvider()).isEqualTo("pci pal");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getStatus()).isEqualTo("success");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getSiteId()).isEqualTo("ABA1");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getServiceName()).isEqualTo("Divorce");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getFees().get(0).getApportionedPayment()).isEqualTo("550.00");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getFees().get(0).getCalculatedAmount()).isEqualTo("550.00");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getFees().get(0).getMemoLine()).isEqualTo("GOV - App for divorce/nullity of marriage or CP");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getFees().get(0).getNaturalAccountCode()).isEqualTo("4481102159");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getFees().get(0).getJurisdiction1()).isEqualTo("family");
            Java6Assertions.assertThat(liberataResponseApproach1.getPayments().get(0).getFees().get(0).getJurisdiction2()).isEqualTo("family court");

        });

    }
}
