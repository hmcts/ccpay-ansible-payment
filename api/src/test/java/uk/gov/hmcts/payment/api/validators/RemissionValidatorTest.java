package uk.gov.hmcts.payment.api.validators;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.gov.hmcts.payment.api.contract.exception.ValidationErrorDTO;
import uk.gov.hmcts.payment.api.dto.RemissionRequest;
import uk.gov.hmcts.payment.api.exception.ValidationErrorException;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RemissionValidator.class)
public class RemissionValidatorTest {

    @Mock
    ReferenceDataService<SiteDTO> referenceDataService;

    @InjectMocks
    RemissionValidator remissionValidator = new RemissionValidator();

    List<SiteDTO> sites;

    @Before
    public void initiate(){
        sites = new ArrayList<SiteDTO>();
        SiteDTO site1 = SiteDTO.siteDTOwith()
                            .siteID("site-1").build();
        sites.add(site1);
    }


    @Test
    public void testValidate() throws Exception {
        ValidationErrorDTO dto = mock(ValidationErrorDTO.class);
        when(referenceDataService.getSiteIDs()).thenReturn(sites);
        PowerMockito.whenNew(ValidationErrorDTO.class).withNoArguments().thenReturn(dto);

        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
                                                .siteId("site-2").build();
        remissionValidator.validate(remissionRequest);
        verify(dto).addFieldError("site_id","Invalid siteID: "+remissionRequest.getSiteId());
    }

    @Test(expected = ValidationErrorException.class)
    public void testValidateThrowsException() throws Exception {
        ValidationErrorDTO dto = mock(ValidationErrorDTO.class);
        when(referenceDataService.getSiteIDs()).thenReturn(sites);
        PowerMockito.whenNew(ValidationErrorDTO.class).withNoArguments().thenReturn(dto);
        when(dto.hasErrors()).thenReturn(true);
        RemissionRequest remissionRequest = RemissionRequest.createRemissionRequestWith()
            .siteId("site-2").build();
        remissionValidator.validate(remissionRequest);
    }


}
