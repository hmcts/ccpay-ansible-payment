package uk.gov.hmcts.payment.api.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
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
import uk.gov.hmcts.payment.api.contract.CasePaymentOrdersDto;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
public class CasePaymentOrdersControllerTest extends PaymentsDataUtil {
    private static final String USER_ID = UserResolverBackdoor.CASEWORKER_ID;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected ServiceResolverBackdoor serviceRequestAuthorizer;

    @Autowired
    protected UserResolverBackdoor userRequestAuthorizer;

    private RestActions restActions;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @Before
    public void setup() {

        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, serviceRequestAuthorizer, userRequestAuthorizer, objectMapper);

        restActions
            .withAuthorizedService("divorce")
            .withAuthorizedUser(USER_ID)
            .withUserId(USER_ID)
            .withReturnUrl("https://www.moneyclaims.service.gov.uk");
    }

    @Test
    @Transactional
    public void getCasePaymentOrdersWithValidInputData_shouldReturn200Test() throws Exception {

        stubFor(get(urlPathMatching("/case-payment-orders"))
                    .withQueryParam("case_ids", containing("1709243447569253"))
                    .withQueryParam("page", containing("1"))
                    .withQueryParam("size", containing("2"))
                    .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(contentsOf(
                                        "case-payment-orders-responses/get-case-payment-orders-response.json"))));

        MvcResult result = restActions
            .get("/case-payment-orders?case_ids=1709243447569253&page_number=1&page_size=2")
            .andExpect(status().isOk())
            .andReturn();

        CasePaymentOrdersDto casePaymentOrdersDto = objectMapper
            .readValue(result.getResponse().getContentAsString(), CasePaymentOrdersDto.class);

        assertNotNull(casePaymentOrdersDto);
        assertThat(casePaymentOrdersDto.getSize(), is(2));
        assertThat(casePaymentOrdersDto.getNumber(), is(1));
        assertThat(casePaymentOrdersDto.getTotalElements(), is(3L));
    }

    @Test
    @Transactional
    public void getCasePaymentOrdersBadRequestWhenCaseIdsMissing_shouldReturn400Test() throws Exception {

        restActions
            .get("/case-payment-orders?page_number=1&page_size=2")
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    public void getCasePaymentOrdersInternalServerError_shouldReturn500Test() throws Exception {

        stubFor(get(urlPathMatching("/case-payment-orders"))
                    .willReturn(aResponse()
                                    .withStatus(500)
                                    .withBody("Error - InternalServerError")));

        restActions
            .get("/case-payment-orders?case_ids=1709243447569253")
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(containsString("Error - InternalServerError")));
    }

    @Test
    @Transactional
    public void getCasePaymentOrdersBadRequestException_shouldReturn400Test() throws Exception {

        stubFor(get(urlPathMatching("/case-payment-orders"))
                    .withQueryParam("case_ids", containing("invalidCaseId"))
                    .willReturn(aResponse()
                                    .withStatus(400)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody("{\"exception\":\"CasePaymentOrdersFilterException\","
                                                  + "\"timestamp\":\"2021-04-12T21:04:26.471639\","
                                                  + "\"status\":400,"
                                                  + "\"error\":\"Bad Request\","
                                                  + "\"message\":\"CPOs cannot be filtered by both id and case id.\","
                                                  + "\"path\":\"/case-payment-orders\","
                                                  + "\"details\":null}")));

        restActions
            .get("/case-payment-orders?case_ids=invalidCaseId")
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("CPOs cannot be filtered by both id and case id.")));
    }

    @Test
    @Transactional
    public void getCasePaymentOrdersForbidden_shouldReturn401Test() throws Exception {

        stubFor(get(urlPathMatching("/case-payment-orders"))
                    .withQueryParam("case_ids", containing("1709243447569253"))
                    .willReturn(aResponse()
                                    .withStatus(401)));

        restActions
            .get("/case-payment-orders?case_ids=1709243447569253")
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    public void getCasePaymentOrdersForbidden_shouldReturn403Test() throws Exception {

        stubFor(get(urlPathMatching("/case-payment-orders"))
                    .withQueryParam("case_ids", containing("1709243447569253"))
                    .willReturn(aResponse()
                                    .withStatus(403)
                                    .withBody("Unauthorised S2S service.")));

        restActions
            .get("/case-payment-orders?case_ids=1709243447569253")
            .andExpect(status().isForbidden())
            .andExpect(content().string(containsString("Unauthorised S2S service.")));
    }
}
