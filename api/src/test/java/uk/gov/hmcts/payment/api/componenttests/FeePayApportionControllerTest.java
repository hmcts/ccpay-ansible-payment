package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilter;
import uk.gov.hmcts.payment.api.controllers.FeePayApportionController;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
public class FeePayApportionControllerTest extends PaymentsDataUtil {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FeePayApportionController feePayApportionController;

    @Autowired
    protected PaymentDbBackdoor db;

    RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private ServiceAuthFilter serviceAuthFilter;

    @InjectMocks
    private ServiceAndUserAuthFilter serviceAndUserAuthFilter;

    @MockBean
    private SecurityUtils securityUtils;


    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @Before
    public void setup() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUID_Roles("UID123","payments"));
        this.restActions = new RestActions(mvc, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withReturnUrl("https://www.gooooogle.com");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "payments")
    public void retrieveApportionDetailsWithReferenceForCardPayments() throws Exception {
        Payment payment = populateCardPaymentToDb("1");
        populateApportionDetails(payment);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .get("/payment-groups/fee-pay-apportion/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentGroupDto.class);
        assertNotNull(paymentGroupDto);
        assertThat(paymentGroupDto.getPayments().get(0).getReference()).isEqualTo(payment.getReference());
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void retrieveApportionDetailsWithReferenceNumber() throws Exception {
        Payment payment = populateCardPaymentToDb("1");
        populateApportionDetails(payment);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .get("/payment-groups/fee-pay-apportion/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentGroupDto.class);
        assertNotNull(paymentGroupDto);
        assertThat(paymentGroupDto.getPayments().get(0).getReference()).isEqualTo(payment.getReference());
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void retrieveApportionDetailsWithReferenceWhenFeeIdIsDifferent() throws Exception {
        Payment payment = populateCardPaymentToDb("1");
        populateApportionDetails(payment);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .get("/payment-groups/fee-pay-apportion/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentGroupDto.class);
        assertNotNull(paymentGroupDto);
        assertThat(paymentGroupDto.getPayments().get(0).getReference()).isEqualTo(payment.getReference());
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void retrieveApportionDetailsWithReferenceWhenFeeIdIsSame() throws Exception {
        Payment payment = populateCardPaymentToDb("1");
        populateApportionDetails(payment);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(false);
        MvcResult result = restActions
            .get("/payment-groups/fee-pay-apportion/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentGroupDto.class);
        assertNotNull(paymentGroupDto);
        assertThat(paymentGroupDto.getPayments().get(0).getReference()).isEqualTo(payment.getReference());
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void retrunEmptyListWhenPaymentIsNotPresent() throws Exception {
        Payment payment = populateCardPaymentToDb("1");
        populateApportionDetails(payment);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(false);
        MvcResult result = restActions
            .get("/payment-groups/fee-pay-apportion/" + "123")
            .andExpect(status().isNotFound())
            .andReturn();
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void getting404PaymentNotFoundException() throws Exception {
        String errorMessage = "errorMessage";
        PaymentNotFoundException ex = new PaymentNotFoundException(errorMessage);
        assertEquals(errorMessage, feePayApportionController.notFound(ex));
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void retrieveApportionDetailsWithReference() throws Exception {
        Payment payment = populateCardPaymentToDb("1");
        populateApportionDetails(payment);
        when(featureToggler.getBooleanValue("apportion-feature",false)).thenReturn(true);
        MvcResult result = restActions
            .get("/payment-groups/fee-pay-apportion/" + payment.getReference())
            .andExpect(status().isOk())
            .andReturn();

        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentGroupDto.class);
        assertNotNull(paymentGroupDto);
        assertThat(paymentGroupDto.getPayments().get(0).getReference()).isEqualTo(payment.getReference());
    }

}
