package uk.gov.hmcts.payment.functional;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class RemissionFunctionalTest {

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
    private static final String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";
    private static final String REMISSION_REFERENCE_REGEX = "^[RM-]{3}(\\w{4}-){3}(\\w{4})";

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
            .when().createUpfrontRemission(getRemissionRequest())
            .then().gotCreated(RemissionDto.class, remissionDto -> {
            assertThat(remissionDto).isNotNull();
            assertThat(remissionDto.getFee()).isEqualToComparingOnlyGivenFields(getFee());
        });
    }

    @Test
    public void createRetrospectiveRemissionAndRetrieveRemissionByPaymentGroupTest() throws Exception {

        // TEST create telephony card payment
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createCardPayment(getCardPaymentRequest())
            .then().gotCreated(PaymentDto.class, paymentDto -> {
                assertThat(paymentDto).isNotNull();
                assertThat(paymentDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFee());
                assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();

                String paymentGroupReference = paymentDto.getPaymentGroupReference();
                FeeDto feeDto = paymentDto.getFees().get(0);
                Integer feeId = feeDto.getId();

                // TEST create retrospective remission
                dsl.given().userToken(USER_TOKEN)
                    .s2sToken(SERVICE_TOKEN)
                    .when().createRetrospectiveRemission(getRemissionRequest(), paymentGroupReference, feeId)
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
                        assertThat(paymentGroupDto.getPayments().get(0)).isEqualToComparingOnlyGivenFields(getCardPaymentRequest());
                        assertThat(paymentGroupDto.getRemissions().get(0)).isEqualToComparingOnlyGivenFields(getRemissionRequest());
                        assertThat(paymentGroupDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFee());

                        BigDecimal netAmount = paymentGroupDto.getFees().get(0).getCalculatedAmount()
                            .subtract(paymentGroupDto.getRemissions().get(0).getHwfAmount());
                        assertThat(netAmount).isEqualTo(paymentGroupDto.getFees().get(0).getNetAmount());
                });

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
            .service(Service.DIVORCE)
            .siteId("AA001")
            .fees(Collections.singletonList(getFee()))
            .build();
    }

    private RemissionRequest getRemissionRequest() {
        return RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber("1111-2222-3333-4444")
            .hwfAmount(new BigDecimal("50"))
            .hwfReference("HR1111")
            .siteId("Y431")
            .fee(getFee())
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


}
