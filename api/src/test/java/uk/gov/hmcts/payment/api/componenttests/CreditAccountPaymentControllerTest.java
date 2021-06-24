package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.exception.AccountServiceUnavailableException;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.util.AccountStatus;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.lang.String.format;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class CreditAccountPaymentControllerTest extends PaymentsDataUtil {

    private final static String PAYMENT_REFERENCE_REFEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";
    private static final int CCD_CASE_NUMBER_MIN_VALUE = 100000000;
    private static final int CCD_CASE_NUMBER_MAX_VALUE = 999999999;

    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    @Autowired
    protected PaymentDbBackdoor db;

    @Autowired
    protected Payment2Repository paymentRepository;

    @Autowired
    protected AccountService<AccountDto, String> accountService;

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");

        Mockito.reset(accountService);
    }


    @Test
    public void createCreditAccountPaymentTest() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());
    }

    @Test
    public void rejectDuplicatePayment_ccdCaseNumber() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);
        request.setCcdCaseNumber(ccdCaseNumber);
        request.setCaseReference(null);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());

        // 2nd request
        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isBadRequest());
    }

    @Test
    public void rejectDuplicatePayment_caseReference() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);

        request.setCcdCaseNumber(ccdCaseNumber);
        request.setCaseReference("33333");

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());

        // 2nd request
        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldNotRejectDuplicatePaymentIfAmountIsDifferent() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());

        // different amount for the 2nd request
        request.setAmount(BigDecimal.valueOf(500.50));
        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());
    }

    @Test
    public void shouldNotRejectDuplicatePaymentIfFeeCodeIsDifferent() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());

        // different fee code for the 2nd request
        FeeDto x0102 = FeeDto.feeDtoWith().code("X0102").version("1").calculatedAmount(BigDecimal.valueOf(101.89)).build();
        request.setFees(Lists.newArrayList(x0102));
        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());
    }

    @Test
    public void shouldNotRejectDuplicatePaymentIfFeeVersionIsDifferent() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName", new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());

        // different fee version for the 2nd request
        FeeDto x0101_v4 = FeeDto.feeDtoWith().code("X0101").version("4").calculatedAmount(BigDecimal.valueOf(101.89)).build();
        request.setFees(Lists.newArrayList(x0101_v4));
        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());
    }

    @Test
    public void retrieveCreditAccountPaymentByPaymentReference() throws Exception {
        //Create a payment in remissionDbBackdoor
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("Reference1")
            .ccdCaseNumber("ccdCaseNumber1")
            .description("Description1")
            .serviceType("Probate")
            .currency("GBP")
            .siteId("AA01")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .reference("RC-1519-9028-1909-3475")
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186162020").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/credit-account-payments/RC-1519-9028-1909-3475")
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(response);
        assertTrue(response.getReference().matches(PAYMENT_REFERENCE_REFEX));
        assertEquals(response.getAmount(), new BigDecimal("11.99"));
    }

    @Test
    public void validCreditAccountPaymentRequestJsonTest() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJson().getBytes(), CreditAccountPaymentRequest.class);
        assertNotNull(request);
    }

    @Test
    public void createCreditAccountPayment_withInvalidRequestJsonTest() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentInvalidRequestJson().getBytes(), CreditAccountPaymentRequest.class);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createCreditAccountPayment_WithInvalidServiceNameTest() throws Exception {

        restActions
            .post(format("/credit-account-payments"), creditAccountPaymentRequestJsonWithInvalidServiceName())
            .andExpect(status().isBadRequest())
            .andReturn();

    }

    @Test
    public void retrievePaymentStatusesTest() throws Exception {
        //Create a payment in remissionDbBackdoor
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("Reference11")
            .ccdCaseNumber("ccdCaseNumber11")
            .description("Description11")
            .serviceType("Probate")
            .currency("GBP")
            .siteId("AA011")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("payment by account").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .reference("RC-1519-9239-1920-0375")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .status("pending")
                .build()))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15199028243").payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

        Payment savedPayment = paymentFeeLink.getPayments().get(0);

        MvcResult result = restActions
            .get("/credit-account-payments/RC-1519-9239-1920-0375/statuses")
            .andExpect(status().isOk())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(payment);
        assertEquals(paymentDto.getReference(), payment.getReference());
        paymentDto.getStatusHistories().stream().forEach(h -> {
            assertEquals(h.getStatus(), "Pending");
        });
    }

    @Test
    public void validateCreateCreditAccountPayment_InvalidCcdCaseNumberTest() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(jsonRequestWithInvalidCcdCaseRef().getBytes(), CreditAccountPaymentRequest.class);

        MvcResult res = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
        assertEquals("Invalid or missing attribute", res.getResponse().getErrorMessage());
    }

    @Test
    public void createCreditAccountPayment_withEitherCcdCaseNumberOrCaseReferenceTest() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
        assertEquals("Success", paymentDto.getStatus());
    }

    @Test
    public void failCreditAccountPaymentForFinRemAndLiberataRespondsAccountHasInsufficientFundsShouldReturnPaymentFailed() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(100), new BigDecimal(100), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isForbidden()).andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        assertEquals("Failed", paymentDto.getStatus());
        assertEquals("CA-E0001", paymentDto.getStatusHistories().get(0).getErrorCode());
        assertEquals("Payment request failed. PBA account accountName have insufficient funds available", paymentDto.getStatusHistories().get(0).getErrorMessage());
    }

    @Test
    public void failCreditAccountPaymentWhenLiberataRespondsAccountStatusOnHold() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountOnHoldDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ON_HOLD, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountOnHoldDto);

        MvcResult result = restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isForbidden()).andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        assertEquals("Failed", paymentDto.getStatus());
        assertEquals("CA-E0003", paymentDto.getStatusHistories().get(0).getErrorCode());
        assertEquals("Your account is on hold", paymentDto.getStatusHistories().get(0).getErrorMessage());
    }

    @Test
    public void failCreditAccountPaymentWhenLiberataRespondsAccountStatusDeleted() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountDeletedDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(100), new BigDecimal(100), AccountStatus.DELETED, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountDeletedDto);

        MvcResult result = restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isForbidden()).andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        assertEquals("Failed", paymentDto.getStatus());
        assertEquals("CA-E0004", paymentDto.getStatusHistories().get(0).getErrorCode());
        assertEquals("Your account is deleted", paymentDto.getStatusHistories().get(0).getErrorMessage());
    }

    @Test
    public void createCreditAccountPaymentAndLiberataRespondsCannotFindAccountShouldReturn404() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenThrow(HttpClientErrorException.class);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isNotFound());
    }

    @Test
    public void createCreditAccountPaymentAndLiberataIsNotResponsiveShouldReturn504() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenThrow(AccountServiceUnavailableException.class);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isGatewayTimeout());
    }

    @Test
    public void createCreditAccountPaymentWithSuccessWhenAvailableBalanceGreaterThanRequestedAmount() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(),
            CreditAccountPaymentRequest.class);
        request.setAmount(new BigDecimal(50));

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(100), new BigDecimal(100), AccountStatus.ACTIVE, new Date());

        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated()).andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        assertEquals("Success", paymentDto.getStatus());
    }

    @Test
    public void createCreditAccountPaymentWithSuccessWhenAvailableBalanceEqualToRequestedAmount() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(),
            CreditAccountPaymentRequest.class);
        request.setAmount(new BigDecimal("100.99"));

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(200), new BigDecimal("100.99"), AccountStatus.ACTIVE, new Date());

        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated()).andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        assertEquals("Success", paymentDto.getStatus());
    }

    @Test
    public void createCreditAccountPaymentWithFailedWhenAvailableBalanceGreaterThanRequestedAmount() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(),
            CreditAccountPaymentRequest.class);
        request.setAmount(new BigDecimal(101));

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(100), new BigDecimal("100.99"), AccountStatus.ACTIVE, new Date());

        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isForbidden()).andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        assertEquals("Failed", paymentDto.getStatus());
        assertEquals("CA-E0001", paymentDto.getStatusHistories().get(0).getErrorCode());
        assertEquals("Payment request failed. PBA account accountName have insufficient funds available", paymentDto.getStatusHistories().get(0).getErrorMessage());
    }

    @Test
    public void givenLiberataCheckOnAndCheckLiberataAccountForAllSericesOnThenAllServicesTriggerLiberataCheck() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void givenLiberataCheckOffAndCheckLiberataAccountForAllSericesOffThenNoServiceTriggersLiberataCheck() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void givenLiberataCheckOnAndCheckLiberataAccountForAllSericesOffThenOnlyFINREMTriggersLiberataCheck() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void givenLiberataCheckOnAndCheckLiberataAccountForAllSericesOffThenOnlyFPLATriggersLiberataCheck() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFPLAJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void givenLiberataCheckOnAndCheckLiberataAccountForAllSericesOffThenOnlyIACTriggersLiberataCheck() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithIACJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }


    @Test
    public void givenLiberataCheckOffAndCheckLiberataAccountForAllSericesOnThenAllServicesTriggerLiberataCheck() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFinRemJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        // Retrieve payment by payment group reference
        MvcResult result3 = restActions
            .get("/payment-groups/" + paymentDto.getPaymentGroupReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result3.getResponse().getContentAsByteArray(), PaymentGroupDto.class);
        PaymentDto paymentDtoForCredit = paymentGroupDto.getPayments().get(0);
        //PAY-2856-Missing PBA details changes
        assertTrue(paymentDtoForCredit.getAccountNumber().equalsIgnoreCase("AC101010"));
        assertTrue(paymentDtoForCredit.getCustomerReference().equalsIgnoreCase("CUST101"));
        assertTrue(paymentDtoForCredit.getOrganisationName().equalsIgnoreCase("ORG101"));

    }

    @Test
    public void givenLiberataCheckOffAndCheckLiberataAccountForAllServicesOnThenAllServicesTriggerLiberataCheck_FPLA() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFPLAJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void givenLiberataCheckOffAndCheckLiberataAccountForAllServicesOnThenAllServicesTriggerLiberataCheck_IAC() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithIAC_Json().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void checkPBAPaymentsFor_Civil_Service() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithCivil_Json().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);

        request = objectMapper.readValue(creditAccountPaymentRequestJsonWithProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
        assertNotNull(paymentDto);
    }

    @Test
    public void checkPBAPaymentsErrorWithDifferentSiteIdFor_Civil_Service() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithDifferentSiteIdForCivil_Json().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void checkPBAPaymentsErrorWithNoServiceNameFor_Civil_Service() throws Exception {

        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithNoServiceNameForCivil_Json().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    }

    @Test
    public void createCreditAccountPaymentTest_FPLService() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFPLJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isCreated());
    }

    @Test
    public void createCreditAccountPaymentTest_FPLService_InvalidSiteId() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithFPLJsonInvalidSiteId().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createCreditAccountPaymentTest_ProbateService_InvalidSiteId() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithInvalidSiteIdForProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createCreditAccountPaymentTest_ProbateService_NoSiteId() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithNoSiteIdForProbateJson().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isUnprocessableEntity());
    }


    @Test
    public void createCreditAccountPaymentTest_IACService_InvalidSiteId() throws Exception {
        CreditAccountPaymentRequest request = objectMapper.readValue(creditAccountPaymentRequestJsonWithIACLJsonInvalidSiteId().getBytes(), CreditAccountPaymentRequest.class);
        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        restActions
            .post(format("/credit-account-payments"), request)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createCreditAccountPaymentWithMultipleFee_ExactPayment() throws Exception {

        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest request = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("AC101010")
            .fees(fees)
            .build();

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

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
    public void createCreditAccountPaymentWithMultipleFee_ShortfallPayment() throws Exception {

        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(30))
            .volume(1).version("1").calculatedAmount(new BigDecimal(30)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest request = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .fees(fees)
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("AC101010")
            .build();

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(10)).build();
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
        assertEquals(new BigDecimal(10), savedfees.get(2).getAmountDue());
    }

    @Test
    public void createCreditAccountPaymentWithMultipleFee_SurplusPayment() throws Exception {

        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(10))
            .volume(1).version("1").calculatedAmount(new BigDecimal(10)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest request = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("120"))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.PROBATE)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .fees(fees)
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("AC101010")
            .build();

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(-10)).build();
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
        assertEquals(new BigDecimal(-10), savedfees.get(2).getAmountDue());
    }

    @Test
    public void createCreditAccountPaymentWithMultipleFee_AmountDue() throws Exception {

        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);

        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);

        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest request = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("100"))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.FPL)
            .currency(CurrencyCode.GBP)
            .siteId("ABA3")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("AC101010")
            .fees(fees)
            .build();

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(20)).build();
        mockFees.add(fee1);
        mockFees.add(fee2);
        mockFees.add(fee3);
        PaymentFeeLink mockFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .fees(mockFees)
            .build();
        PaymentDbBackdoor mockDb = mock(PaymentDbBackdoor.class);
        when(mockDb.findByReference(paymentDto.getPaymentGroupReference())).thenReturn(mockFeeLink);

        List<PaymentFee> savedfees = mockDb.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        assertEquals(BigDecimal.valueOf(0), savedfees.get(0).getAmountDue());
        assertEquals(BigDecimal.valueOf(0), savedfees.get(1).getAmountDue());
        assertEquals(BigDecimal.valueOf(20), savedfees.get(2).getAmountDue());
    }

    @Test
    public void createCreditAccountPaymentWithMultipleFee_AmountDue_When_Apportion_Flag_Is_True() throws Exception {

        String ccdCaseNumber = "1111222" + RandomUtils.nextInt(CCD_CASE_NUMBER_MIN_VALUE, CCD_CASE_NUMBER_MAX_VALUE);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);
        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(20))
            .volume(1).version("1").calculatedAmount(new BigDecimal(20)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(40))
            .volume(1).version("1").calculatedAmount(new BigDecimal(40)).build());
        fees.add(FeeDto.feeDtoWith().code("FEE0271").ccdCaseNumber(ccdCaseNumber).feeAmount(new BigDecimal(60))
            .volume(1).version("1").calculatedAmount(new BigDecimal(60)).build());

        CreditAccountPaymentRequest request = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal("100"))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(Service.FPL)
            .currency(CurrencyCode.GBP)
            .siteId("ABA3")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("AC101010")
            .fees(fees)
            .build();

        AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
            new BigDecimal(1000), new BigDecimal(1000), AccountStatus.ACTIVE, new Date());
        Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

        MvcResult result = restActions
            .post("/credit-account-payments", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);

        List<PaymentFee> mockFees = new ArrayList<>();
        PaymentFee fee1 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee2 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(0)).build();
        PaymentFee fee3 = PaymentFee.feeWith().amountDue(BigDecimal.valueOf(20)).build();
        mockFees.add(fee1);
        mockFees.add(fee2);
        mockFees.add(fee3);
        PaymentFeeLink mockFeeLink = PaymentFeeLink.paymentFeeLinkWith()
                                        .fees(mockFees)
                                        .build();
        PaymentDbBackdoor mockDb = mock(PaymentDbBackdoor.class);
        when(mockDb.findByReference(paymentDto.getPaymentGroupReference())).thenReturn(mockFeeLink);

        List<PaymentFee> savedfees = mockDb.findByReference(paymentDto.getPaymentGroupReference()).getFees();

        assertEquals(BigDecimal.valueOf(0), savedfees.get(0).getAmountDue());
        assertEquals(BigDecimal.valueOf(0), savedfees.get(1).getAmountDue());
        assertEquals(BigDecimal.valueOf(20), savedfees.get(2).getAmountDue());
    }

    private String jsonRequestWithInvalidCcdCaseRef() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"ccd_case_number\": \"1111\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"ABA6\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String jsonRequestWithCaseReference() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"case_reference\": \"caseReference\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AA101\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"ABA6\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithFinRemJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"FINREM\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AA101\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithProbateJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"ABA6\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithInvalidSiteIdForProbateJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AA00\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithNoSiteIdForProbateJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentInvalidRequestJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"PROBATE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AA101\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "  ]\n" +
            "}";
    }


    private String creditAccountPaymentRequestJsonWithInvalidServiceName() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"NO_SERVICE\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AA101\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithFPLJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"FPL\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"ABA3\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithFPLJsonInvalidSiteId() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"FPL\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AA07\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithIACLJsonInvalidSiteId() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"IAC\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"BFA0\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithFPLAJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"FPL\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"ABA3\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }


    private String creditAccountPaymentRequestJsonWithIAC_Json() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"IAC\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"BFA1\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithCivil_Json() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"CIVIL\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"AAA7\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithDifferentSiteIdForCivil_Json() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"CIVIL\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"A000\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithNoServiceNameForCivil_Json() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"A000\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }

    private String creditAccountPaymentRequestJsonWithIACJson() {
        return "{\n" +
            "  \"amount\": 101.89,\n" +
            "  \"description\": \"New passport application\",\n" +
            "  \"ccd_case_number\": \"1111222233334444\",\n" +
            "  \"case_reference\": \"12345\",\n" +
            "  \"service\": \"IAC\",\n" +
            "  \"currency\": \"GBP\",\n" +
            "  \"site_id\": \"BFA1\",\n" +
            "  \"customer_reference\": \"CUST101\",\n" +
            "  \"organisation_name\": \"ORG101\",\n" +
            "  \"account_number\": \"AC101010\",\n" +
            "  \"fees\": [\n" +
            "    {\n" +
            "      \"calculated_amount\": 101.89,\n" +
            "      \"code\": \"X0101\",\n" +
            "      \"version\": \"1\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    }
}
