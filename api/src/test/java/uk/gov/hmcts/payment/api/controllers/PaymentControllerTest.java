package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.validator.Arg;
import org.ff4j.services.domain.FeatureApiBean;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.UpdatePaymentRequest;
import uk.gov.hmcts.payment.api.contract.exception.ValidationErrorDTO;
import uk.gov.hmcts.payment.api.dto.SupplementaryDetailsResponse;
import uk.gov.hmcts.payment.api.dto.SupplementaryPaymentDto;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentAllocation;
import uk.gov.hmcts.payment.api.model.PaymentAllocationStatus;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.servicebus.CallbackServiceImpl;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.any;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest", "mockcallbackservice"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class PaymentControllerTest extends PaymentsDataUtil {

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMAT_DD_MM_YYYY = DateTimeFormat.forPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT_T_HH_MM_SS = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;
    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;
    @MockBean
    protected CallbackServiceImpl callbackServiceImplMock;
    @Autowired
    protected PaymentDbBackdoor db;

    RestActions restActions;
    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @MockBean
    @Qualifier("restTemplateIacSupplementaryInfo")
    private RestTemplate restTemplateIacSupplementaryInfo;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

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
    }

    @Test
    public void updateCaseReference_forGivenPaymentReferenceTest() throws Exception {
        //Create a payment in remissionDbBackdoor
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("caseReference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Description1")
            .serviceType("Probate")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .reference("RC-1519-9028-1909-3890")
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186168000").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);
        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result1 = restActions.
            get(format("/credit-account-payments/RC-1519-9028-1909-3890"))
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertEquals(paymentDto.getCaseReference(), "caseReference");
        assertEquals(paymentDto.getCcdCaseNumber(), "ccdCaseNumber");

        /* -- update payment -- */
        UpdatePaymentRequest updatePaymentRequest = objectMapper.readValue(updatePaymentRequestJson().getBytes(), UpdatePaymentRequest.class);
        MvcResult result2 = restActions.
            patch(format("/payments/" + paymentDto.getReference()), updatePaymentRequest)
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    @Transactional
    public void updateCaseReference_forGivenPaymentReferenceWithoutCaseNumber() throws Exception {
        //Create a payment in remissionDbBackdoor
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("caseReference")
            //.ccdCaseNumber("ccdCaseNumber")
            .description("Description1")
            .serviceType("Probate")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .reference("RC-1519-9028-1909-3890")
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186168000").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);
        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result1 = restActions.
            get(format("/credit-account-payments/RC-1519-9028-1909-3890"))
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertEquals(paymentDto.getCaseReference(), "caseReference");
        //assertEquals(paymentDto.getCcdCaseNumber(), "ccdCaseNumber");

        /* -- update payment -- */
        UpdatePaymentRequest updatePaymentRequest = objectMapper.readValue(updatePaymentRequestJson().getBytes(), UpdatePaymentRequest.class);
        updatePaymentRequest.setCcdCaseNumber(null);
        MvcResult result2 = restActions.
            patch(format("/payments/" + paymentDto.getReference()), updatePaymentRequest)
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    @Transactional
    public void updateCaseReference_forGivenPaymentReferenceWithoutCaseReference() throws Exception {
        //Create a payment in remissionDbBackdoor
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            //.caseReference("caseReference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Description1")
            .serviceType("Probate")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .reference("RC-1519-9028-1909-3890")
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186168000").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);
        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result1 = restActions.
            get(format("/credit-account-payments/RC-1519-9028-1909-3890"))
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentDto.class);
        //assertEquals(paymentDto.getCaseReference(), "caseReference");
        assertEquals(paymentDto.getCcdCaseNumber(), "ccdCaseNumber");

        /* -- update payment -- */
        UpdatePaymentRequest updatePaymentRequest = objectMapper.readValue(updatePaymentRequestJson().getBytes(), UpdatePaymentRequest.class);
        updatePaymentRequest.setCaseReference(null);
        MvcResult result2 = restActions.
            patch(format("/payments/" + paymentDto.getReference()), updatePaymentRequest)
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    public void getPaymentsBasedOnPaymentReference() throws Exception {
        //Create a payment in remissionDbBackdoor

        PaymentAllocation paymentAllocation = PaymentAllocation.paymentAllocationWith()
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Allocated").build())
            .paymentGroupReference("2018-15186168000")
            .paymentReference("RC-1519-9028-1909-3890")
            .build();
        List<PaymentAllocation> paymentAllocationsList = new ArrayList<>();
        paymentAllocationsList.add(paymentAllocation);
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("caseReference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Description1")
            .serviceType("Probate")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .paymentAllocation(paymentAllocationsList)
            .reference("RC-1519-9028-1909-3890")
            .bankedDate(new Date())
            .documentControlNumber("12345")
            .payerName("test")
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186168000").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);
        Payment savedPayment = paymentFeeLink.getPayments().get(0);


        MvcResult result = restActions
            .get("/payments/RC-1519-9028-1909-3890")
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void upateCaseReferenceValidation_forEmptyValues() throws Exception {
        UpdatePaymentRequest updatePaymentRequest = objectMapper.readValue(updatePaymentInvalidRequestJson().getBytes(), UpdatePaymentRequest.class);
        MvcResult result = restActions.
            patch(format("/payments/RC-1519-9028-1909-3111"), updatePaymentRequest)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }


    private String updatePaymentRequestJson() {
        return "{\n" +
            "  \"case_reference\": \"newCaseReference\",\n" +
            "  \"ccd_case_number\": \"newCcdCaseNumber\"\n" +
            "}";
    }


    private String updatePaymentInvalidRequestJson() {
        return "{\n" +
            "  \"case_reference\": \"\"\n" +
            "}";
    }

    @Test
    @Transactional
    public void searchAllPayments_withValidBetweenDates_shouldReturn200() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());


        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().size()).isEqualTo(2);
    }

    @Test
    @Transactional
    public void searchAllPayments_withValidBetweenDates_shouldReturn200ForBulkScanDisable() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/disable")
            .andExpect(status().isAccepted());

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().size()).isEqualTo(2);
    }


    @Test
    @Transactional
    public void searchAllPayments_withCcdCaseNumber_shouldReturnRequiredFieldsForVisualComponent() throws Exception {
        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("1");

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?ccd_case_number=ccdCaseNumber1" + "&start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse payments = objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<PaymentsResponse>() {
        });

        assertThat(payments.getPayments().size()).isEqualTo(2);

        PaymentDto payment = payments.getPayments().get(0);

        assertThat(payment.getCcdCaseNumber()).isEqualTo("ccdCaseNumber1");

        assertThat(payment.getReference()).isNotBlank();
        assertThat(payment.getAmount()).isPositive();
        assertThat(payment.getDateCreated()).isNotNull();
        assertThat(payment.getCustomerReference()).isNotBlank();
    }

    @Test
    @Transactional
    public void searchCardPayments_withValidBetweenDates_shouldReturnOnlyCardPayments() throws Exception {

        populateCardPaymentToDb("2");
        populateCreditAccountPaymentToDb("1");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&payment_method=CARD")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        List<PaymentDto> payments = paymentsResponse.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo("RC-1519-9028-2432-0002");
            assertThat(p.getCcdCaseNumber()).isEqualTo("ccdCaseNumber2");
            assertThat(p.getCaseReference()).isEqualTo("Reference2");
            assertThat(p.getAmount()).isEqualTo(new BigDecimal("99.99"));
            assertThat(p.getChannel()).isEqualTo("online");
            assertThat(p.getMethod()).isEqualTo("card");
            assertThat(p.getStatus()).isEqualTo("initiated");
            assertThat(p.getSiteId()).isEqualTo("AA02");
            assertThat(p.getDateCreated()).isNotNull();
            assertThat(p.getDateUpdated()).isNotNull();
            p.getFees().stream().forEach(f -> {
                assertThat(f.getCode()).isEqualTo("FEE0002");
                assertThat(f.getVersion()).isEqualTo("1");
                assertThat(f.getCalculatedAmount()).isEqualTo(new BigDecimal("99.99"));
            });
        });
    }

    @Test
    @Transactional
    public void searchCreditPayments_withValidBetweenDates_shouldReturnOnlyPbaPayments() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&payment_method=PBA")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        assertPbaPaymentsForLibereta(paymentsResponse.getPayments());

    }

    @Test
    @Transactional
    public void searchCreditPayments_withValidBetweenDates_shouldReturnAllPayments() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&payment_method=ALL")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        assertThat(paymentsResponse.getPayments().size()).isEqualTo(2);

    }

    @Test
    @Transactional
    public void searchCreditPayments_withValidBetweenDates_shouldReturnPaymentsBasedOnServiceType() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");
        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&service_name=DIVORCE")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        assertThat(paymentsResponse.getPayments().size()).isEqualTo(0);

    }

    @Test
    @Transactional
    public void searchCreditPayments_withValidStartDate_shouldReturnAllPayments() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&payment_method=ALL")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        assertThat(paymentsResponse.getPayments().size()).isEqualTo(2);

    }

    @Test
    @Transactional
    public void searchCreditPayments_withValidEndDate_shouldReturnAllPayments() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?end_date=" + endDate + "&payment_method=ALL")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        assertThat(paymentsResponse.getPayments().size()).isEqualTo(0);
    }

    @Test
    @Transactional
    public void searchCreditPayments_withPbaNumber() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?pba_number=123456" + "&start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertPbaPaymentsForLibereta(paymentsResponse.getPayments());

    }


    @Test
    @Transactional
    public void searchCreditPayments_byCcdCaseNumber() throws Exception {

        populateCardPaymentToDb("1");

        Payment creditPayment = populateCreditAccountPaymentToDb("2");

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult result = restActions
            .get("/payments?ccd_case_number=" + creditPayment.getCcdCaseNumber() + "&start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertPbaPaymentsForLibereta(paymentsResponse.getPayments());

    }


    @Test
    @Transactional
    public void searchAllPayments_withInvalidMethodType_shouldReturn400() throws Exception {
        populateCardPaymentToDb("1");

        String startDate = LocalDate.now().toString(DATE_FORMAT);
        String endDate = startDate;

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&payment_method=UNKNOWN")
            .andExpect(status().isBadRequest())
            .andReturn();

        ValidationErrorDTO errorDTO = objectMapper.readValue(result.getResponse().getContentAsString(), ValidationErrorDTO.class);
        assertThat(errorDTO.hasErrors()).isTrue();
        assertThat(errorDTO.getFieldErrors().size()).isEqualTo(1);
        assertThat(errorDTO.getFieldErrors().get(0).getMessage()).isEqualTo("Invalid payment method requested");
    }

    @Test
    @Transactional
    public void searchAllPayment_withFutureDate_shouldReturn400() throws Exception {
        populateCardPaymentToDb("1");

        String startDate = LocalDate.now().toString(DATE_FORMAT);
        String endDate = LocalDate.now().plusDays(1).toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&payment_method=CARD")
            .andExpect(status().isBadRequest())
            .andReturn();

        ValidationErrorDTO errorDTO = objectMapper.readValue(result.getResponse().getContentAsString(), ValidationErrorDTO.class);
        assertThat(errorDTO.hasErrors()).isTrue();
        assertThat(errorDTO.getFieldErrors().size()).isEqualTo(1);
        assertThat(errorDTO.getFieldErrors().get(0).getField()).isEqualTo("end_date");
        assertThat(errorDTO.getFieldErrors().get(0).getMessage()).isEqualTo("Date cannot be in the future");
    }

    @Test
    @Transactional
    public void searchAllPayments_withInvalidFormatDates_shouldReturn400() throws Exception {
        populateCardPaymentToDb("1");

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=12/05/2018&end_date=14-05-2018&payment_method=CARD")
            .andExpect(status().isBadRequest())
            .andReturn();

        ValidationErrorDTO errorDTO = objectMapper.readValue(result.getResponse().getContentAsString(), ValidationErrorDTO.class);
        assertThat(errorDTO.hasErrors()).isTrue();
        assertThat(errorDTO.getFieldErrors().size()).isEqualTo(1);
        assertThat(errorDTO.getFieldErrors().get(0).getField()).isEqualTo("start_date");
        assertThat(errorDTO.getFieldErrors().get(0).getMessage()).contains("Invalid date format received, required data format is ISO");
    }

    @Test
    public void testFindFeatureFlag_withCorrectUID() throws Exception {
        MvcResult result = restActions
            .get("/api/ff4j/store/features/payment-search")
            .andExpect(status().isOk())
            .andReturn();

        FeatureApiBean feature = objectMapper.readValue(result.getResponse().getContentAsByteArray(), FeatureApiBean.class);
        assertThat(feature.getUid()).isEqualTo("payment-search");
        assertThat(feature.isEnable()).isEqualTo(true);
        assertThat(feature.getDescription()).isEqualTo("Payments search API");
    }

    @Test
    public void testFindFeatureFlag_withIncorrectUID_shouldReturn404() throws Exception {
        restActions
            .get("/api/ff4j/store/features/my-feature")
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void testEnableFeatureFlag_withCorrectUID() throws Exception {
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/api/ff4j/store/features/payment-search")
            .andExpect(status().isOk())
            .andReturn();

        FeatureApiBean feature = objectMapper.readValue(result.getResponse().getContentAsByteArray(), FeatureApiBean.class);
        assertThat(feature.getUid()).isEqualTo("payment-search");
        assertThat(feature.isEnable()).isEqualTo(true);
        assertThat(feature.getDescription()).isEqualTo("Payments search API");
    }

    @Test
    public void testRetrievePayments_withFeatureFlagDisabled_shouldReturnValidMessage() throws Exception {
        restActions
            .post("/api/ff4j/store/features/payment-search/disable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?payment_method=ALL")
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("Payment search feature is not available for usage.");
    }

    @Test
    @Transactional
    public void searchCardPayments_withValidStartDateAndNoEndDate_shouldReturnOk() throws Exception {

        populateCardPaymentToDb("2");
        populateCreditAccountPaymentToDb("1");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate + "&payment_method=CARD")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        List<PaymentDto> payments = paymentsResponse.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo("RC-1519-9028-2432-0002");
            assertThat(p.getCcdCaseNumber()).isEqualTo("ccdCaseNumber2");
            assertThat(p.getCaseReference()).isEqualTo("Reference2");
            assertThat(p.getAmount()).isEqualTo(new BigDecimal("99.99"));
            assertThat(p.getChannel()).isEqualTo("online");
            assertThat(p.getMethod()).isEqualTo("card");
            assertThat(p.getStatus()).isEqualTo("initiated");
            assertThat(p.getSiteId()).isEqualTo("AA02");
            assertThat(p.getDateCreated()).isNotNull();
            assertThat(p.getDateUpdated()).isNotNull();
            p.getFees().stream().forEach(f -> {
                assertThat(f.getCode()).isEqualTo("FEE0002");
                assertThat(f.getVersion()).isEqualTo("1");
                assertThat(f.getCalculatedAmount()).isEqualTo(new BigDecimal("99.99"));
            });
        });
    }

    @Test
    @Transactional
    public void searchCardPayments_withValidEndDateAndNoStartDate_shouldReturnOk() throws Exception {

        populateCardPaymentToDb("2", "RC-1519-9028-2432-0002");
        populateCreditAccountPaymentToDb("1");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_TIME_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?end_date=" + endDate + "&start_date=" + startDate + "&payment_method=CARD")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        List<PaymentDto> payments = paymentsResponse.getPayments();
        assertThat(payments.size()).isEqualTo(1);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo("RC-1519-9028-2432-0002");
            assertThat(p.getCcdCaseNumber()).isEqualTo("ccdCaseNumber2");
            assertThat(p.getCaseReference()).isEqualTo("Reference2");
            assertThat(p.getAmount()).isEqualTo(new BigDecimal("99.99"));
            assertThat(p.getChannel()).isEqualTo("online");
            assertThat(p.getMethod()).isEqualTo("card");
            assertThat(p.getStatus()).isEqualTo("initiated");
            assertThat(p.getSiteId()).isEqualTo("AA02");
            assertThat(p.getDateCreated()).isNotNull();
            assertThat(p.getDateUpdated()).isNotNull();
            p.getFees().stream().forEach(f -> {
                assertThat(f.getCode()).isEqualTo("FEE0002");
                assertThat(f.getVersion()).isEqualTo("1");
                assertThat(f.getCalculatedAmount()).isEqualTo(new BigDecimal("99.99"));
            });
        });
    }


    @Test
    @Transactional
    public void searchAllPaymentsWithInvalidDateFormatshouldFail() throws Exception {
        populateCardPaymentToDb("1");

        String startDate = LocalDate.now().toString("dd/MM/yyyy");
        String endDate = startDate;

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isBadRequest())
            .andReturn();

        ValidationErrorDTO errorDTO = objectMapper.readValue(result.getResponse().getContentAsString(), ValidationErrorDTO.class);
        assertThat(errorDTO.hasErrors()).isTrue();
        assertThat(errorDTO.getFieldErrors().get(0).getMessage()).isEqualTo("Invalid date format received, required data format is ISO");
    }

    @Test
    @Transactional
    public void searchAllPaymentsWithValidDateFormatShouldPass() throws Exception {
        populateBarCashPaymentToDb("1");
        populateBarCardPaymentToDb("2");

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(2);
    }

    @Test
    @Transactional
    public void searchAllPaymentsWithValidDateTimeFormatShouldPass() throws Exception {
        populateBarCashPaymentToDb("3");
        populateBarChequePaymentToDb("4");

        String startDate = LocalDate.now().toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(2);
    }

    @Test
    @Transactional
    public void searchAllPaymentsWithDateDDMMYYYYFormatShouldPass() throws Exception {
        populateBarCashPaymentToDb("5");
        populateBarChequePaymentToDb("6");

        String startDate = LocalDateTime.now().toString(DATE_FORMAT_DD_MM_YYYY);
        String endDate = LocalDateTime.now().minusSeconds(1).toString(DATE_FORMAT_DD_MM_YYYY);


        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(2);

    }

    @Test
    public void searchAllPaymentsWithDateFormatYYYYDDMMTHHMMSSShouldPass() throws Exception {
        String startDate = LocalDateTime.now().toString(DATE_TIME_FORMAT_T_HH_MM_SS);
        String endDate = LocalDateTime.now().toString(DATE_TIME_FORMAT_T_HH_MM_SS);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        assertNotNull(response);
    }

    @Test
    public void updatePaymentStatusForInvalidPaymentReferenceShouldFail() throws Exception {
        restActions
            .patch("/payments/RC-1519-9028-1909-1400/status/success")
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Transactional
    public void updatePaymentStatusForPaymentReferenceShouldPass() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1433";
        populateTelephonyPaymentToDb(paymentReference, false);

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo(paymentReference);
            assertThat(p.getStatus()).isEqualTo("initiated");
            assertThat(p.getExternalProvider()).isEqualTo("pci pal");
            assertThat(p.getChannel()).isEqualTo("telephony");
        });

        // Update payment status with valid payment reference
        restActions
            .patch("/payments/" + paymentReference + "/status/success")
            .andExpect(status().isNoContent());


        MvcResult result2 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        response = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        payments = response.getPayments();
        assertNotNull(payments);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo(paymentReference);
            assertThat(p.getStatus()).isEqualTo("success");
        });
    }

    @Test
    @Transactional
    public void updatePaymentStatusForPaymentReferenceWhenBulkScanDisabled() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1433";
        populateTelephonyPaymentToDb(paymentReference, false);

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/disable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo(paymentReference);
            assertThat(p.getStatus()).isEqualTo("Initiated");
            assertThat(p.getExternalProvider()).isEqualTo("pci pal");
            assertThat(p.getChannel()).isEqualTo("telephony");
        });

        // Update payment status with valid payment reference
        restActions
            .patch("/payments/" + paymentReference + "/status/success")
            .andExpect(status().isNoContent());


        MvcResult result2 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        response = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        payments = response.getPayments();
        assertNotNull(payments);
        payments.stream().forEach(p -> {
            assertThat(p.getPaymentReference()).isEqualTo(paymentReference);
            assertThat(p.getStatus()).isEqualTo("Success");
        });
    }

    @Test
    @Transactional
    public void updateIncorrectPaymentStatusForPaymentReferenceShouldFail() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1434";
        populateTelephonyPaymentToDb(paymentReference, false);

        // update payment status with invalid status type
        MvcResult errResult = restActions
            .patch("/payments/" + paymentReference + "/status/something")
            .andExpect(status().is4xxClientError())
            .andReturn();
        assertThat(errResult.getResponse().getContentAsString()).contains("PaymentStatus with something is not found");
    }

    // if callback URL exists make sure to call callbackservice
    @Test
    @Transactional
    public void updatePaymentStatusForPaymentReferenceShouldUseCallbackServiceToUpdateInterestedService() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populateTelephonyPaymentToDb(paymentReference, true);

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);

        // Update payment status with valid payment reference
        restActions
            .patch("/payments/" + paymentReference + "/status/success")
            .andExpect(status().isNoContent());

        //get the payment
        PaymentFeeLink updatedPaymentFeeLink = db.findByReference(paymentReference);

        verify(callbackServiceImplMock, times(1)).callback(updatedPaymentFeeLink, updatedPaymentFeeLink.getPayments().get(0));
    }

    @Test
    @Transactional
    public void shouldCheckExelaPaymentsWhenBulkScanIsToggledOn() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populatePaymentToDbForExelaPayments(paymentReference);

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckExelaPaymentsWhenBulkScanIsToggledOnAndPaymentProviderIsNull() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populatePaymentToDbForExelaPaymentsWithoutPaymentProvider(paymentReference);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckApportionNewFieldsNotPopulatedWhenApportionFeatureIsToggledOffForBulkScanPayments() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populatePaymentToDbForExelaPayments(paymentReference);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(false);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckApportionNewFieldsPopulatedWhenApportionFeatureIsToggledONForBulkScanPayments() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populatePaymentToDbForExelaPayments(paymentReference);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckApportionNewFieldsNotPopulatedWhenApportionFeatureIsToggledOffForCardPayments() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populateTelephonyPaymentToDb(paymentReference, false);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(false);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckApportionNewFieldsPopulatedWhenApportionFeatureIsToggledONForCardPayments() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populateTelephonyPaymentToDb(paymentReference, false);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenApportionFlagToggledONForCardPayments() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);
        populateApportionDetails(payment);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenCallSurplusAmountIsNotNull() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);
        populateApportionDetailsWithCallSurplusAmount(payment);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenFeeIdIsDifferent() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);
        populateApportionDetailsWithDifferentFeeId(payment);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }


    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenApportionFlagToggledOFFForCardPayments() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);
        PaymentFeeLink paymentFeeLink = payment.getPaymentLink();
        List<PaymentFeeLink> paymentFeeLinkList = new ArrayList<>();
        paymentFeeLinkList.add(paymentFeeLink);
        List<FeePayApportion> feePayApportionList = new ArrayList<>();
        FeePayApportion feePayApportion = FeePayApportion.feePayApportionWith()
            .id(1)
            .apportionAmount(BigDecimal.valueOf(100))
            .apportionAmount(BigDecimal.valueOf(100))
            .apportionType("AUTO")
            .feeId(1)
            .feeAmount(BigDecimal.valueOf(100))
            .build();
        feePayApportionList.add(feePayApportion);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(false);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenWhenDateCreatedIsBeforeApportionDate() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);

        List<FeePayApportion> feePayApportionList = new ArrayList<>();
        FeePayApportion feePayApportion = FeePayApportion.feePayApportionWith()
            .id(1)
            .apportionAmount(BigDecimal.valueOf(100))
            .apportionAmount(BigDecimal.valueOf(100))
            .apportionType("AUTO")
            .feeId(1)
            .feeAmount(BigDecimal.valueOf(100))
            .build();
        feePayApportionList.add(feePayApportion);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);
        payment.setDateCreated(parseDate("01.05.2020"));
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenWhenDateCreatedIsEqualToApportionDate() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);
        populateApportionDetailsWithCallSurplusAmount(payment);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);
        payment.setDateCreated(parseDate("01.06.2020"));
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckAmountDueIsCalculatedFromApportionTableWhenWhenDateCreatedIsAfterApportionDate() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);
        populateApportionDetailsWithCallSurplusAmount(payment);
        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);
        payment.setDateCreated(parseDate("05.06.2020"));
        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());
        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void shouldCheckExelaPaymentsWhenBulkScanIsToggledOff() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populatePaymentToDbForExelaPayments(paymentReference);

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/disable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(0);
    }

    // if callback URL does not exist make sure not to call callback service
    @Test
    @Transactional
    public void updatePaymentStatusForPaymentReferenceWithoutCallbackURLShouldNotUseCallbackService() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1436";
        Payment payment = populateTelephonyPaymentToDb(paymentReference, false);

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);

        // Update payment status with valid payment reference
        restActions
            .patch("/payments/" + paymentReference + "/status/success")
            .andExpect(status().isNoContent());

        verify(callbackServiceImplMock, times(0)).callback(payment.getPaymentLink(), payment);
    }

    @Test
    @Transactional
    public void retrievePaymentByReference() throws Exception {
        Payment payment = populateCardPaymentToDb("1");

        MvcResult result = restActions
            .get("/payments/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertThat(paymentDto.getPaymentReference()).isEqualTo(payment.getReference());
        assertThat(paymentDto.getMethod()).isEqualTo(payment.getPaymentMethod().getName());
        assertThat(paymentDto.getAmount()).isEqualTo(payment.getAmount());
        assertThat(paymentDto.getCcdCaseNumber()).isEqualTo(payment.getCcdCaseNumber());

    }

    @Test
    @Transactional
    public void retrievePaymentByReferenceWithApportionmentDetails() throws Exception {
        Payment payment = populateCardPaymentToDbWithApportionmentDetails("1");

        MvcResult result = restActions
            .get("/payments/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentDto.class);
        List<FeeDto> feeDtoList = paymentDto.getFees();
        FeeDto feeDto = feeDtoList.get(0);
        assertThat(paymentDto.getFees().size()).isEqualTo(1);
        assertNotNull(paymentDto);
        assertThat(paymentDto.getPaymentReference()).isEqualTo(payment.getReference());
        assertThat(paymentDto.getMethod()).isEqualTo(payment.getPaymentMethod().getName());
        assertThat(paymentDto.getAmount()).isEqualTo(payment.getAmount());
        assertThat(paymentDto.getCcdCaseNumber()).isEqualTo(payment.getCcdCaseNumber());
        assertThat(feeDto.getAllocatedAmount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(feeDto.getApportionAmount()).isEqualTo(new BigDecimal("99.99"));

    }

    @Test
    @Transactional
    public void retrievePaymentByReference_shouldThrow404_whenReferenceIsUnknown() throws Exception {
        populateCardPaymentToDb("1");

        restActions
            .get("/payments/" + "some_random")
            .andExpect(status().isNotFound())
            .andReturn();

    }

    @Test
    @Transactional
    public void duplicateBSPPaymentsShouldNotAppearLiberata() throws Exception {
        String paymentReference = "RC-1519-9028-1909-1435";
        populatePaymentToDbForBulkScanPayment(paymentReference, "2018-00000000001");

        String startDate = LocalDateTime.now().toString(DATE_FORMAT);
        String endDate = LocalDateTime.now().toString(DATE_TIME_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        restActions
            .post("/api/ff4j/store/features/bulk-scan-check/enable")
            .andExpect(status().isAccepted());

        MvcResult result1 = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse response = objectMapper.readValue(result1.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        List<PaymentDto> payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);

        MvcResult result2 = restActions
            .get("/payments?start_date=" + startDate)
            .andExpect(status().isOk())
            .andReturn();

        response = objectMapper.readValue(result2.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);

        endDate = LocalDateTime.now().toString(DATE_TIME_FORMAT);

        MvcResult result3 = restActions
            .get("/payments?end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        response = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentsResponse.class);
        payments = response.getPayments();
        assertNotNull(payments);
        assertThat(payments.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void searchPaymentsByApportion_withValidDates_shouldReturnPayments() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);
        assertThat(paymentsResponse.getPayments().size()).isEqualTo(2);

    }


    @Test
    @Transactional
    public void iacSupplementaryDetails_withValidDates_shouldReturnPayments_with_supplementaryDetails_checkException() throws Exception {

        populateIACCardPaymentToDb("6");
        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("iac-supplementary-details-feature",false)).thenReturn(true);
        when(this.restTemplateIacSupplementaryInfo.exchange(anyString(),ArgumentMatchers.eq(HttpMethod.POST),Mockito.any(HttpEntity.class),ArgumentMatchers.eq(SupplementaryDetailsResponse.class))).thenThrow(HttpClientErrorException.class);

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isPartialContent())
            .andReturn();

        when(this.restTemplateIacSupplementaryInfo.exchange(anyString(), ArgumentMatchers.eq(HttpMethod.POST),Mockito.any(HttpEntity.class),ArgumentMatchers.eq(SupplementaryDetailsResponse.class))).thenThrow(NullPointerException.class);
        MvcResult result2 = restActions
            .get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isPartialContent())
            .andReturn();

    }

    @Test
    @Transactional
    public void iacSupplementaryDetails_withValidDates_shouldReturnPayments_with_supplementaryDetails_200() throws Exception {

        populateIACCardPaymentToDb("3");
        populateCreditAccountPaymentToDbForIAC("3");
        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("iac-supplementary-details-feature",false)).thenReturn(true);
        SupplementaryDetailsResponse supplementaryDetailsResponse = populateIACSupplementaryDetails("3");
        when(this.restTemplateIacSupplementaryInfo.exchange(anyString(),ArgumentMatchers.eq(HttpMethod.POST),Mockito.any(HttpEntity.class), ArgumentMatchers.eq(SupplementaryDetailsResponse.class)))
            .thenReturn(new ResponseEntity(supplementaryDetailsResponse,HttpStatus.OK));

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());

        MvcResult result = restActions
            .get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        SupplementaryPaymentDto supplementaryPaymentDto = objectMapper.readValue(result.getResponse().getContentAsString(), SupplementaryPaymentDto.class);
        assertThat(supplementaryPaymentDto.getPayments().size()).isEqualTo(2);
        assertNotNull(supplementaryPaymentDto.getSupplementaryInfo());

    }

    @Test
    @Transactional
    public void iacSupplementaryDetails_withValidDates_shouldReturnPayments_with_supplementaryDetailsAndMissingInfo_206() throws Exception {

        populateIACCardPaymentToDb("1");
        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);
        when(featureToggler.getBooleanValue("iac-supplementary-details-feature",false)).thenReturn(true);
        SupplementaryDetailsResponse supplementaryDetailsResponse = populateIACSupplementaryDetailsWithMissingCCDNumbers("1");
        when(this.restTemplateIacSupplementaryInfo.exchange(anyString(),ArgumentMatchers.eq(HttpMethod.POST),Mockito.any(HttpEntity.class),ArgumentMatchers.eq(SupplementaryDetailsResponse.class)))
            .thenReturn(new ResponseEntity(supplementaryDetailsResponse,HttpStatus.PARTIAL_CONTENT));

        restActions
            .post("/api/ff4j/store/features/payment-search/enable")
            .andExpect(status().isAccepted());
        //As IAC supplementary response is 206 so Payment response should be 206
        MvcResult result = restActions
            .get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isPartialContent())
            .andReturn();

        SupplementaryPaymentDto supplementaryPaymentDto = objectMapper.readValue(result.getResponse().getContentAsString(), SupplementaryPaymentDto.class);
        assertThat(supplementaryPaymentDto.getPayments().size()).isEqualTo(1);
        assertNotNull(supplementaryPaymentDto.getSupplementaryInfo());
    }

//    @Test
//    @Transactional
//    public void iacSupplementaryDetails_withValidDates_shouldReturnPayments_with_supplementaryDetailsAndMissingInfoNull() throws Exception {
//
//        populateIACCardPaymentToDb("1");
//        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
//        String endDate = LocalDate.now().toString(DATE_FORMAT);
//        when(featureToggler.getBooleanValue("iac-supplementary-details-feature",false)).thenReturn(true);
//        SupplementaryDetailsResponse supplementaryDetailsResponse = populateIACSupplementaryDetailsWithMissingCCDNumbers("1");
//        supplementaryDetailsResponse.setMissingSupplementaryInfo(null);
//
//        when(this.restTemplateIacSupplementaryInfo.exchange(anyString(), ArgumentMatchers.eq(HttpMethod.POST),Mockito.any(HttpEntity.class),ArgumentMatchers.eq(SupplementaryDetailsResponse.class)))
//            .thenReturn(new ResponseEntity(supplementaryDetailsResponse, HttpStatus.PARTIAL_CONTENT));
//
//        restActions
//            .post("/api/ff4j/store/features/payment-search/enable")
//            .andExpect(status().isAccepted());
//        //As IAC supplementary response is 206 so Payment response should be 206
//        MvcResult result = restActions
//            .get("/reconciliation-payments?start_date=" + startDate + "&end_date=" + endDate)
//            .andExpect(status().isPartialContent())
//            .andReturn();
//
//        SupplementaryPaymentDto supplementaryPaymentDto = objectMapper.readValue(result.getResponse().getContentAsString(), SupplementaryPaymentDto.class);
//        assertThat(supplementaryPaymentDto.getPayments().size()).isEqualTo(1);
//        assertNotNull(supplementaryPaymentDto.getSupplementaryInfo());
//    }


    private Date parseDate(String date) {
        try {
            return new SimpleDateFormat("dd.MM.yyyy").parse(date);
        } catch (ParseException e) {
            return null;
        }
    }
}
