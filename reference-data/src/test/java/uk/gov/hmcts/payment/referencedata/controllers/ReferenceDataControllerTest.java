package uk.gov.hmcts.payment.referencedata.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes=ReferenceDataController.class)
@WebMvcTest(controllers = ReferenceDataController.class)
@AutoConfigureMockMvc
public class ReferenceDataControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    SiteService<Site, String> siteService;

    @Test
    public void testReferenceDataSites() throws Exception {
        Site site1 = Site.siteWith()
                        .siteId("1")
                        .name("site-1")
                        .sopReference("sop-ref")
                        .service("service")
                        .build();
        List<Site> siteList = new ArrayList<>();
        siteList.add(site1);
        given(siteService.getAllSites()).willReturn(siteList);
        mvc.perform(get("/reference-data/sites")
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding("utf-8"))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    }
}
