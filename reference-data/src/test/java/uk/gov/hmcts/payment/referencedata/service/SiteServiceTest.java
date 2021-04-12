package uk.gov.hmcts.payment.referencedata.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.referencedata.exception.ReferenceDataNotFoundException;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.model.SiteRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @InjectMocks
    SiteServiceImpl siteService = new SiteServiceImpl();

    @Test
    public void testRetrieve(){
        Site site1 = Site.siteWith()
            .siteId("1")
            .name("site-1")
            .sopReference("sop-ref")
            .service("service")
            .build();
       when(siteRepository.findBySiteId(any(String.class))).thenReturn(java.util.Optional.ofNullable(site1));
       Site result = siteService.retrieve("1");
       assertEquals("site-1",result.getName());
    }

    @Test(expected = ReferenceDataNotFoundException.class)
    public void testRetrieveThrowException(){
        Site site1 = Site.siteWith()
            .siteId("1")
            .name("site-1")
            .sopReference("sop-ref")
            .service("service")
            .build();
        when(siteRepository.findBySiteId(any(String.class))).thenThrow(new ReferenceDataNotFoundException("exception message"));
        siteService.retrieve("1");
    }

    @Test
    public void testGetAllSites(){
        Site site1 = Site.siteWith()
            .siteId("1")
            .name("site-1")
            .sopReference("sop-ref")
            .service("service")
            .build();
        List<Site> siteList = new ArrayList<>();
        siteList.add(site1);
        when(siteRepository.findAll()).thenReturn(siteList);
        List<Site> result = siteService.getAllSites();
        assertEquals("site-1",result.get(0).getName());
    }

}
