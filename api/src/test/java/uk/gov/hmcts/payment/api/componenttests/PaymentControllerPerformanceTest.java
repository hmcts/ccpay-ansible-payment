package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.joda.time.LocalDate;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
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
import org.springframework.test.annotation.Rollback;
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
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilterTest.getUserInfoBasedOnUID_Roles;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

@RunWith(SpringRunner.class)
@ActiveProfiles({"componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@EnableFeignClients
@AutoConfigureMockMvc
@Transactional
public class PaymentControllerPerformanceTest extends PaymentsDataUtil {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

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
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PaymentDbBackdoor db;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("dd-MM-yyyy");

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

    }

    private void createPayment(int n) {

        //Create a payment in remissionDbBackdoor
        Payment payment = Payment.paymentWith()
            .amount(new BigDecimal("11.99"))
            .caseReference("Reference" + n)
            .ccdCaseNumber("ccdCaseNumber" + n)
            .description("Description1")
            .serviceType("PROBATE")
            .currency("GBP")
            .siteId("AA01")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name("created").build())
            .externalReference("" + new Integer(n).hashCode())
            .reference("RC-1519-9028-1909-" + n)
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .status("Initiated")
                .externalStatus("created")
                .build()))
            .build();
        PaymentFee fee = PaymentFee.feeWith().calculatedAmount(new BigDecimal("11.99")).version("1").code("X0001").build();

        PaymentFeeLink paymentFeeLink = db.create(paymentFeeLinkWith().paymentReference("2018-15186162001" + n).payments(Arrays.asList(payment)).fees(Arrays.asList(fee)));
        payment.setPaymentLink(paymentFeeLink);

    }

    private final static int PAYMENTS_BACKLOG = 10;

    private final static int PAYMENTS_TODAY = 10;

    @Test
    public void testSuitesRequireAtLeastOneTest() {

    }

    //@Test
    @Transactional
    @Rollback(false)
    public void testPerformance() throws Exception {

        int i;

        for (i = 0; i < PAYMENTS_BACKLOG; i++) {
            createPayment(i);
        }

        for (int j = i; j < PAYMENTS_BACKLOG + PAYMENTS_TODAY; j++) {
            createPayment(j);
        }

        restActions
            .post("/api/ff4j/store/features/payment-search/enable","")
            .andExpect(status().isAccepted());

        String startDate = LocalDate.now().minus(Minutes.ONE).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        long timestamp = System.currentTimeMillis();

        MvcResult result = restActions
            .get("/payments?start_date=" + startDate + "&end_date=" + endDate)
            .andExpect(status().isOk())
            .andReturn();

        long durationOfSearch = System.currentTimeMillis() - timestamp;

        System.out.println("Search took " + durationOfSearch + " milliseconds");

        PaymentsResponse paymentsResponse = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentsResponse.class);

        assertThat(paymentsResponse.getPayments().size()).isGreaterThan(PAYMENTS_TODAY - 1);

        assertThat(durationOfSearch).isLessThan(10000);

    }


}
