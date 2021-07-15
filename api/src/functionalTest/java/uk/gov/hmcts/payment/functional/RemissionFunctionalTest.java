package uk.gov.hmcts.payment.functional;


import org.apache.commons.validator.routines.UrlValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.TelephonyPaymentRequest;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.fixture.PaymentFixture;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;
import uk.gov.hmcts.payment.functional.service.PaymentTestService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class RemissionFunctionalTest {

    private static final String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";
    private static final String REMISSION_REFERENCE_REGEX = "^[RM-]{3}(\\w{4}-){3}(\\w{4})";
    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED = false;
    @Autowired
    private TestConfigProperties testProps;
    @Autowired
    private PaymentsTestDsl dsl;
    @Autowired
    private IdamService idamService;
    @Autowired
    private PaymentTestService paymentTestService;
    @Autowired
    private S2sTokenService s2sTokenService;

    @Before
    public void setUp() throws Exception {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void createUpfrontRemission() throws Exception {

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createUpfrontRemission(getRemissionRequest(false))
            .then().gotCreated(RemissionDto.class, remissionDto -> {
            assertThat(remissionDto).isNotNull();
            assertThat(remissionDto.getFee()).isEqualToComparingOnlyGivenFields(getFee());
        });
    }

    @Test
    public void createRetrospectiveRemissionAndRetrieveRemissionByPaymentGroupTest() throws Exception {

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(new BigDecimal("99.99"))
            .description("telephonyPayment")
            .caseReference("caseRef")
            .ccdCaseNumber("1234")
            .currency(CurrencyCode.GBP)
            .provider("pci pal")
            .channel("telephony")
            .caseType("LegacySearch")
            .fees(Collections.singletonList(FeeDto.feeDtoWith()
                .code("feeCode")
                .version("1")
                .calculatedAmount(new BigDecimal("100.1"))
                .build()))
            .channel("telephony")
            .provider("pci pal")
            .build();

        // TEST create telephony card payment
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(getPaymentFeeGroupRequest())
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            assertThat(paymentGroupFeeDto).isNotNull();
            assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isNotNull();
            assertThat(paymentGroupFeeDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getPaymentFeeGroupRequest());

            String paymentGroupReference = paymentGroupFeeDto.getPaymentGroupReference();
            FeeDto feeDto = paymentGroupFeeDto.getFees().get(0);
            Integer feeId = feeDto.getId();

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().createTelephonyCardPayment(telephonyPaymentRequest, paymentGroupReference)
                .then().created(paymentDto -> {
                assertTrue(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX));
                assertEquals("payment status is properly set", "Initiated", paymentDto.getStatus());
                String[] schemes = {"https"};
                UrlValidator urlValidator = new UrlValidator(schemes);
                assertNotNull(paymentDto.getLinks().getNextUrl());
                assertTrue(urlValidator.isValid(paymentDto.getLinks().getNextUrl().getHref()));
            });

            // TEST create retrospective remission
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().createRetrospectiveRemission(getRemissionRequest(false), paymentGroupReference, feeId)
                .then().gotCreated(RemissionDto.class, remissionDto -> {
                assertThat(remissionDto).isNotNull();
                assertThat(remissionDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
                assertThat(remissionDto.getRemissionReference().matches(REMISSION_REFERENCE_REGEX)).isTrue();
            });

            // TEST retrieve payments, remissions and fees by payment-group-reference
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().getRemissions(paymentGroupReference)
                .then().got(PaymentGroupDto.class, paymentGroupDto -> {
                assertThat(paymentGroupDto).isNotNull();
                assertThat(paymentGroupDto.getPayments().get(0)).isEqualToComparingOnlyGivenFields(telephonyPaymentRequest);
                assertThat(paymentGroupDto.getRemissions().get(0)).isEqualToComparingOnlyGivenFields(getRemissionRequest(false));
                assertThat(paymentGroupDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFee());

                BigDecimal netAmount = paymentGroupDto.getFees().get(0).getCalculatedAmount()
                    .subtract(paymentGroupDto.getRemissions().get(0).getHwfAmount());
                assertThat(netAmount).isEqualTo(paymentGroupDto.getFees().get(0).getNetAmount());
            });

        });
    }

    @Test
    public void create_retrospective_remission_is_retrospective_remission_false() throws Exception {
        // create a PBA payment
        String accountNumber = testProps.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest
            = PaymentFixture.aPbaPaymentRequestForProbate("90.00", "CMC");
        accountPaymentRequest.setAccountNumber(accountNumber);

        PaymentDto paymentDto = paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success")).extract().body().as(PaymentDto.class);

        //Retrieve the PaymentGroupDto For the purpose of getting the Fees.
        String paymentGroupReference = paymentDto.getPaymentGroupReference();
        PaymentGroupDto paymentGroupDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getPaymentGroup(paymentGroupReference)
            .then().getPaymentGroupDtoByStatusCode(200);
        Integer feeId =  paymentGroupDto.getFees().get(0).getId();

        // TEST create retrospective remission
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createRetrospectiveRemission(getRemissionRequest(false), paymentGroupReference, feeId)
            .then().gotCreated(RemissionDto.class, remissionDto -> {
            assertThat(remissionDto).isNotNull();
            assertThat(remissionDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
            assertThat(remissionDto.getRemissionReference().matches(REMISSION_REFERENCE_REGEX)).isTrue();
        });

        // TEST retrieve payments, remissions and fees by payment-group-reference
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getRemissions(paymentGroupReference)
            .then().got(PaymentGroupDto.class, paymentGroupDto1 -> {
            assertThat(paymentGroupDto1).isNotNull();
            assertThat(paymentGroupDto1.getPayments().get(0)).isEqualToComparingOnlyGivenFields(accountPaymentRequest);
            assertThat(paymentGroupDto1.getRemissions().get(0)).isEqualToComparingOnlyGivenFields(getRemissionRequest(false));
            assertThat(paymentGroupDto1.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFee());

            BigDecimal netAmount = paymentGroupDto1.getFees().get(0).getCalculatedAmount()
                .subtract(paymentGroupDto1.getRemissions().get(0).getHwfAmount());
            assertThat(netAmount).isEqualTo(paymentGroupDto1.getFees().get(0).getNetAmount());
        });
    }

    @Test
    public void create_retrospective_remission_is_retrospective_remission_true() throws Exception {
        // create a PBA payment
        String accountNumber = testProps.existingAccountNumber;
        final CreditAccountPaymentRequest accountPaymentRequest
            = PaymentFixture.aPbaPaymentRequestForProbate("90.00", "CMC");
        accountPaymentRequest.setAccountNumber(accountNumber);

        PaymentDto paymentDto = paymentTestService.postPbaPayment(USER_TOKEN, SERVICE_TOKEN, accountPaymentRequest)
            .then()
            .statusCode(CREATED.value())
            .body("status", equalTo("Success")).extract().body().as(PaymentDto.class);

        //Retrieve the PaymentGroupDto For the purpose of getting the Fees.
        String paymentGroupReference = paymentDto.getPaymentGroupReference();
        PaymentGroupDto paymentGroupDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getPaymentGroup(paymentGroupReference)
            .then().getPaymentGroupDtoByStatusCode(200);
        Integer feeId =  paymentGroupDto.getFees().get(0).getId();

        // TEST create retrospective remission
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().createRetrospectiveRemission(getRemissionRequest(true), paymentGroupReference, feeId)
            .then().gotCreated(RemissionDto.class, remissionDto -> {
            assertThat(remissionDto).isNotNull();
            assertThat(remissionDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
            assertThat(remissionDto.getRemissionReference().matches(REMISSION_REFERENCE_REGEX)).isTrue();
        });

        // TEST retrieve payments, remissions and fees by payment-group-reference
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().getRemissions(paymentGroupReference)
            .then().got(PaymentGroupDto.class, paymentGroupDto1 -> {
            assertThat(paymentGroupDto1).isNotNull();
            assertThat(paymentGroupDto1.getPayments().get(0)).isEqualToComparingOnlyGivenFields(accountPaymentRequest);
            assertThat(paymentGroupDto1.getRemissions().get(0)).isEqualToComparingOnlyGivenFields(getRemissionRequest(true));
            assertThat(paymentGroupDto1.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFee());

            BigDecimal netAmount = paymentGroupDto1.getFees().get(0).getCalculatedAmount()
                .subtract(paymentGroupDto1.getRemissions().get(0).getHwfAmount());
            assertThat(netAmount).isEqualTo(paymentGroupDto1.getFees().get(0).getNetAmount());

            BigDecimal amountDue = paymentGroupDto1.getFees().get(0).getCalculatedAmount()
                .subtract(paymentGroupDto1.getRemissions().get(0).getHwfAmount())
                .subtract(paymentGroupDto1.getPayments().get(0).getAmount());
            assertThat(amountDue).isEqualTo(paymentGroupDto1.getFees().get(0).getAmountDue());
        });
    }


    private CardPaymentRequest getCardPaymentRequest() {
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("550"))
            .ccdCaseNumber("1111-2222-3333-4444")
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .service("DIVORCE")
            .caseType("DIVORCE_ExceptionRecord")
            .fees(Collections.singletonList(getFee()))
            .build();
    }

    private RemissionRequest getRemissionRequest(final boolean retroRemissionFlag) {
        return RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber("1111-2222-3333-4444")
            .hwfAmount(new BigDecimal("50"))
            .hwfReference("HR1111")
            .caseType("DIVORCE_ExceptionRecord")
            .fee(getFee())
            .isRetroRemission(retroRemissionFlag)
            .build();
    }

    private FeeDto getFee() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("550.00"))
            .ccdCaseNumber("1111-2222-3333-4444")
            .version("1")
            .code("FEE0123")
            .build();
    }

    private PaymentGroupDto getPaymentFeeGroupRequest() {
        return PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("250.00"))
                .code("FEE3232")
                .version("1")
                .reference("testRef")
                .volume(2)
                .build())).build();
    }


}
