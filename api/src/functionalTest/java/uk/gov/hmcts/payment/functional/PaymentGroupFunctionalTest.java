package uk.gov.hmcts.payment.functional;


import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.contract.*;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.BulkScanPaymentRequest;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.functional.config.LaunchDarklyFeature;
import uk.gov.hmcts.payment.functional.config.TestConfigProperties;
import uk.gov.hmcts.payment.functional.dsl.PaymentsTestDsl;
import uk.gov.hmcts.payment.functional.idam.IdamService;
import uk.gov.hmcts.payment.functional.s2s.S2sTokenService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static uk.gov.hmcts.payment.functional.idam.IdamService.CMC_CITIZEN_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class PaymentGroupFunctionalTest {

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
    private static final String REMISSION_REFERENCE_REGEX = "^[RM-]{3}(\\w{4}-){3}(\\w{4})";

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
    public void createNewFeeWithPaymentGroup() throws Exception {

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(getPaymentFeeGroupRequest())
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            assertThat(paymentGroupFeeDto).isNotNull();
            assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isNotNull();
            assertThat(paymentGroupFeeDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getPaymentFeeGroupRequest());
        });
    }

    @Test
    public void givenAFeeAndRemissionInPG_WhenAFeeNeedUpdatingthenFeeShouldBeAddedToExistingGroup() throws Exception {

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

                //Test add new Fee to payment group
                dsl.given().userToken(USER_TOKEN)
                    .s2sToken(SERVICE_TOKEN)
                    .when().addNewFeetoExistingPaymentGroup(getPaymentFeeGroupRequest(), paymentGroupReference)
                    .then().got(PaymentGroupDto.class, paymentGroupFeeDto -> {
                    assertThat(paymentGroupFeeDto).isNotNull();
                    assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
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
                        assertThat(paymentGroupDto.getFees().size()).isEqualTo(2);
                        assertThat(paymentGroupDto.getFees().get(1)).isEqualToComparingOnlyGivenFields(getPaymentFeeGroupRequest());

                        if(paymentGroupDto.getFees().get(0).getCode().equalsIgnoreCase("FEE0123")) {
                            BigDecimal netAmount = paymentGroupDto.getFees().get(0).getCalculatedAmount()
                                .subtract(paymentGroupDto.getRemissions().get(0).getHwfAmount());
                            assertThat(netAmount).isEqualTo(paymentGroupDto.getFees().get(0).getNetAmount());
                        }
                });

            });
    }


    @Test
    public void givenAFeeInPG_WhenABulkScanPaymentNeedsMappingthenPaymentShouldBeAddedToExistingGroup() throws Exception {

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIVORCE)
            .siteId("AA01")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN2903423425343478348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User1")
            .bankedDate(DateTime.now().toString())
            .paymentMethod(PaymentMethodType.CHEQUE)
            .paymentStatus(PaymentStatus.SUCCESS)
            .giroSlipNo("GH716376")
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("450.00"))
                .code("FEE3132")
                .version("1")
                .reference("testRef1")
                .volume(2)
                .ccdCaseNumber("1111-CCD2-3353-4464")
                .build())).build();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(paymentGroupDto)
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            assertThat(paymentGroupFeeDto).isNotNull();
            assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isNotNull();
            assertThat(paymentGroupFeeDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(paymentGroupDto);


            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().createBulkScanPayment(bulkScanPaymentRequest,paymentGroupFeeDto.getPaymentGroupReference())
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                    assertThat(paymentDto.getReference()).isNotNull();
                    assertThat(paymentDto.getStatus()).isEqualToIgnoringCase("success");
                    assertThat(paymentDto.getPaymentGroupReference()).isEqualTo(paymentGroupFeeDto.getPaymentGroupReference());
            });

        });

    }

    @Test
    public void makeAndRetrieveBulkScanPayment_TestShouldReturnAutoApportionedFees() {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(120.00))
            .service(Service.DIVORCE)
            .siteId("AA01")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN2903423425343478348")
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User1")
            .bankedDate(DateTime.now().toString())
            .paymentMethod(PaymentMethodType.CHEQUE)
            .paymentStatus(PaymentStatus.SUCCESS)
            .giroSlipNo("GH716376")
            .build();

        PaymentGroupDto paymentGrpDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(fees)
            .build();

        AtomicReference<String> paymentReference = new AtomicReference<>();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(paymentGrpDto)
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            assertThat(paymentGroupFeeDto).isNotNull();
            assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isNotNull();
            assertThat(paymentGroupFeeDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(paymentGrpDto);


            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().createBulkScanPayment(bulkScanPaymentRequest,paymentGroupFeeDto.getPaymentGroupReference())
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                assertThat(paymentDto.getReference()).isNotNull();
                assertThat(paymentDto.getStatus()).isEqualToIgnoringCase("success");
                assertThat(paymentDto.getPaymentGroupReference()).isEqualTo(paymentGroupFeeDto.getPaymentGroupReference());
                paymentReference.set(paymentDto.getReference());
            });
        });

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
    public void givenABulkScanPaymentNeedsMappingthenPaymentShouldBeCreatedWithPaymentGroup() throws Exception {

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA01")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .paymentMethod(PaymentMethodType.CHEQUE)
            .paymentStatus(PaymentStatus.SUCCESS)
            .giroSlipNo("GH716276")
            .build();


            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().createBulkScanPaymentWithPaymentGroup(bulkScanPaymentRequest)
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                assertThat(paymentDto.getReference()).isNotNull();
                assertThat(paymentDto.getStatus()).isEqualToIgnoringCase("success");

                dsl.given().userToken(USER_TOKEN)
                    .s2sToken(SERVICE_TOKEN)
                    .when().getRemissions(paymentDto.getPaymentGroupReference())
                    .then().got(PaymentGroupDto.class, paymentGroupDto -> {
                    assertThat(paymentGroupDto).isNotNull();
                    assertThat(paymentGroupDto.getPayments().get(0)).isEqualToComparingOnlyGivenFields(bulkScanPaymentRequest);
                    assertThat(paymentGroupDto.getFees().size()).isEqualTo(0);

                });

            });

    }

    @Test
    public void givenMultipleFeesAndRemissionWithPaymentInPG_WhenCaseIsSearchedShouldBeReturned() throws Exception {

        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("550.00"))
            .ccdCaseNumber(ccdCaseNumber)
            .version("1")
            .code("FEE0123")
            .build();

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber(ccdCaseNumber)
            .hwfAmount(new BigDecimal("50"))
            .hwfReference("HR1111")
            .siteId("Y431")
            .fee(getFee())
            .build();

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("550"))
            .ccdCaseNumber(ccdCaseNumber)
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .service(Service.DIVORCE)
            .siteId("AA001")
            .fees(Collections.singletonList(feeDto))
            .build();

        PaymentGroupDto groupDto = PaymentGroupDto.paymentGroupDtoWith()
        .fees(Arrays.asList(FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("250.00"))
            .code("FEE3232")
            .version("1")
            .reference("testRef")
            .volume(2)
            .ccdCaseNumber(ccdCaseNumber)
            .build())).build();


        // TEST create telephony card payment
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createCardPayment(cardPaymentRequest)
            .then().gotCreated(PaymentDto.class, paymentDto -> {
            assertThat(paymentDto).isNotNull();
            assertThat(paymentDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(feeDto);
            assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();

            String paymentGroupReference = paymentDto.getPaymentGroupReference();
            FeeDto feeDto1 = paymentDto.getFees().get(0);
            Integer feeId = feeDto1.getId();

            // TEST create retrospective remission
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().createRetrospectiveRemission(remissionRequest, paymentGroupReference, feeId)
                .then().gotCreated(RemissionDto.class, remissionDto -> {
                assertThat(remissionDto).isNotNull();
                assertThat(remissionDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
                assertThat(remissionDto.getRemissionReference().matches(REMISSION_REFERENCE_REGEX)).isTrue();
            });

            //Test add new Fee to payment group
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().addNewFeetoExistingPaymentGroup(groupDto, paymentGroupReference)
                .then().got(PaymentGroupDto.class, paymentGroupFeeDto -> {
                assertThat(paymentGroupFeeDto).isNotNull();
                assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
            });

            // TEST retrieve payments, remissions and fees by payment-group-reference
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().getPaymentGroups(ccdCaseNumber)
                .then().getPaymentGroups((paymentGroupsResponse -> {
                Assertions.assertThat(paymentGroupsResponse.getPaymentGroups().size()).isEqualTo(1);
            }));

        });
    }

    @Test
    public void givenMultipleFeesAndRemissionWithPaymentInPG_WhenCaseIsSearchedShouldBeReturnedForFinrem() throws Exception {

        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("550.00"))
            .ccdCaseNumber(ccdCaseNumber)
            .version("1")
            .code("FEE0123")
            .build();

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber(ccdCaseNumber)
            .hwfAmount(new BigDecimal("50"))
            .hwfReference("HR1111")
            .siteId("Y431")
            .fee(getFee())
            .build();

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("550"))
            .ccdCaseNumber(ccdCaseNumber)
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .service(Service.FINREM)
            .siteId("AA001")
            .fees(Collections.singletonList(feeDto))
            .build();

        PaymentGroupDto groupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal("250.00"))
                .code("FEE3232")
                .version("1")
                .reference("testRef")
                .volume(2)
                .ccdCaseNumber(ccdCaseNumber)
                .build())).build();


        // TEST create telephony card payment
        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createCardPayment(cardPaymentRequest)
            .then().gotCreated(PaymentDto.class, paymentDto -> {
            assertThat(paymentDto).isNotNull();
            assertThat(paymentDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(feeDto);
            assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();

            String paymentGroupReference = paymentDto.getPaymentGroupReference();
            FeeDto feeDto1 = paymentDto.getFees().get(0);
            Integer feeId = feeDto1.getId();

            // TEST create retrospective remission
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().createRetrospectiveRemission(remissionRequest, paymentGroupReference, feeId)
                .then().gotCreated(RemissionDto.class, remissionDto -> {
                assertThat(remissionDto).isNotNull();
                assertThat(remissionDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
                assertThat(remissionDto.getRemissionReference().matches(REMISSION_REFERENCE_REGEX)).isTrue();
            });

            //Test add new Fee to payment group
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().addNewFeetoExistingPaymentGroup(groupDto, paymentGroupReference)
                .then().got(PaymentGroupDto.class, paymentGroupFeeDto -> {
                assertThat(paymentGroupFeeDto).isNotNull();
                assertThat(paymentGroupFeeDto.getPaymentGroupReference()).isEqualTo(paymentGroupReference);
            });

            // TEST retrieve payments, remissions and fees by payment-group-reference
            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .when().getPaymentGroups(ccdCaseNumber)
                .then().getPaymentGroups((paymentGroupsResponse -> {
                Assertions.assertThat(paymentGroupsResponse.getPaymentGroups().size()).isEqualTo(1);
            }));

        });
    }

    @Test
    public void givenFeesWithPaymentInPG_WhenCaseIsSearchedShouldBeReturned() throws Exception {

        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("110.00"))
            .ccdCaseNumber(ccdCaseNumber)
            .version("1")
            .code("FEE0123")
            .description("Application for a third party debt order")
            .jurisdiction1("civil")
            .jurisdiction2("Country")
            .memoLine("Receipt of Fees")
            .naturalAccountCode("4481102145")
            .build();

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("110"))
            .ccdCaseNumber(ccdCaseNumber)
            .channel("telephony")
            .currency(CurrencyCode.GBP)
            .description("A test telephony payment")
            .provider("pci pal")
            .service(Service.DIVORCE)
            .siteId("AA007")
            .build();

        PaymentGroupDto groupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(feeDto)).build();

        dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .when().addNewFeeAndPaymentGroup(groupDto)
            .then().gotCreated(PaymentGroupDto.class, paymentGroupFeeDto -> {
            assertThat(paymentGroupFeeDto).isNotNull();

            String paymentGroupReference = paymentGroupFeeDto.getPaymentGroupReference();

            dsl.given().userToken(USER_TOKEN)
                .s2sToken(SERVICE_TOKEN)
                .returnUrl("https://www.moneyclaims.service.gov.uk")
                .when().createTelephonyCardPayment(cardPaymentRequest, paymentGroupReference)
                .then().gotCreated(PaymentDto.class, paymentDto -> {
                assertThat(paymentDto).isNotNull();
                assertThat(paymentDto.getReference().matches(PAYMENT_REFERENCE_REGEX)).isTrue();
                assertThat(paymentDto.getStatus()).isEqualTo("Initiated");

            });

        });

    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithDivorce() {
        this.addNewPaymentToExistingPaymentGroupForPCIPALAntenna(Service.DIVORCE);
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithCMC() {
        this.addNewPaymentToExistingPaymentGroupForPCIPALAntenna(Service.CMC);
    }


    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithProbate() {
        this.addNewPaymentToExistingPaymentGroupForPCIPALAntenna(Service.PROBATE);
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithFinancialRemedy() {
        this.addNewPaymentToExistingPaymentGroupForPCIPALAntenna(Service.FINREM);
    }

    @Test
    public void createCardPaymentPaymentWithMultipleFee_SurplusPayment_ForPCIPALAntenna() throws Exception {
        String ccdCaseNumber = "1111111122222222";
        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(10))
            .volume(1).version("1").calculatedAmount(new BigDecimal(10)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees(fees)
            .build();

        PaymentGroupDto paymentGroupDtoForNewGroup = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().addNewFeeAndPaymentGroup(request).then().createdWithContent(201);

        assertThat(paymentGroupDtoForNewGroup).isNotNull();
        assertThat(paymentGroupDtoForNewGroup.getFees().size()).isNotZero();
        assertThat(paymentGroupDtoForNewGroup.getFees().size()).isEqualTo(3);

        BigDecimal amount = new BigDecimal("120");
        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber(ccdCaseNumber)
            .returnURL("http://localhost")
            .build();
        TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(telephonyCardPaymentsRequest, paymentGroupDtoForNewGroup.getPaymentGroupReference())
            .then().createdTelephoneCardPaymentsResponse();

        PaymentGroupDto paymentGroupDto = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().getPaymentGroupByReference(telephonyCardPaymentsResponse.getPaymentGroupReference())
            .then().getPaymentGroupDtoByStatusCode(200);
        assertEquals(3, paymentGroupDto.getFees().size());

    }

    private void addNewPaymentToExistingPaymentGroupForPCIPALAntenna(Service service) {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getNewFee()))
            .build();
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        PaymentGroupDto paymentGroupDtoForNewGroup = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().addNewPaymentGroup(request).then().createdWithContent(201);
        assertThat(paymentGroupDtoForNewGroup).isNotNull();
        assertThat(paymentGroupDtoForNewGroup.getFees().size()).isNotZero();
        assertThat(paymentGroupDtoForNewGroup.getFees().size()).isEqualTo(1);

        PaymentGroupDto paymentGroupDtoFornewFees = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().addNewPaymentGroup(consecutiveRequest).then().getPaymentGroupDtoByStatusCode(201);
        assertThat(paymentGroupDtoFornewFees).isNotNull();
        assertThat(paymentGroupDtoFornewFees.getFees().size()).isNotZero();
        assertThat(paymentGroupDtoFornewFees.getFees().size()).isEqualTo(1);

        BigDecimal amount = new BigDecimal("200");
        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest =
            TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
                .amount(amount)
                .currency(CurrencyCode.GBP)
                .service(service)
                .siteId("AA07")
                .ccdCaseNumber("2154234356342357")
                .returnURL("http://localhost")
                .build();
        TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().createTelephonyPayment(telephonyCardPaymentsRequest, paymentGroupDtoForNewGroup.getPaymentGroupReference())
            .then().createdTelephoneCardPaymentsResponse();

        PaymentDto paymentsResponse = dsl.given().userToken(USER_TOKEN)
            .s2sToken(SERVICE_TOKEN)
            .returnUrl("https://www.moneyclaims.service.gov.uk")
            .when().getCardPayment(telephonyCardPaymentsResponse.getPaymentReference()).then().ok().get();

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(telephonyCardPaymentsRequest.getAmount().setScale(2, RoundingMode.CEILING), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount.setScale(2, RoundingMode.CEILING), paymentsResponse.getAmount());
    }

    private CardPaymentRequest getCardPaymentRequest() {
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("550"))
            .ccdCaseNumber("1111-CCD2-3333-4444")
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
            .ccdCaseNumber("1111-CCD2-3333-4444")
            .hwfAmount(new BigDecimal("50"))
            .hwfReference("HR1111")
            .siteId("Y431")
            .fee(getFee())
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
            .ccdCaseNumber("1111-CCD2-3333-4444")
            .build())).build();
    }

    private FeeDto getFee() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("550.00"))
            .ccdCaseNumber("1111-CCD2-3333-4444")
            .version("1")
            .code("FEE0123")
            .build();
    }

    private FeeDto getNewFee(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .ccdCaseNumber("1111-2222-2222-1111")
            .build();

    }

    private FeeDto getConsecutiveFee(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("100.19"))
            .code("FEE313")
            .id(1)
            .version("1")
            .volume(2)
            .reference("BXsd11253")
            .ccdCaseNumber("1111-2222-2222-1111")
            .build();
    }
}
