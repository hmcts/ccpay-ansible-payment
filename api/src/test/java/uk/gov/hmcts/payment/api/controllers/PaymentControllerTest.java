package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.UpdatePaymentRequest;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
public class PaymentControllerTest {
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
    private PaymentService<PaymentFeeLink, String> paymentService;

    @MockBean
    private PaymentStatusRepository paymentStatusRepository;

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");

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
    public void testUpdateCaseReference() throws Exception {
        when(paymentService.retrieve(anyString())).thenReturn(getPaymentFeeLink());
        UpdatePaymentRequest request = UpdatePaymentRequest.updatePaymentRequestWith()
                                                .caseReference("case-reference")
                                                .ccdCaseNumber("ccd-number").build();
        MvcResult mvcResult = restActions
            .patch("/payments/RC-1234-1234-1234-1234",request)
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    public void  testRetrievePayments() throws Exception {
        String startDate = LocalDate.parse("2020-01-20").toString(DATE_FORMAT);
        String endDate = LocalDate.parse("2020-01-23").toString(DATE_FORMAT);
        when(paymentService.search(any(PaymentSearchCriteria.class))).thenReturn(getPaymentFeeLinkList());
        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();
        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(),PaymentsResponse.class);
        assertEquals("ccd-number",paymentsResponse.getPayments().get(0).getCcdCaseNumber());
    }

    @Test
    public void testUpdatePaymentStatus() throws Exception {
        when(paymentStatusRepository.findByNameOrThrow(anyString())).thenReturn(PaymentStatus.FAILED);
        when(paymentService.retrieve(anyString())).thenReturn(getPaymentFeeLink());
        MvcResult result = restActions
            .patch("/payments/RC-1234-1234-1234-1234/status/failed")
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    public void testRetrievePayment() throws Exception {
        when(paymentService.retrieve(anyString())).thenReturn(getPaymentFeeLink());
        MvcResult result = restActions
            .get("/payments/RC-1234-1234-1234-1234")
            .andExpect(status().isOk())
            .andReturn();
    }

    private PaymentFeeLink getPaymentFeeLink(){
        List<PaymentFee> paymentFees = new ArrayList<>();
        PaymentFee fee = PaymentFee.feeWith()
            .feeAmount(BigDecimal.valueOf(30))
            .calculatedAmount(BigDecimal.valueOf(10))
            .code("FEE-123")
            .build();
        paymentFees.add(fee);
        Payment payment = Payment.paymentWith()
            .paymentStatus(PaymentStatus.SUCCESS)
            .status("success")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("card").build())
            .currency("GBP")
            .caseReference("case-reference")
            .ccdCaseNumber("ccd-number")
            .paymentMethod(PaymentMethod.paymentMethodWith().name("cash").build())
            .dateCreated(Date.valueOf("2020-01-20"))
            .dateUpdated(Date.valueOf("2020-01-21"))
            .externalReference("external-reference")
            .reference("RC-1234-1234-1234-1234")
            .build();
        List<Payment> paymentList = new ArrayList<>();
        paymentList.add(payment);
        return PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("RC-1234-1234-1234-1234")
            .dateCreated(Date.valueOf("2020-01-20"))
            .dateUpdated(Date.valueOf("2020-01-21"))
            .fees(paymentFees)
            .payments(paymentList)
            .build();
    }

    private List<PaymentFeeLink> getPaymentFeeLinkList(){
        List<PaymentFeeLink> paymentFeeLinkList = new ArrayList<>();
        paymentFeeLinkList.add(getPaymentFeeLink());
        return paymentFeeLinkList;
    }
}
