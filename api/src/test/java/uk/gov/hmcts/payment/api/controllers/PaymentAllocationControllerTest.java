package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
public class PaymentAllocationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    RestActions restActions;

    @MockBean
    private PaymentService<PaymentFeeLink, String> paymentService;

    @MockBean
    private Payment2Repository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    MockMvc mvc;

    @Before
    public void setup() {
        mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.gooooogle.com");
    }


    @Test
    public void testAddNewFee() throws Exception {
        Mockito.when(paymentService.retrieve(anyString())).thenReturn(getPaymentFeeLink());
        PaymentAllocation paymentAllocation = PaymentAllocation.paymentAllocationWith()
                                                    .receivingOffice("receiving-off")
                                                    .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("allocated").build())
                                                    .paymentGroupReference("group-reference")
                                                    .paymentReference("payment-reference")
                                                    .id(null)
                                                    .dateCreated(Date.valueOf("2020-02-21"))
                                                    .userName("user123")
                                                    .build();
        Payment payment = Payment.paymentWith()
                            .paymentAllocation(Arrays.asList(paymentAllocation))
                            .build();
        Mockito.when(paymentRepository.save(Mockito.any(Payment.class))).thenReturn(payment);
        MvcResult mvcResult = restActions
            .post("/payment-allocations",getPaymentAllocationDto())
            .andExpect(status().isCreated())
            .andReturn();
        PaymentAllocationDto paymentAllocationDto = objectMapper.readValue(mvcResult.getResponse().getContentAsString(),PaymentAllocationDto.class);
        assertEquals("group-reference",paymentAllocationDto.getPaymentGroupReference());
    }

    @Test
    public void testAddNewFee_WhenPaymentNotFound() throws Exception {
        Mockito.when(paymentService.retrieve(anyString())).thenThrow(PaymentNotFoundException.class);
        MvcResult mvcResult = restActions
            .post("/payment-allocations",getPaymentAllocationDto())
            .andExpect(status().isNotFound())
            .andReturn();
    }

    private PaymentFeeLink getPaymentFeeLink(){
        PaymentFee fee = PaymentFee.feeWith()
            .feeAmount(BigDecimal.valueOf(30))
            .calculatedAmount(BigDecimal.valueOf(10))
            .code("FEE-123")
            .build();
        return PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("RC-1234-1234-1234-1234")
            .dateCreated(Date.valueOf("2020-01-20"))
            .dateUpdated(Date.valueOf("2020-01-21"))
            .fees(Arrays.asList(fee))
            .payments(Arrays.asList( Payment.paymentWith()
                .paymentStatus(PaymentStatus.SUCCESS)
                .status("success")
                .paymentChannel(PaymentChannel.paymentChannelWith().name("card").build())
                .currency("GBP")
                .caseReference("case-reference")
                .ccdCaseNumber("ccd-number")
                .paymentMethod(PaymentMethod.paymentMethodWith().name("cash").build())
                .dateCreated(Date.valueOf("2020-02-20"))
                .dateUpdated(Date.valueOf("2020-02-21"))
                .externalReference("external-reference")
                .reference("RC-1234-1234-1234-1234")
                .build()))
            .build();
    }


    private PaymentAllocationDto getPaymentAllocationDto(){
        PaymentAllocationDto paymentAllocationRequest = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentReference("RC-1234-1234-1234-1234")
            .paymentGroupReference("group-reference")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("allocated").build())
            .reason("reason")
            .explanation("explanation")
            .userName("user123")
            .receivingOffice("receiving-off")
            .unidentifiedReason("unidentified-reason")
            .build();
        return  paymentAllocationRequest;
    }
}
