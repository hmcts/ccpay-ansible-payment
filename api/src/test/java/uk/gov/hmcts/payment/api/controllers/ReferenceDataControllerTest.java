package uk.gov.hmcts.payment.api.controllers;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.referencedata.controllers.ReferenceDataController;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class ReferenceDataControllerTest {

    @InjectMocks
    private ReferenceDataController referenceDataController;

    @Mock
    private SiteService<Site, String> siteServiceMock;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Test
    public void getAllSitesInformation() {
        List<SiteDTO> expectedResponse = Collections.singletonList(SiteDTO.siteDTOwith()
            .sopReference("sop")
            .siteID("siteID")
            .service("service")
            .name("name")
            .build());
        List<Site> serviceReturn = Collections.singletonList(Site.siteWith()
            .sopReference("sop")
            .siteId("siteID")
            .name("name")
            .service("service")
            .id(1)
            .build());
        when(siteServiceMock.getAllSites()).thenReturn(serviceReturn);
        ResponseEntity<List<SiteDTO>> responseBody = referenceDataController.getSites();
        List<SiteDTO> actualResponse = responseBody.getBody();

        assertEquals(expectedResponse, actualResponse);
        assertEquals(HttpStatus.OK, responseBody.getStatusCode());
    }
}
