package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.RandomUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.BulkScanPaymentRequest;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class PaymentGroupControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    private UserResolverBackdoor userRequestAuthorizer;


    private static final String USER_ID = UserResolverBackdoor.CITIZEN_ID;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    protected PaymentDbBackdoor paymentDbBackdoor;

    @Autowired
    protected PaymentFeeDbBackdoor paymentFeeDbBackdoor;

    @Autowired
    private SiteService<Site, String> siteServiceMock;

    @Autowired
    private PaymentDbBackdoor db;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    private final static String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.gooooogle.com");

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
    public void retrievePaymentsRemissionsAndFeeByGroupReferenceTest() throws Exception {
        CardPaymentRequest cardPaymentRequest = getCardPaymentRequest();

        MvcResult result1 = restActions
            .withReturnUrl("https://www.google.com")
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();


        PaymentDto createPaymentResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);

        // Create a remission
        // Get fee id
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(createPaymentResponseDto.getPaymentGroupReference());
        PaymentFee fee = paymentFeeDbBackdoor.findByPaymentLinkId(paymentFeeLink.getId());

        // create a partial remission
        MvcResult result2 = restActions
            .post("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference() + "/fees/" + fee.getId() + "/remissions", getRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();

        // Retrieve payment by payment group reference
        MvcResult result3 = restActions
            .get("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        PaymentDto paymentDto = paymentGroupDto.getPayments().get(0);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentDto).isEqualToComparingOnlyGivenFields(cardPaymentRequest);
        assertThat(paymentDto.getReference()).isEqualTo(createPaymentResponseDto.getReference());
        FeeDto feeDto = paymentGroupDto.getFees().stream().filter(f -> f.getCode().equals("FEE0123")).findAny().get();
        assertThat(feeDto).isEqualToComparingOnlyGivenFields(getFee());
        assertThat(feeDto.getNetAmount()).isEqualTo(new BigDecimal("200.00"));
    }

    @Test
    public void retrievePaymentsAndFeesByPaymentGroupReferenceTest() throws Exception {
        CardPaymentRequest cardPaymentRequest = getCardPaymentRequest();

        MvcResult result1 = restActions
            .withReturnUrl("https://www.google.com")
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();


        PaymentDto createPaymentResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);

        // Retrieve payment by payment group reference
        MvcResult result3 = restActions
            .get("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        PaymentDto paymentDto = paymentGroupDto.getPayments().get(0);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentDto).isEqualToComparingOnlyGivenFields(cardPaymentRequest);
        assertThat(paymentGroupDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFee());
    }

    @Test
    public void retrievePaymentsAndFeesByPaymentGroupReferenceWithApportionmentDetails() throws Exception {
        CardPaymentRequest cardPaymentRequest = getCardPaymentRequest();

        MvcResult result1 = restActions
            .withReturnUrl("https://www.google.com")
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();


        PaymentDto createPaymentResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);

        // Retrieve payment by payment group reference
        MvcResult result3 = restActions
            .get("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        PaymentDto paymentDto = paymentGroupDto.getPayments().get(0);
        FeeDto feeDto = paymentGroupDto.getFees().get(0);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(feeDto).isNotNull();
        assertThat(paymentDto).isEqualToComparingOnlyGivenFields(cardPaymentRequest);
        assertThat(paymentGroupDto.getFees().get(0)).isEqualToComparingOnlyGivenFields(getFeeWithApportionDetails());
    }

    @Test
    public void retrievePaymentsRemissionsAndFeesWithInvalidPaymentGroupReferenceShouldFailTest() throws Exception {
        restActions
            .get("/payment-groups/1011-10000000001")
            .andExpect(status().isNotFound());
    }

    @Test
    public void addNewFeewithNoPaymentGroupTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().get(0).getCalculatedAmount()).isEqualTo(new BigDecimal("92.19"));
        assertThat(paymentGroupDto.getFees().get(0).getNetAmount()).isEqualTo(new BigDecimal("92.19"));

        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentGroupDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto1 = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto1).isNotNull();
        assertThat(paymentGroupDto1.getFees().size()).isNotZero();
        assertThat(paymentGroupDto1.getFees().size()).isEqualTo(1);

    }

    @Test
    public void addNewFeewithPaymentGroupWhenApportionFlagIsOn() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().get(0).getCalculatedAmount()).isEqualTo(new BigDecimal("92.19"));
        assertThat(paymentGroupDto.getFees().get(0).getNetAmount()).isEqualTo(new BigDecimal("92.19"));

        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentGroupDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto1 = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto1).isNotNull();
        assertThat(paymentGroupDto1.getFees().size()).isNotZero();
        assertThat(paymentGroupDto1.getFees().size()).isEqualTo(1);

    }

    @Test
    public void addNewFeewithNoPaymentGroupNegativeTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getInvalidFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    }

    @Test
    public void addNewFeetoExistingPaymentGroupTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);

    }

    @Test
    public void addNewFeewithNoCaseDetailsTest() throws Exception {

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFeeWithOutCaseDetails()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void addNewFeewithCcdCaseNumberOnlyTest() throws Exception {

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFeeWithCCDcasenumberOnly()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    public void addNewFeewithCaseReferenceOnlyTest() throws Exception {

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFeeWithCaseReferenceOnly()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Test
    public void attachNewFeewithNoCaseDetailsTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFeeWithOutCaseDetails()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void attachNewFeewithCcdCaseNumberOnlyTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFeeWithCCDcasenumberOnly()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void attachNewFeewithCaseReferenceOnlyTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFeeWithCaseReferenceOnly()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();
    }


    @Test
    public void addNewFeetoExistingPaymentGroupCountTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getConsecutiveFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupFeeDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentGroupDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto1 = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto1).isNotNull();
        assertThat(paymentGroupDto1.getFees().size()).isNotZero();
        assertThat(paymentGroupDto1.getFees().size()).isEqualTo(2);

    }


    @Test
    public void retrievePaymentsAndFeesByPaymentGroupReferenceAfterFeeAdditionTest() throws Exception {
        CardPaymentRequest cardPaymentRequest = getCardPaymentRequest();

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getConsecutiveFee()))
            .build();

        MvcResult result1 = restActions
            .withReturnUrl("https://www.google.com")
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();


        PaymentDto createPaymentResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);

        // Create a remission
        // Get fee id
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(createPaymentResponseDto.getPaymentGroupReference());
        PaymentFee fee = paymentFeeDbBackdoor.findByPaymentLinkId(paymentFeeLink.getId());

        // create a partial remission
        MvcResult result2 = restActions
            .post("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference() + "/fees/" + fee.getId() + "/remissions", getRemissionRequest())
            .andExpect(status().isCreated())
            .andReturn();

        // Adding another fee to the exisitng payment group
        restActions
            .put("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference(),request)
            .andReturn();


        // Retrieve payment by payment group reference
        MvcResult result3 = restActions
            .get("/payment-groups/" + createPaymentResponseDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        PaymentDto paymentDto = paymentGroupDto.getPayments().get(0);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentDto).isEqualToComparingOnlyGivenFields(cardPaymentRequest);
        assertThat(paymentDto.getReference()).isEqualTo(createPaymentResponseDto.getReference());
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(2);
        FeeDto feeDto = paymentGroupDto.getFees().stream().filter(f -> f.getCode().equals("FEE0123")).findAny().get();
        assertThat(feeDto).isEqualToComparingOnlyGivenFields(getFee());
        assertThat(feeDto.getNetAmount()).isEqualTo(new BigDecimal("200.00"));

    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupTest() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.DIVORCE)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsFinrem() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsDivorce() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.DIVORCE)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsProbate() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.PROBATE)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsCMC() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.CMC)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void shouldThrowPaymentExceptionWhilePassingUnsupportedServiceType() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FPL)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupTestWhenChannelAndProviderIsEmpty() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.DIVORCE)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    public void addInvalidBulkScanPayment() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA07")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void addInvalidDateBulkScanPayment() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate("23-04-2019")
            .paymentStatus(PaymentStatus.SUCCESS)
            .giroSlipNo("BCH82173823")
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void addNoPaymentMethodBulkScanPayment() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .paymentStatus(PaymentStatus.SUCCESS)
            .giroSlipNo("BCH82173823")
            .bankedDate("23-04-2019")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }


    @Test
    public void addNoDCNBulkScanPayment() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .paymentStatus(PaymentStatus.CREATED)
            .giroSlipNo("BCH82173823")
            .bankedDate("23-04-2019")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void addNullRequestorBulkScanPayment() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .paymentStatus(PaymentStatus.SUCCESS)
            .build();

        bulkScanPaymentRequest.setAmount(new BigDecimal(100.00));
        bulkScanPaymentRequest.setSiteId("AA001");
        bulkScanPaymentRequest.setCurrency(CurrencyCode.GBP);
        bulkScanPaymentRequest.setDocumentControlNumber("DCN293842342342834278348");
        bulkScanPaymentRequest.setPayerName("CCD User");
        bulkScanPaymentRequest.setBankedDate(DateTime.now().toString());
        bulkScanPaymentRequest.setGiroSlipNo("BCH82173823");
        bulkScanPaymentRequest.setPaymentMethod(PaymentMethodType.CHEQUE);

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void addvalidBulkScanPayment() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertTrue(paymentsResponse.getCcdCaseNumber().equals("1231-1231-3453-4333"));
    }

    @Test
    public void shouldThrowErrorWhenInvalidSiteId() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("aaaa")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void shouldReturnSuccessWhenExternalProviderIsExela() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .externalProvider("exela")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertTrue(paymentsResponse.getCcdCaseNumber().equals("1231-1231-3453-4333"));
    }

    @Test
    public void addInvalidNewBulkScanPayment() throws Exception{

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA07")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void addNewvalidBulkScanPayment() throws Exception{

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .giroSlipNo("BCH82173823")
            .bankedDate(DateTime.now().toString())
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertTrue(paymentsResponse.getCcdCaseNumber().equals("1231-1231-3453-4333"));
    }

    @Test
    public void addNewvalidBulkScanPaymentWithExceptionRecordAndCCDCaseNumber() throws Exception{

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .exceptionRecord("1231-1231-3453-5333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .giroSlipNo("BCH82173823")
            .bankedDate(DateTime.now().toString())
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertTrue(paymentsResponse.getCcdCaseNumber().equals("1231-1231-3453-4333"));
        assertTrue(paymentsResponse.getCaseReference().equals("1231-1231-3453-5333"));
    }

    @Test
    public void shouldThrowErrorWhenSiteIdIsInvalid() throws Exception{

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("aaaaa")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .giroSlipNo("BCH82173823")
            .bankedDate(DateTime.now().toString())
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    public void shouldThrowErrorWhenBothCCDNumberAndExceptionRecordIsEmpty() throws Exception{

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA07")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .giroSlipNo("BCH82173823")
            .bankedDate(DateTime.now().toString())
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void shouldReturnSuccessWhenPaymentProviderIsExela() throws Exception{

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .giroSlipNo("BCH82173823")
            .bankedDate(DateTime.now().toString())
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .externalProvider("exela")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertTrue(paymentsResponse.getCcdCaseNumber().equals("1231-1231-3453-4333"));
    }

    @Test
    public void createBulkScanPaymentWithMultipleFee_ExactPayment() throws Exception {

        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees(fees)
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(120.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .externalProvider("exela")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        List<PaymentFee> savedfees = db.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        assertEquals(new BigDecimal(0), savedfees.get(0).getAmountDue());
        assertEquals(new BigDecimal(0), savedfees.get(1).getAmountDue());
        assertEquals(new BigDecimal(0), savedfees.get(2).getAmountDue());
    }

    @Test
    public void createBulkScanPaymentWithMultipleFee_ShortfallPayment() throws Exception {

        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(30))
            .volume(1).version("1").calculatedAmount(new BigDecimal(30)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees(fees)
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(120.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .externalProvider("exela")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        List<PaymentFee> savedfees = db.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        assertEquals(new BigDecimal(0), savedfees.get(0).getAmountDue());
        assertEquals(new BigDecimal(0), savedfees.get(1).getAmountDue());
        assertEquals(new BigDecimal(10), savedfees.get(2).getAmountDue());
    }

    @Test
    public void createBulkScanPaymentWithMultipleFee_SurplusPayment() throws Exception {

        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

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

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        BulkScanPaymentRequest bulkScanPaymentRequest = BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(120.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA001")
            .currency(CurrencyCode.GBP)
            .documentControlNumber("DCN293842342342834278348")
            .ccdCaseNumber(ccdCaseNumber)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .externalProvider("exela")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments", bulkScanPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        List<PaymentFee> savedfees = db.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        assertEquals(new BigDecimal(0), savedfees.get(0).getAmountDue());
        assertEquals(new BigDecimal(0), savedfees.get(1).getAmountDue());
        assertEquals(new BigDecimal(-10), savedfees.get(2).getAmountDue());
    }


    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithDivorce() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.DIVORCE)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithCMC() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(true);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("120");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.CMC)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithProbate() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(true);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.PROBATE)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithUnSupportedServiceName() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.DIGITAL_BAR)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaWithFinrem() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(true);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentDto.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(cardPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaThrowsException() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenPaymentProviderIsEmpty() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenPaymentChannelIsEmpty() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenPaymentProviderAndPaymentChannelIsEmpty() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenPaymentProviderIsOtherThanPCIPAL() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("gov pay")
            .channel("telephony")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenPaymentChannelIsOtherThanTelephony() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("online")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenPaymentChannelAndProviderIsInvalid() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(false);
        PaymentGroupDto consecutiveRequest = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getConsecutiveFee())).build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupDto).isNotNull();
        assertThat(paymentGroupDto.getFees().size()).isNotZero();
        assertThat(paymentGroupDto.getFees().size()).isEqualTo(1);

        MvcResult result2 = restActions
            .put("/payment-groups/" + paymentGroupDto.getPaymentGroupReference(), consecutiveRequest)
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupFeeDto = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        assertThat(paymentGroupFeeDto).isNotNull();
        assertThat(paymentGroupFeeDto.getFees().size()).isNotZero();
        assertThat(paymentGroupFeeDto.getFees().size()).isEqualTo(2);


        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("gov pay")
            .channel("online")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void createCardPaymentPaymentWithMultipleFee_SurplusPayment_ForPCIPALAntenna() throws Exception {

        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();
        when(featureToggler.getBooleanValue("pci-pal-antenna-feature",false)).thenReturn(true);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

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

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        BigDecimal amount = new BigDecimal("120");
        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .service(Service.FINREM)
            .siteId("AA07")
            .ccdCaseNumber(ccdCaseNumber)
            .provider("pci pal")
            .channel("telephony")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments-antenna", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        List<PaymentFee> savedfees = db.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        assertEquals(new BigDecimal(10), savedfees.get(0).getAmountDue());
        assertEquals(new BigDecimal(40), savedfees.get(1).getAmountDue());
        assertEquals(new BigDecimal(60), savedfees.get(2).getAmountDue());
    }

    private CardPaymentRequest getCardPaymentRequest() {
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("250.00"))
            .description("description")
            .ccdCaseNumber("1111-2222-2222-1111")
            .service(Service.DIVORCE)
            .currency(CurrencyCode.GBP)
            .provider("pci pal")
            .channel("telephony")
            .siteId("AA001")
            .fees(Collections.singletonList(getFee()))
            .build();
    }

    private RemissionRequest getRemissionRequest() {
        return RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("A partial remission")
            .ccdCaseNumber("1111-2222-2222-1111")
            .hwfAmount(new BigDecimal("50.00"))
            .hwfReference("HR1111")
            .siteId("AA001")
            .fee(getFee())
            .build();
    }

    private FeeDto getFee() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("250.00"))
            .version("1")
            .code("FEE0123")
            .build();
    }

    private FeeDto getFeeWithApportionDetails() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("250.00"))
            .version("1")
            .code("FEE0123")
            .apportionAmount(new BigDecimal("250.00"))
            .allocatedAmount(new BigDecimal("250.00"))
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

    private FeeDto getNewFeeWithOutCaseDetails(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .build();

    }

    private FeeDto getNewFeeWithCCDcasenumberOnly(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .ccdCaseNumber("1111-2222-2222-1111")
            .build();

    }

    private FeeDto getNewFeeWithCaseReferenceOnly(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .reference("BXsd1123")
            .build();

    }

    private FeeDto getInvalidFee(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
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

    private BulkScanPaymentRequest getInvalidBulkScanRequest(){
        return BulkScanPaymentRequest.createBulkScanPaymentWith()
            .amount(new BigDecimal(100.00))
            .service(Service.DIGITAL_BAR)
            .siteId("AA07")
            .build();
    }
}
