package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.RandomUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.PaymentFeeDbBackdoor;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.*;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.*;
import uk.gov.hmcts.payment.api.external.client.dto.State;
import uk.gov.hmcts.payment.api.external.client.dto.TelephonyProviderAuthorisationResponse;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.PciPalPaymentService;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.model.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.NoServiceFoundException;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationHealthApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @MockBean
    private PciPalPaymentService pciPalPaymentService;

    @Autowired
    protected PaymentFeeDbBackdoor paymentFeeDbBackdoor;

    @MockBean
    private SiteService<Site, String> siteServiceMock;

    @InjectMocks
    private PaymentGroupController paymentGroupController;

    @MockBean
    @Qualifier("restTemplatePaymentGroup")
    private RestTemplate restTemplatePaymentGroup;

    @MockBean
    private ReferenceDataService referenceDataService;

    @Autowired
    private PaymentDbBackdoor db;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private ServiceAuthorisationApi serviceAuthorisationApi;

    @MockBean
    private ServiceAuthorisationHealthApi serviceAuthorisationHealthApi;

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

        when(referenceDataService.getSiteIDs()).thenReturn(SiteDTO.fromSiteList(serviceReturn));
    }

    @Test
    @Transactional
    public void retrievePaymentsRemissionsAndFeeByGroupReferenceTest() throws Exception {
        CardPaymentRequest cardPaymentRequest = getCardPaymentRequest();

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result1 = restActions
            .withHeader("service-callback-url", "http://payments.com")
            .post("/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated())
            .andReturn();


        PaymentDto createPaymentResponseDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);

        // Create a remission
        // Get fee id
        PaymentFeeLink paymentFeeLink = paymentDbBackdoor.findByReference(createPaymentResponseDto.getPaymentGroupReference());
        PaymentFee fee = paymentFeeDbBackdoor.findByPaymentLinkId(paymentFeeLink.getId());


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AAD7")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

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

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result1 = restActions
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

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result1 = restActions
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

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AAD7")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        CardPaymentRequest cardPaymentRequest = getCardPaymentRequest();

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getConsecutiveFee()))
            .build();

        MvcResult result1 = restActions
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

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result3 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
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
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsFinrem() throws Exception {
        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();


        BigDecimal amount = new BigDecimal("200");

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("FinancialRemedyContested")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("Financial Remedy")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
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
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    private PaymentGroupDto addNewPaymentToExistingPaymentGroup() throws Exception {
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees(Arrays.asList(getNewFee()))
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
        return paymentGroupDto;
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsDivorce() throws Exception {
        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();


        BigDecimal amount = new BigDecimal("200");

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
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
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsProbate() throws Exception {
        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();


        BigDecimal amount = new BigDecimal("200");

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("PROBATE_ExceptionRecord")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("Probate")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
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
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymenttoExistingPaymentGroupWhenServiceTypeIsCMC() throws Exception {
        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();


        BigDecimal amount = new BigDecimal("200");

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
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
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyPaymentRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addnewPaymentgroupWithCaseTypeShouldReturnSuccess() throws Exception {

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());


        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AAD7")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isCreated());
    }

    @Test
    public void shouldThrowNoServiceExceptionWithCaseType() throws Exception {

        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();

        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        when(referenceDataService.getOrganisationalDetail(anyString(),any())).thenThrow(new NoServiceFoundException("No Service found for given CaseType"));
        restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isNotFound())
            .andExpect(content().string("No Service found for given CaseType"));
    }

    @Test
    public void shouldThrowGatewyTimeOutExceptionWithCaseType() throws Exception {

        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();

        BigDecimal amount = new BigDecimal("200");

        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .provider("pci pal")
            .channel("telephony")
            .build();

        when(referenceDataService.getOrganisationalDetail(anyString(),any())).thenThrow(new GatewayTimeoutException("Unable to retrieve service information. Please try again later"));
        restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", cardPaymentRequest)
            .andExpect(status().isGatewayTimeout())
            .andExpect(content().string("Unable to retrieve service information. Please try again later"));
    }


    @Test
    public void addNewPaymenttoExistingPaymentGroupTestWhenChannelAndProviderIsEmpty() throws Exception {
        PaymentGroupDto paymentGroupDto = addNewPaymentToExistingPaymentGroup();


        BigDecimal amount = new BigDecimal("200");

        TelephonyPaymentRequest telephonyPaymentRequest = TelephonyPaymentRequest.createTelephonyPaymentRequestDtoWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .description("Test cross field validation")
            .caseType("tax_exception")
            .ccdCaseNumber("2154-2343-5634-2357")
            .build();


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result3 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/card-payments", telephonyPaymentRequest)
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
    public void testValidBulkScanPaymentForStrategic() throws Exception{
        when(this.restTemplatePaymentGroup.exchange(anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            eq(String.class), any(Map.class)))
            .thenReturn(new ResponseEntity(HttpStatus.OK));

        when(featureToggler.getBooleanValue("prod-strategic-fix",false)).thenReturn(true);

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);

        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals("1231-1231-3453-4333", paymentsResponse.getCcdCaseNumber());
        assertNotNull(paymentsResponse.getPaymentAllocation());
        assertTrue(paymentsResponse.getPaymentAllocation().get(0).getPaymentAllocationStatus().getName().equalsIgnoreCase("Allocated"));

        MvcResult duplicateRequest = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(duplicateRequest.getResponse().getContentAsString().contains("Bulk scan payment already exists for DCN = DCN293842342342834278348"));
    }

    @Test
    public void testValidAndDuplicateTransferredBulkScanPayments() throws Exception{
        when(featureToggler.getBooleanValue("prod-strategic-fix",false)).thenReturn(true);
        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);
        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Transferred","Transferred bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);
        assertTrue(paymentsResponse.getPaymentAllocation().get(0).getPaymentAllocationStatus().getName().equalsIgnoreCase("Transferred"));

        MvcResult duplicateRequest = restActions
            .post("/payment-groups/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Transferred","Transferred bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(duplicateRequest.getResponse().getContentAsString().contains("Bulk scan payment already exists for DCN = DCN293842342342834278348"));
    }

    @Test
    public void testUnidentifiedBulkScanPayments() throws Exception{
        when(featureToggler.getBooleanValue("prod-strategic-fix",false)).thenReturn(true);
        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);
        MvcResult result2 = restActions
            .post("/payment-groups/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Unidentified","Unidentified bulk scan payments", "Test Unidentified Reason", "DCN293842342342834278348"))
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), PaymentDto.class);
        assertTrue(paymentsResponse.getPaymentAllocation().get(0).getPaymentAllocationStatus().getName().equalsIgnoreCase("Unidentified"));
        assertTrue(paymentsResponse.getPaymentAllocation().get(0).getUnidentifiedReason().equalsIgnoreCase("Test Unidentified Reason"));
    }

    @Test
    public void testBulkScanPaymentHandlingClientErrorExceptions() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        when(this.restTemplatePaymentGroup.exchange(anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            eq(String.class), any(Map.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        when(featureToggler.getBooleanValue("prod-strategic-fix",false)).thenReturn(true);

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result2.getResponse().getContentAsString().contains("Bulk scan payment can't be marked as processed for DCN DCN293842342342834278348 Due to response status code as  = 404 NOT_FOUND"));

        MvcResult result3 = restActions
            .post("/payment-groups/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278349"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result3.getResponse().getContentAsString().contains("Bulk scan payment can't be marked as processed for DCN DCN293842342342834278349 Due to response status code as  = 404 NOT_FOUND"));
    }

    @Test
    public void testToggleOffFeatureStrategicFix() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        when(featureToggler.getBooleanValue("prod-strategic-fix",false)).thenReturn(false);

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result2.getResponse().getContentAsString().contains("This feature is not available to use !!!"));

        MvcResult result3 = restActions
            .post("/payment-groups/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278349"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result3.getResponse().getContentAsString().contains("This feature is not available to use !!!"));
    }

    @Test
    public void testBulkScanPaymentHandlingConnectionException() throws Exception{
        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        when(this.restTemplatePaymentGroup.exchange(anyString(),
            eq(HttpMethod.PATCH),
            any(HttpEntity.class),
            eq(String.class), any(Map.class)))
            .thenThrow(new RestClientException("Connection failed for bulk scan api"));
        when(featureToggler.getBooleanValue("prod-strategic-fix",false)).thenReturn(true);

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA001")
            .serviceDescription("DIVORCE")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference()  + "/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278348"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result2.getResponse().getContentAsString().contains("Error occurred while processing bulk scan payments with DCN DCN293842342342834278348"));

        MvcResult result3 = restActions
            .post("/payment-groups/bulk-scan-payments-strategic", getBulkScanPaymentStrategic("Allocated","Allocated bulk scan payments", null, "DCN293842342342834278349"))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result3.getResponse().getContentAsString().contains("Error occurred while processing bulk scan payments with DCN DCN293842342342834278349"));
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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
            .service("DIGITAL_BAR")
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

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        mockFees.add(fee1);
        mockFees.add(fee2);
        mockFees.add(fee3);
        PaymentFeeLink mockFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .fees(mockFees)
            .build();
        PaymentDbBackdoor mockDb = mock(PaymentDbBackdoor.class);
        when(mockDb.findByReference(paymentDto.getPaymentGroupReference())).thenReturn(mockFeeLink);

        List<PaymentFee> savedfees = mockDb.findByReference(paymentDto.getPaymentGroupReference()).getFees();

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
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
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
            .service("DIGITAL_BAR")
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

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().code("FEE0271").amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().code("FEE0272").amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().code("FEE0273").amountDue(BigDecimal.valueOf(10)).build();
        mockFees.add(fee1);
        mockFees.add(fee2);
        mockFees.add(fee3);
        PaymentFeeLink mockFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .fees(mockFees)
            .build();
        PaymentDbBackdoor mockDb = mock(PaymentDbBackdoor.class);
        when(mockDb.findByReference(paymentDto.getPaymentGroupReference())).thenReturn(mockFeeLink);

        List<PaymentFee> savedfees = mockDb.findByReference(paymentDto.getPaymentGroupReference()).getFees();


        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
        if(apportionFeature) {
            savedfees.stream()
                .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0271"))
                .forEach(fee -> {
                    assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                });
            savedfees.stream()
                .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0272"))
                .forEach(fee -> {
                    assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                });
            savedfees.stream()
                .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0273"))
                .forEach(fee -> {
                    assertEquals(BigDecimal.valueOf(10).intValue(), fee.getAmountDue().intValue());
                });
        }
    }

    @Test
    public void createBulkScanPaymentWithMultipleFee_SurplusPayment() throws Exception {

        String ccdCaseNumber = "1111CC12" + RandomUtils.nextInt();

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(10))
            .volume(1).version("1").calculatedAmount(new BigDecimal(10)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0272").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0273").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
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
            .service("DIGITAL_BAR")
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

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().code("FEE0271").amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().code("FEE0272").amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().code("FEE0273").amountDue(BigDecimal.valueOf(-10)).build();
        mockFees.add(fee1);
        mockFees.add(fee2);
        mockFees.add(fee3);
        PaymentFeeLink mockFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .fees(mockFees)
            .build();
        PaymentDbBackdoor mockDb = mock(PaymentDbBackdoor.class);
        when(mockDb.findByReference(paymentDto.getPaymentGroupReference())).thenReturn(mockFeeLink);

        List<PaymentFee> savedfees = mockDb.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
        if(apportionFeature) {
            savedfees.stream()
                .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0271"))
                .forEach(fee -> {
                    assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                });
            savedfees.stream()
                .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0272"))
                .forEach(fee -> {
                    assertEquals(BigDecimal.valueOf(0).intValue(), fee.getAmountDue().intValue());
                });
            savedfees.stream()
                .filter(fee -> fee.getCode().equalsIgnoreCase("FEE0273"))
                .forEach(fee -> {
                    assertEquals(BigDecimal.valueOf(-10).intValue(), fee.getAmountDue().intValue());
                });
        }
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


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA07")
            .serviceDescription("Divorce")
            .build();

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());

        Mockito.when(pciPalPaymentService.getPaymentProviderAutorisationTokens()).thenReturn(getTelephonyProviderAuthorisationResponse());

        Mockito.when(pciPalPaymentService.getTelephonyProviderLink(any(PciPalPaymentRequest.class)
            ,any(TelephonyProviderAuthorisationResponse.class),anyString(),anyString())).thenReturn(getTelephonyProviderAuthorisationResponse());

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("divorce")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isCreated())
            .andReturn();

        TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), TelephonyCardPaymentsResponse.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + telephonyCardPaymentsResponse.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
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


        BigDecimal amount = new BigDecimal("120.00");


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Specified Money Claims")
            .build();

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());

        Mockito.when(pciPalPaymentService.getPaymentProviderAutorisationTokens()).thenReturn(getTelephonyProviderAuthorisationResponse());

        Mockito.when(pciPalPaymentService.getTelephonyProviderLink(any(PciPalPaymentRequest.class)
            ,any(TelephonyProviderAuthorisationResponse.class),anyString(),anyString())).thenReturn(getTelephonyProviderAuthorisationResponse());

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);


        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("cmc_exception")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isCreated())
            .andReturn();

        TelephonyCardPaymentsResponse paymentDtoResult = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), TelephonyCardPaymentsResponse.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + paymentDtoResult.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
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


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA11")
            .serviceDescription("Probate")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());

        Mockito.when(pciPalPaymentService.getPaymentProviderAutorisationTokens()).thenReturn(getTelephonyProviderAuthorisationResponse());

        Mockito.when(pciPalPaymentService.getTelephonyProviderLink(any(PciPalPaymentRequest.class)
            ,any(TelephonyProviderAuthorisationResponse.class),anyString(),anyString())).thenReturn(getTelephonyProviderAuthorisationResponse());

        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("probate")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isCreated())
            .andReturn();

        TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), TelephonyCardPaymentsResponse.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + telephonyCardPaymentsResponse.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
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


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA10")
            .serviceDescription("Digital Bar")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenThrow(new NoServiceFoundException("No Service found for given CaseType"));


        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("DigitalBar")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isNotFound())
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


        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();

        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());

        Mockito.when(pciPalPaymentService.getPaymentProviderAutorisationTokens()).thenReturn(getTelephonyProviderAuthorisationResponse());

        Mockito.when(pciPalPaymentService.getTelephonyProviderLink(any(PciPalPaymentRequest.class)
            ,any(TelephonyProviderAuthorisationResponse.class),anyString(),anyString())).thenReturn(getTelephonyProviderAuthorisationResponse());

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);


        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("finrem")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isCreated())
            .andReturn();

        TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), TelephonyCardPaymentsResponse.class);

        MvcResult result4 = restActions
            .get("/card-payments/" + telephonyCardPaymentsResponse.getPaymentReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentsResponse = objectMapper.readValue(result4.getResponse().getContentAsString(), PaymentDto.class);

        assertNotNull(paymentsResponse);
        assertEquals("Initiated", paymentsResponse.getStatus());
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
        assertTrue(paymentsResponse.getReference().matches(PAYMENT_REFERENCE_REGEX));
        assertEquals(telephonyCardPaymentsRequest.getAmount(), paymentsResponse.getAmount());
        assertEquals("Amount saved in remissionDbBackdoor is equal to the on inside the request", amount, paymentsResponse.getAmount());
    }

    @Test
    public void addNewPaymentToExistingPaymentGroupForPCIPALAntennaThrowsExceptionWhenFlagIsOff() throws Exception {
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

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("finrem")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isBadRequest())
            .andReturn();

    }


    @Test
    public void throwExceptionWhenCurrencyIsEmpty() throws Exception {
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

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .caseType("finrem")
            .ccdCaseNumber("2154234356342357")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenAmountIsZero() throws Exception {
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


        BigDecimal amount = new BigDecimal("0");

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);


        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("finrem")
            .returnURL("http://localhost")
            .ccdCaseNumber("2154234356342357")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenCaseNumberIsEmpty() throws Exception {
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

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);


        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("finrem")
            .returnURL("http://localhost")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    }

    @Test
    public void throwExceptionWhenReturnURLIsEmpty() throws Exception {
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

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("finrem")
            .ccdCaseNumber("2154234356342357")
            .build();

        MvcResult result3 = restActions
            .withReturnUrl("https://www.google.com")
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    }

    @Test
    public void createCardPaymentPaymentWithMultipleFee_SurplusPayment_ForPCIPALAntenna() throws Exception {

        String ccdCaseNumber = "1111111122222222";
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

        OrganisationalServiceDto organisationalServiceDto = OrganisationalServiceDto.orgServiceDtoWith()
            .serviceCode("AA08")
            .serviceDescription("Financial Remedy")
            .build();
        when(pciPalPaymentService.create(any(PaymentServiceRequest.class)))
            .thenReturn(PciPalPayment.pciPalPaymentWith().paymentId("1").state(State.stateWith().status("created").build()).build());

        Mockito.when(pciPalPaymentService.getPaymentProviderAutorisationTokens()).thenReturn(getTelephonyProviderAuthorisationResponse());

        Mockito.when(pciPalPaymentService.getTelephonyProviderLink(any(PciPalPaymentRequest.class)
            ,any(TelephonyProviderAuthorisationResponse.class),anyString(),anyString())).thenReturn(getTelephonyProviderAuthorisationResponse());

        when(referenceDataService.getOrganisationalDetail(any(),any())).thenReturn(organisationalServiceDto);

        TelephonyCardPaymentsRequest telephonyCardPaymentsRequest = TelephonyCardPaymentsRequest.telephonyCardPaymentsRequestWith()
            .amount(amount)
            .currency(CurrencyCode.GBP)
            .caseType("finrem")
            .ccdCaseNumber(ccdCaseNumber)
            .returnURL("http://localhost")
            .build();

        MvcResult result2 = restActions
            .post("/payment-groups/" + paymentGroupDto.getPaymentGroupReference() + "/telephony-card-payments", telephonyCardPaymentsRequest)
            .andExpect(status().isCreated())
            .andReturn();

        TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = objectMapper.readValue(result2.getResponse().getContentAsString(), TelephonyCardPaymentsResponse.class);

        List<PaymentFee> savedfees = db.findByReference(telephonyCardPaymentsResponse.getPaymentGroupReference()).getFees();

        assertEquals(new BigDecimal(10), savedfees.get(0).getAmountDue());
        assertEquals(new BigDecimal(40), savedfees.get(1).getAmountDue());
        assertEquals(new BigDecimal(60), savedfees.get(2).getAmountDue());
    }

    private CardPaymentRequest getCardPaymentRequest() {
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal("250.00"))
            .description("description")
            .ccdCaseNumber("1111-2222-2222-1111")
            .service("DIVORCE")
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
            .caseType("tax_exception")
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
            .service("DIGITAL_BAR")
            .siteId("AA07")
            .build();
    }

    private PaymentAllocationDto getPaymentAllocationDto(String paymentAllocationStatus, String paymentAllocationDescription, String unidentifiedReason) {
        return PaymentAllocationDto.paymentAllocationDtoWith()
            .dateCreated(new Date())
            .explanation("test explanation")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith()
                .description(paymentAllocationDescription).name(paymentAllocationStatus).build())
            .reason("testReason")
            .receivingOffice("testReceivingOffice")
            .unidentifiedReason(unidentifiedReason)
            .userId("124")
            .userName("testname")
            .build();
    }

    private BulkScanPaymentRequestStrategic getBulkScanPaymentStrategic(String paymentAllocationStatus, String paymentAllocationDescription, String unIdentifiedReason, String documentControlNumber) {
        return BulkScanPaymentRequestStrategic.createBulkScanPaymentStrategicWith()
            .amount(new BigDecimal(100.00))
            .currency(CurrencyCode.GBP)
            .documentControlNumber(documentControlNumber)
            .ccdCaseNumber("1231-1231-3453-4333")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .payerName("CCD User")
            .bankedDate(DateTime.now().toString())
            .giroSlipNo("BCH82173823")
            .paymentStatus(PaymentStatus.SUCCESS)
            .paymentMethod(PaymentMethodType.CHEQUE)
            .paymentAllocationDTO(getPaymentAllocationDto(paymentAllocationStatus,paymentAllocationDescription, unIdentifiedReason))
            .caseType("MoneyClaimCase")
            .build();
    }

    private TelephonyProviderAuthorisationResponse getTelephonyProviderAuthorisationResponse(){

        return  new TelephonyProviderAuthorisationResponse(
            "accessToken",
            "bearer",
            "299",
            "refreshTokeb",
            "HMCTSStage",
            "HMCTS",
            "2021-06-23T12:57:10Z",
            "2021-06-23T13:02:10Z",
            "https://nextUrl.com"
        );


    }
    private PaymentFeeLink getPaymentFeeLink(){
        return PaymentFeeLink.paymentFeeLinkWith()
            .payments(Arrays.asList(
                Payment.paymentWith()
                    .reference("reference")
                    .build()
            ))
            .build();
    }
}
