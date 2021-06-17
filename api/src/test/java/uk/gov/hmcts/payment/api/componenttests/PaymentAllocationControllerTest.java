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
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentAllocationStatus;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
public class PaymentAllocationControllerTest extends PaymentsDataUtil {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SiteService<Site, String> siteServiceMock;

    @MockBean
    private Payment2Repository paymentRepository;

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
    private PaymentDbBackdoor db;

    private final static String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    protected CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

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
    public void addInvalidPaymentAllocationTest() throws Exception {
        Payment payment =populateCardPaymentToDbForPaymentAllocation("1");
        PaymentAllocationDto paymentAllocationDto = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentGroupReference("2018-00000000001")
            .paymentReference("RC-1519-9028-2432-0001")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("NewReason").build())
            .build();

        restActions
            .post("/payment-allocations", paymentAllocationDto)
            .andExpect(status().isNotFound());

    }

    @Test
    @WithMockUser(authorities = "payments")
    public void addPaymentAllocationWithoutPGTest() throws Exception {
        PaymentAllocationDto paymentAllocationDto = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentReference("RC-23423423")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Allocated").build())
            .build();

        restActions
            .post("/payment-allocations", paymentAllocationDto)
            .andExpect(status().isUnprocessableEntity());

    }

    @Test
    @WithMockUser(authorities = "payments")
    public void addPaymentAllocationWithoutPaymentTest() throws Exception {
        PaymentAllocationDto paymentAllocationDto = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentGroupReference("2019-234234923")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Allocated").build())
            .build();

        restActions
            .post("/payment-allocations", paymentAllocationDto)
            .andExpect(status().isUnprocessableEntity());

    }

    @Test
    @WithMockUser(authorities = "payments")
    public void addPaymentAllocationForSolicitedPaymentTest() throws Exception {
        Payment payment =populateCardPaymentToDbForPaymentAllocation("1");
        PaymentAllocationDto request = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentGroupReference("2018-00000000001")
            .paymentReference("RC-1519-9028-2432-0001")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Allocated").build())
            .build();
        when(paymentRepository.findByReference(anyString())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        MvcResult result = restActions
            .post("/payment-allocations", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentAllocationDto paymentAllocationDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentAllocationDto.class);

        assertTrue(paymentAllocationDto.getPaymentReference().equals(request.getPaymentReference()));
        assertTrue(paymentAllocationDto.getPaymentGroupReference().equals(request.getPaymentGroupReference()));
        //assertThat(paymentAllocationDto.getDateCreated().getDate()).isEqualTo(new Date().getDate());

    }


    @Test
    @WithMockUser(authorities = "payments")
    @Transactional
    public void addPaymentAllocationForUnsolicitedPaymentTest() throws Exception {
        Payment payment =populateCardPaymentToDbForPaymentAllocation("1");
        PaymentAllocationDto request = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentGroupReference("2018-00000000001")
            .paymentReference("RC-1519-9028-2432-0001")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Transferred").build())
            .receivingOffice("Home office")
            .reason("receiver@receiver.com")
            .explanation("sender@sender.com")
            .userId("userId")
            .build();
        when(paymentRepository.findByReference(anyString())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

    MvcResult result = restActions
        .post("/payment-allocations", request)
        .andExpect(status().isCreated())
        .andReturn();

    PaymentAllocationDto paymentAllocationDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentAllocationDto.class);

    assertTrue(paymentAllocationDto.getPaymentReference().equals(request.getPaymentReference()));
    assertTrue(paymentAllocationDto.getPaymentGroupReference().equals(request.getPaymentGroupReference()));
    //assertThat(paymentAllocationDto.getDateCreated().getDate()).isEqualTo(new Date().getDate());
    assertThat(paymentAllocationDto.getReason()).isEqualTo(request.getReason());

    }

    @Test
    @WithMockUser(authorities = "payments")
    public void addPaymentAllocationForUnIdentifiedPaymentTest() throws Exception {
        Payment payment =populateCardPaymentToDbForPaymentAllocation("1");
        PaymentAllocationDto request = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentGroupReference("2018-00000000001")
            .paymentReference("RC-1519-9028-2432-0001")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Unidentified").build())
            .unidentifiedReason("payment is unidentified sent back to exela")
            .build();
        when(paymentRepository.findByReference(anyString())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);


        MvcResult result = restActions
            .post("/payment-allocations", request)
            .andExpect(status().isCreated())
            .andReturn();

        PaymentAllocationDto paymentAllocationDto = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentAllocationDto.class);

        assertTrue(paymentAllocationDto.getPaymentReference().equals(request.getPaymentReference()));
        assertTrue(paymentAllocationDto.getPaymentGroupReference().equals(request.getPaymentGroupReference()));
        //assertThat(paymentAllocationDto.getDateCreated().getDate()).isEqualTo(new Date().getDate());
        assertThat(paymentAllocationDto.getUnidentifiedReason()).isEqualTo(request.getUnidentifiedReason());

    }
    @Test
    @WithMockUser(authorities = "payments")
    public void paymentIsNotFound() throws Exception {
        Payment payment =populateCardPaymentToDbForPaymentAllocation("1");
        PaymentAllocationDto request = PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentGroupReference("2018-00000000001")
            .paymentReference("RC-1519-9028-2432-00011")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Unidentified").build())
            .unidentifiedReason("payment is unidentified sent back to exela")
            .build();
        when(paymentRepository.findByReference(anyString())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        MvcResult result = restActions
            .post("/payment-allocations", request)
            .andExpect(status().isNotFound())
            .andReturn();

    }

}
