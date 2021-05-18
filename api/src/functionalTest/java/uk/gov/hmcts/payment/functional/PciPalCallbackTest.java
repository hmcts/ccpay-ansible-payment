package uk.gov.hmcts.payment.functional;

import com.mifmif.common.regex.Generex;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.TelephonyPaymentRequest;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.PaymentRecordRequest;
import uk.gov.hmcts.payment.api.dto.TelephonyCallbackDto;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.functional.config.LaunchDarklyFeature;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PciPalCallbackTest {
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

    private static String USER_TOKEN;
    private static String USER_TOKEN_PAYMENT;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;
    private static final String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    @Before
    public void setUp() throws Exception {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            USER_TOKEN_PAYMENT = idamService.createUserWith(CMC_CITIZEN_GROUP, "payments").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void updateTelephonyPayment_shouldReturnSucceess() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        PaymentRecordRequest paymentRecordRequest = getTelephonyPayment(telRefNumber);

        // create telephony payment using old api
        PaymentDto paymentDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(paymentRecordRequest)
            .then().getByStatusCode(201);

        assertNotNull(paymentDto.getReference());

        String paymentReference = paymentDto.getReference();

        //pci-pal callback
        TelephonyCallbackDto callbackDto = TelephonyCallbackDto.telephonyCallbackWith()
            .orderReference(paymentReference)
            .orderAmount("9999")
            .transactionResult("SUCCESS")
            .build();

        dsl.given().s2sToken(SERVICE_TOKEN)
            .when().telephonyCallback(callbackDto)
            .then().noContent();

        // retrieve payment
        paymentDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getCardPayment(paymentReference)
            .then().get();

        assertNotNull(paymentDto);
        assertEquals(paymentDto.getReference(), paymentReference);
        assertEquals(paymentDto.getExternalProvider(), "pci pal");
        assertEquals(paymentDto.getStatus(), "Success");
    }

    @Test
    public void makeAndRetrievePCIPALPayment_Success_TestShouldReturnAutoApportionedFees() {
        final String[] reference = new String[1];
        // create card payment

        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .ccdCaseNumber(ccdCaseNumber)
            .channel("telephony")
            .caseType("DIVORCE")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .build();

        PaymentGroupDto groupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(fees).build();

        AtomicReference<String> paymentReference = new AtomicReference<>();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(groupDto)
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            Assertions.assertThat(paymentGroupFeeDto).isNotNull();

            String paymentGroupReference = paymentGroupFeeDto.getPaymentGroupReference();

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().createTelephonyCardPayment(telephonyPaymentRequest, paymentGroupReference)
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                    Assertions.assertThat(paymentDto).isNotNull();
                    Assertions.assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();
                    Assertions.assertThat(paymentDto.getStatus()).isEqualTo("Initiated");
                    paymentReference.set(paymentDto.getReference());

            });

        });

        //pci-pal callback
        TelephonyCallbackDto callbackDto = TelephonyCallbackDto.telephonyCallbackWith()
            .orderReference(paymentReference.get())
            .orderAmount("120")
            .transactionResult("SUCCESS")
            .build();

        dsl.given().s2sToken(SERVICE_TOKEN)
            .when().telephonyCallback(callbackDto)
            .then().noContent();

        // retrieve payment
        PaymentDto paymentDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getCardPayment(paymentReference.get())
            .then().get();

        assertNotNull(paymentDto);
        assertEquals(paymentDto.getReference(), paymentReference.get());
        assertEquals(paymentDto.getExternalProvider(), "pci pal");
        assertEquals(paymentDto.getStatus(), "Success");

        // TEST retrieve payments, remissions and fees by payment-group-reference
        dsl.given().userToken(USER_TOKEN_PAYMENT)
            .s2sToken(SERVICE_TOKEN)
            .when().getPaymentGroups(ccdCaseNumber)
            .then().getPaymentGroups((paymentGroupsResponse -> {
            paymentGroupsResponse.getPaymentGroups().stream()
                .filter(paymentGroupDto -> paymentGroupDto.getPayments().get(0).getReference()
                    .equalsIgnoreCase(paymentReference.get()))
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
    public void makeAndRetrievePCIPALPayment_Failed_TestShouldReApportionFees() {
        final String[] reference = new String[1];
        // create card payment

        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .ccdCaseNumber(ccdCaseNumber)
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .caseType("DIVORCE")
            .build();

        PaymentGroupDto groupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(fees).build();

        AtomicReference<String> paymentReference = new AtomicReference<>();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(groupDto)
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            Assertions.assertThat(paymentGroupFeeDto).isNotNull();

            String paymentGroupReference = paymentGroupFeeDto.getPaymentGroupReference();

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().createTelephonyCardPayment(telephonyPaymentRequest, paymentGroupReference)
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                Assertions.assertThat(paymentDto).isNotNull();
                Assertions.assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();
                Assertions.assertThat(paymentDto.getStatus()).isEqualTo("Initiated");
                paymentReference.set(paymentDto.getReference());

            });

        });

        //pci-pal callback
        TelephonyCallbackDto callbackDto = TelephonyCallbackDto.telephonyCallbackWith()
            .orderReference(paymentReference.get())
            .orderAmount("120")
            .transactionResult("FAILED")
            .build();

        dsl.given().s2sToken(SERVICE_TOKEN)
            .when().telephonyCallback(callbackDto)
            .then().noContent();

        // TEST retrieve payments, remissions and fees by payment-group-reference
        dsl.given().userToken(USER_TOKEN_PAYMENT)
            .s2sToken(SERVICE_TOKEN)
            .when().getPaymentGroups(ccdCaseNumber)
            .then().getPaymentGroups((paymentGroupsResponse -> {
            //Assertions.assertThat(paymentGroupsResponse.getPaymentGroups().size()).isEqualTo(1);
            paymentGroupsResponse.getPaymentGroups().stream()
                .filter(paymentGroupDto -> paymentGroupDto.getPayments().get(0).getReference()
                    .equalsIgnoreCase(paymentReference.get()))
                .forEach(paymentGroupDto -> {

                    boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
                    if(apportionFeature) {
                        paymentGroupDto.getFees().stream()
                            .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0271"))
                            .forEach(fee -> {
                                assertEquals(BigDecimal.valueOf(20).intValue(), fee.getAmountDue().intValue());
                            });
                        paymentGroupDto.getFees().stream()
                            .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0272"))
                            .forEach(fee -> {
                                assertEquals(BigDecimal.valueOf(40).intValue(), fee.getAmountDue().intValue());
                            });
                        paymentGroupDto.getFees().stream()
                            .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0273"))
                            .forEach(fee -> {
                                assertEquals(BigDecimal.valueOf(60).intValue(), fee.getAmountDue().intValue());
                            });
                    }
                });
        }));
    }

    @Test
    public void updateTelephonyPayment_shouldReturnNotFoundWhenPaymentReferenceNotFound() {
        String telRefNumber = new Generex("TEL_PAY_\\d{8}").random();
        PaymentRecordRequest paymentRecordRequest = getTelephonyPayment(telRefNumber);

        // create telephony payment using old api
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(paymentRecordRequest)
            .then().created(paymentDto -> {
            assertNotNull(paymentDto.getReference());
        });

        //pci-pal callback
        TelephonyCallbackDto callbackDto = TelephonyCallbackDto.telephonyCallbackWith()
            .orderReference("RC-Invalid-reference")
            .orderAmount("9999")
            .transactionResult("SUCCESS")
            .build();

        dsl.given().s2sToken(SERVICE_TOKEN)
            .when().telephonyCallback(callbackDto)
            .then().notFound();
    }

    private PaymentRecordRequest getTelephonyPayment(String reference) {
        return PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .externalProvider("pci pal")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("telephony").build())
            .amount(new BigDecimal("99.99"))
            .paymentMethod(PaymentMethodType.CARD)
            .reference(reference)
            .service("CMC")
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
