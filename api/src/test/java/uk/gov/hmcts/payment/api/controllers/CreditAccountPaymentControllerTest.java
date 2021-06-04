package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.service.CreditAccountPaymentService;
import uk.gov.hmcts.payment.api.util.AccountStatus;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
public class CreditAccountPaymentControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @MockBean
    private AccountService<AccountDto, String> accountService;

    @MockBean
    @Qualifier("loggingCreditAccountPaymentService")
    private CreditAccountPaymentService<PaymentFeeLink, String> creditAccountPaymentService;

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.gooooogle.com");
    }

    @Test
    public void testCreateCreditAccountPayment() throws Exception {
        List<FeeDto> feeDtoList = new ArrayList<>();
        FeeDto fee = FeeDto.feeDtoWith()
                        .calculatedAmount(BigDecimal.valueOf(10))
                        .code("FEE123")
                        .version("1")
                        .build();
        feeDtoList.add(fee);
        CreditAccountPaymentRequest creditAccountPaymentRequest = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
                                                                            .amount(BigDecimal.valueOf(100))
                                                                            .description("New passport application")
                                                                            .ccdCaseNumber("ccd_case_number")
                                                                            .caseReference("12345")
                                                                            .service(Service.FINREM)
                                                                            .currency(CurrencyCode.GBP)
                                                                            .siteId("AA09")
                                                                            .customerReference("customer_reference")
                                                                            .organisationName("org-name")
                                                                            .accountNumber("AC101010")
                                                                            .fees(feeDtoList)
                                                                            .build();
        AccountDto accountDto = AccountDto.accountDtoWith()
                                    .accountName("accountName")
                                    .accountNumber("AC101010")
                                    .creditLimit(BigDecimal.valueOf(100))
                                    .availableBalance(BigDecimal.valueOf(100))
                                    .status(AccountStatus.ACTIVE)
                                    .effectiveDate(Date.valueOf("2020-03-02"))
                                    .build();
        when(accountService.retrieve(anyString())).thenReturn(accountDto);
        when(creditAccountPaymentService.create(any(Payment.class), anyList(), anyString())).thenReturn(getPaymentFeeLink());
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(true);
        MvcResult result =   restActions
                                .post("/credit-account-payments", creditAccountPaymentRequest)
                                .andExpect(status().isCreated())
                                .andReturn();
        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentDto.class);
        assertEquals("2021-1614709196068",paymentDto.getReference());
    }

    @Test
    public void testRetrieve() throws Exception {
        when(creditAccountPaymentService.retrieveByPaymentReference(anyString())).thenReturn(getPaymentFeeLink());
        MvcResult result =   restActions
                                .get("/credit-account-payments/2021-1614709196068")
                                .andExpect(status().isOk())
                                .andReturn();
        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsString(),PaymentDto.class);
        assertEquals("ccd-number",paymentDto.getCcdCaseNumber());
    }

    @Test
    public void testRetrievePaymentStatus() throws Exception {
        when(creditAccountPaymentService.retrieveByPaymentReference(anyString())).thenReturn(getPaymentFeeLink());
        MvcResult result =   restActions
                                .get("/credit-account-payments/2021-1614709196068/statuses")
                                .andExpect(status().isOk())
                                .andReturn();
        PaymentDto paymentDto = objectMapper.readValue(result.getResponse().getContentAsString(),PaymentDto.class);
        assertEquals("2021-1614709196068",paymentDto.getReference());

    }

    private PaymentFeeLink getPaymentFeeLink(){
        List<PaymentFee> paymentFees = new ArrayList<>();
        PaymentFee fee = PaymentFee.feeWith()
            .feeAmount(BigDecimal.valueOf(30))
            .calculatedAmount(BigDecimal.valueOf(101.89))
            .code("X0101")
            .ccdCaseNumber("CCD101")
            .build();
        paymentFees.add(fee);
        List<StatusHistory> statusHistories = new ArrayList<>();
        StatusHistory history = StatusHistory.statusHistoryWith()
                                    .status("success")
                                    .build();
        statusHistories.add(history);
        Payment payment = Payment.paymentWith()
            .paymentStatus(PaymentStatus.SUCCESS)
            .status("success")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("card").build())
            .currency("GBP")
            .caseReference("case-reference")
            .ccdCaseNumber("ccd-number")
            .paymentMethod(PaymentMethod.paymentMethodWith().name("cash").build())
            .dateCreated(Date.valueOf("2020-02-01"))
            .externalReference("external-reference")
            .reference("2021-1614709196068")
            .statusHistories(statusHistories)
            .build();
        List<Payment> paymentList = new ArrayList<>();
        paymentList.add(payment);
        return PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("2021-1614709196068")
            .dateCreated(Date.valueOf("2020-01-20"))
            .dateUpdated(Date.valueOf("2020-01-21"))
            .fees(paymentFees)
            .payments(paymentList)
            .build();
    }

}
