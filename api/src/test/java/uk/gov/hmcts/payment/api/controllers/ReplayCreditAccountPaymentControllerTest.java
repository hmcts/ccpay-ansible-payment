package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.RandomUtils;
import org.assertj.core.util.Files;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.controllers.utils.ReplayCreditAccountPaymentUtils;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.util.AccountStatus;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.util.Files.newFile;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@TestPropertySource(properties = {"duplicate.payment.check.interval.in.minutes=0", "pba.config1.service.names=PROBATE,CMC"})
@Transactional
public class ReplayCreditAccountPaymentControllerTest extends PaymentsDataUtil {

    private final static String PAYMENT_REFERENCE_REFEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

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

    @Autowired
    protected ReplayCreditAccountPaymentUtils replayCreditAccountPaymentUtils;

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
    public void replayCreditAccountPayment_AutomatedTest() throws Exception {

        File paymentsToReplayCSV = null;

        try {
            //create test csv file
            paymentsToReplayCSV = newFile("src/test/resources/paymentsToReplay.csv");

            Map<String, CreditAccountPaymentRequest> csvParseMap = new HashMap<>();

            //Create 10 PBA payments
            createCreditAccountPayments(csvParseMap, 10);

            //Create CSV
            replayCreditAccountPaymentUtils.createCSV(csvParseMap,"paymentsToReplay.csv");

            //Invoke replay-credit-account-payment
            MockMultipartFile csvFile = new MockMultipartFile("csvFile", "paymentsToReplay.csv", "text/csv",
                new FileInputStream(new File("src/test/resources/paymentsToReplay.csv")));

            MvcResult result = restActions
                .postWithMultiPartFileData("/replay-credit-account-payments",
                    csvFile, "isReplayPBAPayments", "true")
                .andExpect(status().isOk())
                .andReturn();

            csvParseMap.entrySet().stream().forEach(entry -> {
                List<Payment> paymentList = db.findByCcdCaseNumber(entry.getValue().getCcdCaseNumber());

                Assert.assertEquals(2, paymentList.size());

                Payment existingPayment = paymentList
                    .stream()
                    .filter(payment -> payment.getReference().equalsIgnoreCase(entry.getKey())).collect(Collectors.toList())
                    .get(0);

                Payment newPayment = paymentList
                    .stream()
                    .filter(payment -> !payment.getReference().equalsIgnoreCase(entry.getKey())).collect(Collectors.toList())
                    .get(0);

                //existing payment should be failed
                Assert.assertEquals("failed", existingPayment.getPaymentStatus().getName());

                //Status history should contain message as System Failure. Not charged
                Assert.assertEquals("System Failure. Not charged", existingPayment.getStatusHistories().get(0).getMessage());

                //new payment should be pending
                Assert.assertEquals("pending", newPayment.getPaymentStatus().getName());

                //Check for each Request Fields & New Payment Fields

                Assert.assertEquals(entry.getValue().getAmount(), newPayment.getAmount());
                Assert.assertEquals(entry.getValue().getCcdCaseNumber(), newPayment.getCcdCaseNumber());
                Assert.assertEquals(entry.getValue().getAccountNumber().replace("\"", ""), newPayment.getPbaNumber());
                Assert.assertEquals(entry.getValue().getDescription(), newPayment.getDescription());
                Assert.assertEquals(entry.getValue().getCaseReference().replace("\"", ""), newPayment.getCaseReference());
                Assert.assertEquals(entry.getValue().getService(), newPayment.getServiceType());
                Assert.assertEquals(entry.getValue().getCurrency().getCode(), newPayment.getCurrency());
                Assert.assertEquals(entry.getValue().getCustomerReference(), newPayment.getCustomerReference());
                Assert.assertEquals(entry.getValue().getOrganisationName().replace("\"", ""), newPayment.getOrganisationName());
                Assert.assertEquals(entry.getValue().getSiteId(), newPayment.getSiteId());
                Assert.assertEquals(entry.getValue().getFees().get(0).getCode(), newPayment.getPaymentLink().getFees().get(0).getCode());
                Assert.assertEquals(entry.getValue().getFees().get(0).getCalculatedAmount(), newPayment.getPaymentLink().getFees().get(0).getCalculatedAmount());
                Assert.assertEquals(entry.getValue().getFees().get(0).getVersion(), newPayment.getPaymentLink().getFees().get(0).getVersion());

            });
        } finally {
            //Delete test csvfile
            Files.delete(paymentsToReplayCSV);
        }
    }

    @Test
    public void markPBAPaymentFailed_AutomatedTest() throws Exception {

        File paymentsToFailCSV = null;

        try {
            //create test csv file
            paymentsToFailCSV = newFile("src/test/resources/paymentsToFailed.csv");

            Map<String, CreditAccountPaymentRequest> csvParseMap = new HashMap<>();

            //Create 10 PBA payments
            createCreditAccountPayments(csvParseMap, 5);

            //Create CSV
            replayCreditAccountPaymentUtils.createCSV(csvParseMap ,"paymentsToFailed.csv");

            //Invoke replay-credit-account-payment
            MockMultipartFile csvFile = new MockMultipartFile("csvFile", "paymentsToFailed.csv", "text/csv",
                new FileInputStream(new File("src/test/resources/paymentsToFailed.csv")));

            MvcResult result = restActions
                .postWithMultiPartFileData("/replay-credit-account-payments",
                    csvFile, "isReplayPBAPayments", "false")
                .andExpect(status().isOk())
                .andReturn();

            csvParseMap.entrySet().stream().forEach(entry -> {
                List<Payment> paymentList = db.findByCcdCaseNumber(entry.getValue().getCcdCaseNumber());

                Assert.assertEquals(paymentList.size(), 1);

                Payment existingPayment = paymentList
                    .stream()
                    .filter(payment -> payment.getReference().equalsIgnoreCase(entry.getKey())).collect(Collectors.toList())
                    .get(0);

                //existing payment should be failed
                Assert.assertEquals("failed", existingPayment.getPaymentStatus().getName());

                //Status history should contain message as System Failure. Not charged
                Assert.assertEquals("System Failure. Not charged", existingPayment.getStatusHistories().get(0).getMessage());

                //Status History table status should be also failed
                Assert.assertEquals("failed", existingPayment.getStatusHistories().get(0).getStatus());
            });
        } finally {
            //Delete test csvfile
            Files.delete(paymentsToFailCSV);
        }
    }

    @Test
    public void replayCreditAccountPayment_AutomatedTest_BadRequest() throws Exception {
        //create test csv file
        final File testCSV = newFile("src/test/resources/emptyFile.csv");

        //Invoke replay-credit-account-payment
        MockMultipartFile csvFile = new MockMultipartFile("csvFile", "emptyFile.csv", "text/csv",
            new FileInputStream(new File("src/test/resources/emptyFile.csv")));

        MvcResult result = restActions
            .postWithMultiPartFileData("/replay-credit-account-payments",
                csvFile, "isReplayPBAPayments", "false")
            .andExpect(status().isBadRequest())
            .andReturn();

        //Delete test csvfile
        Files.delete(testCSV);
    }

    @Test
    public void replayCreditAccountPayment_ReplayNewOnlyIfOldPaymentExists() throws Exception {

        File paymentsToReplayCSV = null;

        try {
            //create test csv file
            paymentsToReplayCSV = newFile("src/test/resources/paymentsToReplay.csv");

            Map<String, CreditAccountPaymentRequest> csvParseMap = new HashMap<>();

            //Create 2 PBA payments
            createCreditAccountPayments(csvParseMap, 2);

            Map<String, CreditAccountPaymentRequest> csvParseMapWithIncorrectPayRef = new HashMap<>();

            csvParseMap.entrySet().stream().forEach(entry -> {
                csvParseMapWithIncorrectPayRef.put("RC-1607-0707-3939-" + RandomUtils.nextInt(9999), entry.getValue());
            });

            //Create CSV
            replayCreditAccountPaymentUtils.createCSV(csvParseMapWithIncorrectPayRef,"paymentsToReplay.csv");

            //Invoke replay-credit-account-payment
            MockMultipartFile csvFile = new MockMultipartFile("csvFile", "paymentsToReplay.csv", "text/csv",
                new FileInputStream(new File("src/test/resources/paymentsToReplay.csv")));

            MvcResult result = restActions
                .postWithMultiPartFileData("/replay-credit-account-payments",
                    csvFile, "isReplayPBAPayments", "true")
                .andExpect(status().isOk())
                .andReturn();

            csvParseMapWithIncorrectPayRef.entrySet().stream().forEach(entry -> {
                List<Payment> paymentList = db.findByCcdCaseNumber(entry.getValue().getCcdCaseNumber());

                Assert.assertEquals(1, paymentList.size());
                Assert.assertNotEquals(entry.getKey(), paymentList.get(0).getReference());

                //Old payment status should be pending
                Assert.assertEquals("pending", paymentList.get(0).getPaymentStatus().getName());
            });
        } finally {
            //Delete test csvfile
            Files.delete(paymentsToReplayCSV);
        }
    }


    private void createCreditAccountPayments(Map<String, CreditAccountPaymentRequest> csvParseMap , int noOfPayments) throws Exception {
        for (int i = 0; i < noOfPayments; i++) {
            //create PBA payment
            String url = replayCreditAccountPaymentUtils.setCreditAccountPaymentLiberataCheckFeature(true);
            restActions
                .post(url)
                .andExpect(status().isAccepted());

            when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

            Double calculatedAmount = Double.parseDouble(Integer.toString(RandomUtils.nextInt(99) + 1));

            List<FeeDto> fees = replayCreditAccountPaymentUtils.getFees(calculatedAmount);

            CreditAccountPaymentRequest request = replayCreditAccountPaymentUtils.getPBAPayment(calculatedAmount, fees);

            AccountDto accountActiveDto = new AccountDto(request.getAccountNumber(), "accountName",
                new BigDecimal(calculatedAmount), new BigDecimal(calculatedAmount), AccountStatus.ACTIVE, new Date());
            Mockito.when(accountService.retrieve(request.getAccountNumber())).thenReturn(accountActiveDto);

            MvcResult result = restActions
                .post("/credit-account-payments", request)
                .andExpect(status().isCreated())
                .andReturn();

            //Overwrite for response validation
            request.setService("Civil Money Claims");

            PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentDto.class);
            csvParseMap.put(paymentDto.getReference(), request);
        }
    }

 }
