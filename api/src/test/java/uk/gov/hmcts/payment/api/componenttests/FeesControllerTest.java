package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilter;
import uk.gov.hmcts.payment.api.configuration.security.ServicePaymentFilter;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilterTest.getUserInfoBasedOnUID_Roles;
@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
@EnableFeignClients
@AutoConfigureMockMvc
public class FeesControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ServiceAuthFilter serviceAuthFilter;

    @Autowired
    private ServicePaymentFilter servicePaymentFilter;

    @InjectMocks
    private ServiceAndUserAuthFilter serviceAndUserAuthFilter;

    @MockBean
    private SecurityUtils securityUtils;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;


    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @MockBean
    private SiteService<Site, String> siteServiceMock;

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, objectMapper);
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("UID123","payments"));
        restActions
            .withAuthorizedService("divorce")
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
    @WithMockUser(authorities = "payments")
    public void deleteFeesTest() throws Exception {

        PaymentGroupDto request = PaymentGroupDto.paymentGroupDtoWith()
            .fees( Arrays.asList(getNewFee()))
            .build();

        MvcResult result = restActions
            .post("/payment-groups", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), PaymentGroupDto.class);

        Integer feeId = paymentGroupDto.getFees().get(0).getId();
        MvcResult result1 = restActions.
            delete("/fees/"+ feeId)
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void deleteFeesCreatedUsingRemissionTest() throws Exception {

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .beneficiaryName("beneficiary")
            .caseReference("caseRef1234")
            .ccdCaseNumber("CCD1234")
            .hwfAmount(new BigDecimal("10.00"))
            .siteId("AA001")
            .hwfReference("HWFref")
            .fee(getFee())
            .build();

        MvcResult result = restActions
            .post("/remission", remissionRequest)
            .andExpect(status().isCreated())
            .andReturn();

        RemissionDto remissionDto = objectMapper.readValue(result.getResponse().getContentAsByteArray(), RemissionDto.class);

        Integer feeId = remissionDto.getFee().getId();
        MvcResult result1 = restActions.
            delete("/fees/"+ feeId)
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void deleteNoFeesExistingTest() throws Exception {

        Integer feeId = 12;
        MvcResult result1 = restActions.
            delete("/fees/"+ feeId)
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    private FeeDto getNewFee(){
        return FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal("92.19"))
            .code("FEE312")
            .version("1")
            .volume(2)
            .id(1)
            .reference("BXsd1123")
            .ccdCaseNumber("1111-2222-2222-1111")
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
