package uk.gov.hmcts.payment.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.PaymentDbBackdoor;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.controllers.FeePayApportionController;
import uk.gov.hmcts.payment.api.domain.service.FeeDomainService;
import uk.gov.hmcts.payment.api.domain.service.PaymentDomainService;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.order.OrderDto;
import uk.gov.hmcts.payment.api.dto.order.OrderFeeDto;
import uk.gov.hmcts.payment.api.dto.order.OrderPaymentDto;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class FeePayApportionControllerTest extends PaymentsDataUtil{
    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    @Autowired
    private FeePayApportionController feePayApportionController;

    @Autowired
    protected PaymentDbBackdoor db;

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @MockBean
    ReferenceDataService referenceDataService;

    @MockBean
    private PaymentDomainService paymentDomainService;

    @MockBean
    private FeeDomainService feeDomainService;

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
            .withReturnUrl("https://www.gooooogle.com");
    }

    @Test
    @Transactional
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
    public void getting404PaymentNotFoundException() throws Exception {
        String errorMessage = "errorMessage";
        PaymentNotFoundException ex = new PaymentNotFoundException(errorMessage);
        assertEquals(errorMessage, feePayApportionController.notFound(ex));
    }

    @Test
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


    @Test
    public void testRetrieveApportionDetailsForOrders() throws Exception {
        when(paymentDomainService.getPaymentByReference(anyString())).thenReturn(getPayment());
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(true);
        when(feeDomainService.getPaymentFeeById(any())).thenReturn(getPaymentFee());
        when(paymentDomainService.getFeePayApportionByPaymentId(any())).thenReturn(getFeePayApportionList());

        MvcResult result = restActions
            .get("/orderpoc/payment-groups/fee-pay-apportion/RC-1603-1374-9345-6197")
            .andExpect(status().isOk())
            .andReturn();
        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(),PaymentGroupDto.class);
        Assertions.assertEquals(paymentGroupDto.getPayments().get(0).getCcdCaseNumber(),"ccdCaseNumber");
        Assertions.assertEquals(paymentGroupDto.getPayments().get(0).getAmount(),new BigDecimal("99.99"));
    }

    @Test
    public void testRetrieveApportionDetailsForOrdersWithEmptyApportions() throws Exception {
        Mockito.when(paymentDomainService.getPaymentByReference(anyString())).thenReturn(getPayment());
        Mockito.when(paymentDomainService.getFeePayApportionByPaymentId(anyInt())).thenReturn(Collections.emptyList());
        MvcResult result = restActions
            .get("/orderpoc/payment-groups/fee-pay-apportion/RC-1603-1374-9345-6197")
            .andExpect(status().isOk())
            .andReturn();
        PaymentGroupDto paymentGroupDto = objectMapper.readValue(result.getResponse().getContentAsString(),PaymentGroupDto.class);
        assertNull(paymentGroupDto.getFees());
    }

    private Payment getPayment(){
        return Payment.paymentWith()
            .amount(new BigDecimal("99.99"))
            .caseReference("Reference")
            .ccdCaseNumber("ccdCaseNumber")
            .description("Test payments statuses for ")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA09")
            .userId(USER_ID)
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference("e2kkddts5215h9qqoeuth5c0v")
            .reference("RC-1603-1374-9345-6197")
            .build();
    }

    private List<FeePayApportion> getFeePayApportionList(){
        List<FeePayApportion> feePayApportionList = new ArrayList<>();
        feePayApportionList.add(FeePayApportion.feePayApportionWith()
            .apportionAmount(new BigDecimal("99.99"))
            .apportionType("AUTO")
            .feeId(1)
            .paymentId(1)
            .feeAmount(new BigDecimal("99.99"))
            .paymentId(1)
            .build());
        return feePayApportionList;
    }

    private PaymentFee getPaymentFee(){
        return PaymentFee.feeWith()
            .calculatedAmount(new BigDecimal("99.99"))
            .paymentLink(PaymentFeeLink.paymentFeeLinkWith()
                .paymentReference("payment-ref")
                .dateCreated(new Date())
                .dateUpdated(new Date())
                .build()
            )
            .version("1").code("FEE0001").volume(1).build();
    }
}
