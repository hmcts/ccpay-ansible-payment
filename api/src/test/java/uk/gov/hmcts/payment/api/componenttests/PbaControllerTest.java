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
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilter;
import uk.gov.hmcts.payment.api.configuration.security.ServicePaymentFilter;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilterTest.getUserInfoBasedOnUID_Roles;

@RunWith(SpringRunner.class)
@ActiveProfiles({"componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@EnableFeignClients
@AutoConfigureMockMvc
@Transactional
public class PbaControllerTest extends PaymentsDataUtil {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected PaymentDbBackdoor db;

    private RestActions restActions;

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

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, objectMapper);
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("UID123","payments"));
        restActions
            .withAuthorizedService("divorce")
            .withReturnUrl("https://www.gooooogle.com");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "payments")
    public void searchCreditPayments_withPbaNumber() throws Exception {

        populateCardPaymentToDb("1");
        populateCreditAccountPaymentToDb("2");

        MvcResult result = restActions
            .get("/pba-accounts/123456/payments")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertPbaPayments(paymentsResponse.getPayments());

    }

    @Test
    @Transactional
    @WithMockUser(authorities = "payments")
    public void searchCreditPayments_whichHaveNetAmountAsCalculatedAmount() throws Exception {
        BigDecimal calculatedAmount = new BigDecimal("13.33");
        BigDecimal netAmount = new BigDecimal("23.33");
        populateCreditAccountPaymentToDbWithNetAmountForFee("1", calculatedAmount, netAmount);

        MvcResult result = restActions
            .get("/pba-accounts/123456/payments")
            .andExpect(status().isOk())
            .andReturn();

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertEquals(1, paymentsResponse.getPayments().size());
        assertEquals(netAmount,  paymentsResponse.getPayments().get(0).getFees().get(0).getCalculatedAmount());
        assertNotNull("net_amount should be set", paymentsResponse.getPayments().get(0).getFees().get(0).getNetAmount());
    }
}
