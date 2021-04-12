package uk.gov.hmcts.payment.api.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class ReferenceDataServiceTest {

    @Mock
    SiteService<Site, String> siteService;

    @InjectMocks
    ReferenceDataServiceImpl referenceDataService;

    @Test
    public void testGetSiteIDs(){
        Site site1 = Site.siteWith()
                        .name("site1")
                        .build();
        List<Site> siteList = new ArrayList<Site>();
        siteList.add(site1);
        when(siteService.getAllSites()).thenReturn(siteList);
        referenceDataService.getSiteIDs();
        verify(siteService).getAllSites();
    }

}
