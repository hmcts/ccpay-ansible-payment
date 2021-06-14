package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.PaymentFeeDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.domain.service.OrderDomainService;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupResponse;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.StatusHistory;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.service.ReferenceDataServiceImpl;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class CaseControllerTest extends PaymentsDataUtil {

    private static final String USER_ID = UserResolverBackdoor.CASEWORKER_ID;
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;
    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    @Autowired
    protected PaymentDbBackdoor paymentDbBackdoor;

    @Autowired
    protected PaymentFeeDbBackdoor paymentFeeDbBackdoor;
    RestActions restActions;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @MockBean
    private AuthTokenGenerator authTokenGenerator;
    @Autowired
    private SiteService<Site, String> siteServiceMock;
    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private ReferenceDataServiceImpl referenceDataService;
    @Autowired
    private FeesService feesService;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private OrderDomainService orderDomainService;

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");

        List<Site> serviceReturn = Arrays.asList(Site.siteWith()
                .sopReference("sop")
                .siteId("AA99")
                .name("name")
                .service("service")
                .id(1)
                .build(),
            Site.siteWith()
                .sopReference("sop")
                .siteId("AA001")
                .name("name")
                .service("service")
                .id(1)
                .build()
        );

        when(siteServiceMock.getAllSites()).thenReturn(serviceReturn);
    }

    @Test
    @Transactional
    public void searchAllPaymentsWithCcdCaseNumberShouldReturnRequiredFieldsForVisualComponent() throws Exception {

        populateCardPaymentToDb("1");

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/payments")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse payments = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentsResponse>() {
        });

        assertThat(payments.getPayments().size()).isEqualTo(1);

        PaymentDto payment = payments.getPayments().get(0);

        assertThat(payment.getCcdCaseNumber()).isEqualTo("ccdCaseNumber1");

        assertThat(payment.getReference()).isNotBlank();
        assertThat(payment.getAmount()).isPositive();
        assertThat(payment.getDateCreated()).isNotNull();
        assertThat(payment.getCustomerReference()).isNotBlank();

        Assert.assertThat(payment.getStatusHistories(), hasItem(hasProperty("status", is("Initiated"))));
        Assert.assertThat(payment.getStatusHistories(), hasItem(hasProperty("errorCode", nullValue())));
    }

    @Test
    @Transactional
    public void shouldReturnStatusHistoryWithErrorCodeForSearchByCaseReference() throws Exception {
        String number = "1";
        StatusHistory statusHistory = StatusHistory.statusHistoryWith().status("Failed").externalStatus("failed")
            .errorCode("P0200")
            .message("Payment not found")
            .build();

        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("99.99"))
            .caseReference("Reference" + number)
            .ccdCaseNumber("ccdCaseNumber" + number)
            .description("Test payments statuses for " + number)
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA0" + number)
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("failed").build())
            .externalReference("e2kkddts5215h9qqoeuth5c0v" + number)
            .reference("RC-1519-9028-2432-000" + number)
            .statusHistories(Arrays.asList(statusHistory))
            .build();

        populateCardPaymentToDbWith(payment, number);

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/payments")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse payments = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentsResponse>() {
        });

        assertThat(payments.getPayments().size()).isEqualTo(1);

        PaymentDto paymentDto = payments.getPayments().get(0);

        assertThat(paymentDto.getCcdCaseNumber()).isEqualTo("ccdCaseNumber1");

        Assert.assertThat(paymentDto.getStatusHistories(), hasItem(hasProperty("status", is("Failed"))));
        Assert.assertThat(paymentDto.getStatusHistories(), hasItem(hasProperty("errorCode", is("P0200"))));
        Assert.assertThat(paymentDto.getStatusHistories(), hasItem(hasProperty("errorMessage", is("Payment not found"))));
    }


    @Test
    @Transactional
    public void searchAllPaymentsWithCcdCaseNumberAndCitizenCredentialsFails() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("1");

        restActions
            .withAuthorizedUser(UserResolverBackdoor.CITIZEN_ID)
            .withUserId(UserResolverBackdoor.CITIZEN_ID)
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RestActions restActions_Citizen = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        assertThat(restActions_Citizen
            .withAuthorizedUser(UserResolverBackdoor.CITIZEN_ID)
            .withUserId(UserResolverBackdoor.CITIZEN_ID)
            .get("/cases/ccdCaseNumber1/payments")
            .andExpect(status().isForbidden())
            .andReturn()).isNotNull();

    }

    @Test
    @Transactional
    public void searchAllPaymentsWithWrongCcdCaseNumberShouldReturn404() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("1");

        restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        assertThat(restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber2/payments")
            .andExpect(status().isNotFound())
            .andReturn()).isNotNull();
    }

    @Test
    @Transactional
    public void searchAllPaymentGroupsWithCcdCaseNumberShouldReturnRequiredFields() throws Exception {

        populateCardPaymentToDb("1");

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/paymentgroups")
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupResponse paymentGroups = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentGroupResponse>() {
        });

        assertThat(paymentGroups.getPaymentGroups().size()).isEqualTo(1);

    }

    @Test
    @Transactional
    public void getAllPaymentGroupsHavingFeesAndPaymentsWithCcdCaseNumberShouldReturnRequiredFields() throws Exception {

        populateCardPaymentToDb("1");

        FeeDto feeRequest = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .ccdCaseNumber("ccdCaseNumber1")
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(feeRequest))
            .build();

        restActions
            .post("/payment-groups", paymentGroupDto)
            .andReturn();

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/paymentgroups")
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupResponse paymentGroups = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentGroupResponse>() {
        });

        assertThat(paymentGroups.getPaymentGroups().size()).isEqualTo(2);

    }

    @Test
    @Transactional
    public void getAllPaymentGroupsHavingFeesAndPaymentsWithCcdCaseNumberShouldReturnRequiredFieldsWithApportionmentDetails() throws Exception {

        populateCardPaymentToDbWithApportionmentDetails("1");

        FeeDto feeRequest = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .ccdCaseNumber("ccdCaseNumber1")
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(feeRequest))
            .build();

        restActions
            .post("/payment-groups", paymentGroupDto)
            .andReturn();

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/paymentgroups")
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupResponse paymentGroups = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentGroupResponse>() {
        });
        PaymentGroupDto paymentGroupDto1 = paymentGroups.getPaymentGroups().get(0);
        FeeDto feeDto = paymentGroupDto1.getFees().get(0);

        assertThat(paymentGroups.getPaymentGroups().size()).isEqualTo(2);
        assertThat(feeDto.getApportionAmount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(feeDto.getAllocatedAmount()).isEqualTo(new BigDecimal("99.99"));

    }

    @Test
    @Transactional
    public void getAllPaymentGroupsHavingMultipleFeesAndPaymentsWithCcdCaseNumberShouldReturnRequiredFields() throws Exception {

        populateCardPaymentToDb("1");

        FeeDto feeRequest = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .ccdCaseNumber("ccdCaseNumber1")
            .build();

        FeeDto consecutiveFeeRequest = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("100.19"))
            .code("FEE313")
            .id(1)
            .version("1")
            .volume(2)
            .reference("BXsd112543")
            .ccdCaseNumber("ccdCaseNumber1")
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(feeRequest))
            .build();

        PaymentGroupDto consecutivePaymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(consecutiveFeeRequest))
            .build();

        MvcResult result1 = restActions
            .post("/payment-groups", paymentGroupDto)
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupFeeDto.getPaymentGroupReference(), consecutivePaymentGroupDto)
            .andReturn();

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/paymentgroups")
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupResponse paymentGroups = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentGroupResponse>() {
        });

        assertThat(paymentGroups.getPaymentGroups().size()).isEqualTo(2);

    }

    @Test
    @Transactional
    public void getAllPaymentGroupsHavingMultipleFeesRemissionsAndPaymentsWithCcdCaseNumberShouldReturnRequiredFields() throws Exception {

        FeeDto feeRequest = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .ccdCaseNumber("ccdCaseNumber1")
            .build();

        FeeDto consecutiveFeeRequest = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("100.19"))
            .code("FEE313")
            .id(1)
            .version("1")
            .volume(2)
            .reference("BXsd112543")
            .ccdCaseNumber("ccdCaseNumber1")
            .build();

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber("ccdCaseNumber1")
            .hwfAmount(new BigDecimal("50.00"))
            .hwfReference("HR1111")
            .caseType("tax_exception")
            .fee(feeRequest)
            .build();

        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(consecutiveFeeRequest))
            .build();

        MvcResult result1 = restActions
            .post("/payment-groups", paymentGroupDto)
            .andReturn();

        PaymentGroupDto newPaymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(feeRequest))
            .build();


        PaymentGroupDto createPaymentGroupResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        // Create a remission
        // Get fee id
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(createPaymentGroupResponseDto.getPaymentGroupReference());
        PaymentFee fee = paymentFeeDbBackdoor.findByPaymentLinkId(paymentFeeLink.getId());

        // create a partial remission
        MvcResult result2 = restActions
            .post("/payment-groups/" + createPaymentGroupResponseDto.getPaymentGroupReference() + "/fees/" + fee.getId() + "/remissions", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        // Adding another fee to the exisitng payment group
        restActions
            .put("/payment-groups/" + createPaymentGroupResponseDto.getPaymentGroupReference(), paymentGroupDto)
            .andReturn();

        // create new payment which inturns creates a payment group
        populateCardPaymentToDb("1");

        // post new fee which inturns creates a payment group
        MvcResult result12 = restActions
            .post("/payment-groups", newPaymentGroupDto)
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result12.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        // update payment group with another fee
        restActions
            .put("/payment-groups/" + paymentGroupFeeDto.getPaymentGroupReference(), paymentGroupDto)
            .andReturn();

        MvcResult result = restActions
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .get("/cases/ccdCaseNumber1/paymentgroups")
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupResponse paymentGroups = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentGroupResponse>() {
        });
        assertThat(paymentGroups.getPaymentGroups().size()).isEqualTo(3);

    }

//    @Test
//    @Transactional
//    public void validateNewlyAddedFieldsInPaymentGroupResponse() throws Exception {
//
//        stubFor(get(urlPathMatching("/fees-register/fees"))
//            .willReturn(aResponse()
//                .withStatus(200)
//                .withHeader("Content-Type", "application/json")
//                .withBody(contentsOf("fees-register-responses/allfees.json"))));
//
//        // Invoke fees-register service
//        feesService.getFeesDtoMap();
//
//        FeeDto feeRequest = FeeDto.feeDtoWith()
//            .calculatedAmount(new BigDecimal("92.19"))
//            .code("FEE0383")
//            .version("1")
//            .volume(2)
//            .reference("BXsd1123")
//            .ccdCaseNumber("ccdCaseNumber1")
//            .build();
//
//        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
//            .beneficiaryName("A partial remission")
//            .ccdCaseNumber("ccdCaseNumber1")
//            .hwfAmount(new BigDecimal("50.00"))
//            .hwfReference("HR1111")
//            .caseType("tax_exception")
//            .fee(feeRequest)
//            .build();
//
//        PaymentGroupDto paymentGroupDto = PaymentGroupDto.paymentGroupDtoWith()
//            .fees(Arrays.asList(feeRequest))
//            .build();
//
//        MvcResult result1 = restActions
//            .post("/payment-groups", paymentGroupDto)
//            .andReturn();
//
//
//        PaymentGroupDto createPaymentGroupResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
//
//        // Create a remission
//        // Get fee id
//        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(createPaymentGroupResponseDto.getPaymentGroupReference());
//        PaymentFee fee = paymentFeeDbBackdoor.findByPaymentLinkId(paymentFeeLink.getId());
//
//        // create a partial remission
//        MvcResult result2 = restActions
//            .post("/payment-groups/" + createPaymentGroupResponseDto.getPaymentGroupReference() + "/fees/" + fee.getId() + "/remissions", remissionRequest)
//            .andExpect(status().isCreated())
//            .andReturn();
//
//        MvcResult result = restActions
//            .withAuthorizedUser(USER_ID)
//            .withUserId(USER_ID)
//            .get("/cases/ccdCaseNumber1/paymentgroups")
//            .andExpect(status().isOk())
//            .andReturn();
//
//        PaymentGroupResponse paymentGroups = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentGroupResponse>() {
//        });
//
//        assertThat(paymentGroups.getPaymentGroups().size()).isEqualTo(1);
//        assertThat(paymentGroups.getPaymentGroups().get(0)
//            .getFees().get(0).getDescription()).isEqualTo("Application for a charging order");
//        System.out.println(paymentGroups.getPaymentGroups().get(0)
//            .getRemissions().get(0).getDateCreated());
//        System.out.println(new Date());
//        assertThat(paymentGroups.getPaymentGroups().get(0)
//            .getRemissions().get(0).getDateCreated().getDate()).isEqualTo(new Date().getDate());
//    }
//
//    @Test
//    @Transactional
//    public void searchPaymentGroupsWithInexistentCcdCaseNumberShouldReturn404() throws Exception {
//
//        MvcResult result = restActions
//            .withAuthorizedUser(USER_ID)
//            .withUserId(USER_ID)
//            .get("/cases/ccdCaseNumber2/paymentgroups")
//            .andExpect(status().isNotFound())
//            .andReturn();
//    }

    private FeePayApportion getFeePayApportion() {
        return FeePayApportion.feePayApportionWith()
            .apportionAmount(new BigDecimal("99.99"))
            .apportionType("AUTO")
            .feeId(1)
            .paymentId(1)
            .feeAmount(new BigDecimal("99.99"))
            .paymentId(1)
            .paymentLink(getPaymentFeeLink())
            .build();
    }

    private PaymentFeeLink getPaymentFeeLink() {
        return PaymentFeeLink.paymentFeeLinkWith()
            .id(1)
            .orgId("org-id")
            .enterpriseServiceName("enterprise-service-name")
            .paymentReference("payment-ref")
            .ccdCaseNumber("1607065108455502")
            .fees(Arrays.asList(PaymentFee.feeWith().calculatedAmount(new BigDecimal("99.99")).version("1").code("FEE0001").volume(1).build()))
            .build();
    }

    private Payment getPayment() {
        return Payment.paymentWith()
            .id(1)
            .amount(new BigDecimal("99.99"))
            .caseReference("Reference1")
            .ccdCaseNumber("1607065108455502")
            .description("Test payments statuses  ")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA09")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference("e2kkddts5215h9qqoeuth5c0v")
            .reference("RC-1519-9028-2432-0001")
            .build();
    }


}
