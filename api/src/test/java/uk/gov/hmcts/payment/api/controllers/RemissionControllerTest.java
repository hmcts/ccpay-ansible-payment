package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.PaymentFeeDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.RemissionDbBackdoor;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.dto.RemissionServiceRequest;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.Remission;
import uk.gov.hmcts.payment.api.service.RemissionService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.model.exceptions.InvalidPaymentGroupReferenceException;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class RemissionControllerTest {
    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private RemissionService remissionService;

    @MockBean
    private SiteService<Site, String> siteServiceMock;

    private static final String USER_ID = UserResolverBackdoor.CITIZEN_ID;

    RemissionRequest remission;
    @Before
    public void setUp() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");

        remission = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .hwfReference("HWFref")
            .siteId("AA001")
            .fee(getFee())
            .build();

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
    public void shouldReturnRemissionCreatedForRemissionV1() throws Exception {

        List<Remission> remissionList = new ArrayList<>();
        Remission remission1 = Remission.remissionWith()
                                    .remissionReference("remission-reference")
                                    .build();
        remissionList.add(remission1);
        PaymentFee fee = PaymentFee.feeWith().build();
        List<PaymentFee> paymentFees = new ArrayList<>();
        paymentFees.add(fee);
        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
                                            .remissions(remissionList)
                                            .payments(null)
                                            .paymentReference("payment-reference")
                                            .fees(paymentFees)
                                            .build();
        when(remissionService.createRemission(Mockito.any(RemissionServiceRequest.class))).thenReturn(paymentFeeLink);
        MvcResult mvcResult = restActions
                            .post("/remission", remission)
                            .andExpect(status().isCreated())
                            .andReturn();
        RemissionDto remissionDto = (RemissionDto) objectMapper.readValue(mvcResult.getResponse().getContentAsString(),RemissionDto.class);
        assertEquals("remission-reference",remissionDto.getRemissionReference());
    }

    @Test
    public void shouldSendRemissionCreatedForRemissio() throws Exception {
        PaymentFeeLink paymentFeeLink = getPaymentFeeLink();
        when(remissionService.createRemission(Mockito.any(RemissionServiceRequest.class))).thenReturn(paymentFeeLink);
        MvcResult mvcResult = restActions
            .post("/remissions", remission)
            .andExpect(status().isCreated())
            .andReturn();
        RemissionDto remissionDto = (RemissionDto) objectMapper.readValue(mvcResult.getResponse().getContentAsString(),RemissionDto.class);
        assertEquals("remission-reference",remissionDto.getRemissionReference());
    }

    @Test
    public void shouldSendBadRequestWhenDataIntegrityViolationException() throws Exception {
        when(remissionService.createRemission(Mockito.any(RemissionServiceRequest.class))).thenThrow(DataIntegrityViolationException.class);
        MvcResult mvcResult = restActions
            .post("/remission", remission)
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    public void  shouldSendBadRequestWhenInvalidPaymentGroupReferenceException() throws Exception {
        when(remissionService.createRemission(Mockito.any(RemissionServiceRequest.class))).thenThrow(InvalidPaymentGroupReferenceException.class);
        MvcResult mvcResult = restActions
            .post("/remission", remission)
            .andExpect(status().isNotFound())
            .andReturn();
    }

    private PaymentFeeLink getPaymentFeeLink(){
        List<Remission> remissionList = new ArrayList<>();
        Remission remission1 = Remission.remissionWith()
            .remissionReference("remission-reference")
            .build();
        remissionList.add(remission1);
        PaymentFee fee = PaymentFee.feeWith().build();
        List<PaymentFee> paymentFees = new ArrayList<>();
        paymentFees.add(fee);
        return PaymentFeeLink.paymentFeeLinkWith()
            .remissions(remissionList)
            .payments(null)
            .paymentReference("payment-reference")
            .fees(paymentFees)
            .build();

    }

    private FeeDto getFee() {
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("10.00"))
            .ccdCaseNumber("CCD1234")
            .version("1")
            .code("FEE0123")
            .build();
    }
}
