package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
public class GovPayComponentMockTest {

    private static final String USER_ID = UserResolverBackdoor.AUTHENTICATED_USER_ID;

    private final static String PAYMENT_REFERENCE_REFEX = "^[RC-]{3}(\\w{4}-){3}(\\w{4})";

    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    protected ObjectMapper objectMapper;

    protected RestActions restActions;


    @SneakyThrows
    String contentsOf(String fileName) {
        String content = new String(Files.readAllBytes(Paths.get(ResourceUtils.getURL("classpath:" + fileName).toURI())));
        return resolvePlaceholders(content);
    }

    String resolvePlaceholders(String content) {
        return configurableListableBeanFactory.resolveEmbeddedValue(content);
    }


    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;


    @Before
    public void setup() {

        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);


        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");

        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    public void verifyGovPayPostResponseTest() throws Exception {
        DefaultResponseCreator govPayRespnse = withStatus(HttpStatus.CREATED)
            .body(contentsOf("gov-pay-responses/create-payment-response.json").getBytes())
            .contentType(MediaType.APPLICATION_JSON_UTF8);

        mockServer.expect(requestTo("/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(govPayRespnse);

        HttpEntity<?> httpEntity = new HttpEntity<GovPayPayment>(GovPayPayment.govPaymentWith().build());
        ResponseEntity<GovPayPayment> res = restTemplate.postForEntity("/v1/payments", httpEntity, GovPayPayment.class);

        GovPayPayment govPayPayment = res.getBody();

        assertNotNull(govPayPayment);
        assertTrue(govPayPayment.getReference().matches(PAYMENT_REFERENCE_REFEX));
        assertEquals(govPayPayment.getState().getStatus(), "Created");
        assertEquals(govPayPayment.getPaymentId(), "ak8gtvb438drmp59cs7ijppr3i");
        assertEquals(govPayPayment.getDescription(), "New passport application");
        assertEquals(govPayPayment.getAmount(), new Integer(10189));
    }

    @Test
    public void verifyGovPayGetResponseTest() throws Exception {
        String reference = "RC-1519-9028-1909-3475";
        DefaultResponseCreator govPayResponse = withStatus(HttpStatus.OK)
            .body(contentsOf("gov-pay-responses/get-payment-response.json").getBytes())
            .contentType(MediaType.APPLICATION_JSON_UTF8);

        mockServer.expect(requestTo("/v1/payments"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(govPayResponse);

        HttpEntity<?> httpEntity = new HttpEntity<String>(reference);
        ResponseEntity<GovPayPayment> response = restTemplate.getForEntity("/v1/payments", GovPayPayment.class, reference);

        GovPayPayment govPayPayment = response.getBody();
        assertNotNull(govPayPayment);
        assertEquals(govPayPayment.getAmount(), new Integer(1199));
        assertEquals(govPayPayment.getReference(), reference);
        assertEquals(govPayPayment.getState().getStatus(), "Success");
    }
}
