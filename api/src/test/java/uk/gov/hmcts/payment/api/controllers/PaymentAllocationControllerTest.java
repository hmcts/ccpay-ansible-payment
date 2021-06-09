package uk.gov.hmcts.payment.api.componenttests;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentAllocationStatus;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

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

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class PaymentAllocationControllerTest extends PaymentsDataUtil {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    private UserResolverBackdoor userRequestAuthorizer;


    private static final String USER_ID = UserResolverBackdoor.CITIZEN_ID;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SiteService<Site, String> siteServiceMock;

    @MockBean
    private Payment2Repository paymentRepository;


    @Autowired
    private PaymentDbBackdoor db;

    private final static String PAYMENT_REFERENCE_REGEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

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
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");

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
