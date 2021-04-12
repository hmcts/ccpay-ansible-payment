package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentGroupDtoMapper;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.FeesService;
import uk.gov.hmcts.payment.api.service.PaymentGroupService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
public class CaseControllerTest {
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
    private PaymentGroupService<PaymentFeeLink, String> paymentGroupService;

    @MockBean
    private PaymentDtoMapper paymentDtoMapper;

    @MockBean
    private PaymentGroupDtoMapper paymentGroupDtoMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_ID = UserResolverBackdoor.CASEWORKER_ID;

    @Before
    public void setUp(){
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");
    }


    @Test
    public void testRetrieveCasePayments() throws Exception {
        when(paymentService.search(any(PaymentSearchCriteria.class))).thenReturn(Arrays.asList(mock(PaymentFeeLink.class)));
        when(paymentDtoMapper.toReconciliationResponseDto(any(PaymentFeeLink.class))).thenReturn(PaymentDto.payment2DtoWith().build());
        MvcResult result = restActions
            .get("/cases/12341234213412/payments")
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void testRetrieveCasePayments_ThrowsPaymentNotFound() throws Exception {
        when(paymentService.search(any(PaymentSearchCriteria.class))).thenReturn(Arrays.asList());
        MvcResult result = restActions
            .get("/cases/12341234213412/payments")
            .andExpect(status().isNotFound())
            .andReturn();
    }


    @Test
    public void testRetrieveCasePaymentGroups() throws Exception {
        when(paymentGroupService.search(anyString())).thenReturn(Arrays.asList(mock(PaymentFeeLink.class)));
        when(paymentGroupDtoMapper.toPaymentGroupDto(any(PaymentFeeLink.class))).thenReturn(PaymentGroupDto.paymentGroupDtoWith().build());
        MvcResult result = restActions
            .get("/cases/12341234213412/paymentgroups")
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void testRetrieveCasePaymentGroups_ThrowsPaymentGroupNotFoundException() throws Exception {
        when(paymentGroupService.search(anyString())).thenReturn(Arrays.asList());
        MvcResult result = restActions
            .get("/cases/12341234213412/paymentgroups")
            .andExpect(status().isNotFound())
            .andReturn();
    }

}
