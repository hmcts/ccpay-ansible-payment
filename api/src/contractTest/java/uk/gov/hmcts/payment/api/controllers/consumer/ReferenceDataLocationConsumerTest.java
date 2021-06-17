package uk.gov.hmcts.payment.api.controllers.consumer;

import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactFolder;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.payment.api.configuration.RestTemplateConfiguration;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.service.ReferenceDataServiceImpl;
import uk.gov.hmcts.payment.referencedata.service.SiteServiceImpl;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.util.Collections;

import static io.pactfoundation.consumer.dsl.LambdaDsl.newJsonArray;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.given;


@ExtendWith(PactConsumerTestExt.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@PactTestFor(providerName = "referenceData_location", port = "8891")
@PactFolder("pacts")
@SpringBootTest({
    "rd.location.url : http://localhost:8891"
})
@ContextConfiguration(classes = {ReferenceDataServiceImpl.class})
@Import(RestTemplateConfiguration.class)
public class ReferenceDataLocationConsumerTest {

    public static final String SOME_AUTHORIZATION_TOKEN = "Bearer UserAuthToken";
    public static final String SOME_SERVICE_AUTHORIZATION_TOKEN = "ServiceToken";
    public static final String SERVICE_AUTHORIZATION = "ServiceAuthorization";

    @Autowired
    protected ReferenceDataServiceImpl referenceDataService;

    @MockBean
    SiteServiceImpl siteService;

    @MockBean
    AuthTokenGenerator authTokenGenerator;

    @Pact(provider = "referenceData_location", consumer = "payment_App")
    RequestResponsePact retrieveOrganisationDetails(PactDslWithProvider builder) {
        // @formatter:off
        return builder
            .given("Organisational Service details exist")
            .uponReceiving("A request for Organisational Service Details")
            .path("/refdata/location/orgServices")
            .method("GET")
            .query("ccdCaseType=Divorce")
            .headers(HttpHeaders.AUTHORIZATION, SOME_AUTHORIZATION_TOKEN, SERVICE_AUTHORIZATION,
                SOME_SERVICE_AUTHORIZATION_TOKEN)
            .matchHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .willRespondWith()
            .matchHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .status(200)
            .body(buildOrganisationalResponsePactDsl())
            .toPact();
    }


    private DslPart buildOrganisationalResponsePactDsl() {
        return newJsonArray((rootArray) -> {
            rootArray.object(ob -> ob
                .stringType("jurisdiction",
                    "jurisdiction")
                .numberType("service_id", 437345065)
                .stringType("org_unit", "orgUnit")
                .stringType("business_area", "businessArea")
                .stringType("service_description", "DIVORCE")
                .stringType("service_code", "AA07")
                .stringType("service_short_description", "DIVORCE")
                .stringType("ccd_service_name", "ccdServiceName")
                .array("ccd_case_types", pa ->
                    pa.stringType("Divorce"))
            );
        }).build();
    }

    @Test
    @PactTestFor(pactMethod = "retrieveOrganisationDetails")
    public void verifyRetrieveOrganisationDetails() throws JSONException {

        MultiValueMap<String, String> header = new LinkedMultiValueMap<>();
        header.put("Authorization", Collections.singletonList(SOME_AUTHORIZATION_TOKEN));
        header.put("ServiceAuthorization", Collections.singletonList(SOME_SERVICE_AUTHORIZATION_TOKEN));
        header.put("content-type", Collections.singletonList(MediaType.APPLICATION_JSON_VALUE));
        given(authTokenGenerator.generate()).willReturn(SOME_SERVICE_AUTHORIZATION_TOKEN);
        OrganisationalServiceDto organisationalDetail = referenceDataService.getOrganisationalDetail("Divorce", header);
        assertOrganisationalDetails(organisationalDetail);

    }

    private void assertOrganisationalDetails(OrganisationalServiceDto organisationalDetail) {
        assertThat(organisationalDetail.getJurisdiction(), equalTo("jurisdiction"));
        assertThat(organisationalDetail.getServiceId(), equalTo("437345065"));
        assertThat(organisationalDetail.getOrgUnit(), equalTo("orgUnit"));
        assertThat(organisationalDetail.getBusinessArea(), equalTo("businessArea"));
        assertThat(organisationalDetail.getServiceCode(), equalTo("AA07"));
        assertThat(organisationalDetail.getServiceShortDescription(), equalTo("DIVORCE"));
        assertThat(organisationalDetail.getCcdServiceName(), equalTo("ccdServiceName"));
        assertThat(organisationalDetail.getCcdCaseTypes().get(0), equalTo("Divorce"));
        assertThat(organisationalDetail.getServiceDescription(), equalTo("DIVORCE"));
    }
}
