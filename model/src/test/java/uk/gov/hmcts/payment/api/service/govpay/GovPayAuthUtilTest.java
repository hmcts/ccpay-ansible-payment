package uk.gov.hmcts.payment.api.service.govpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayKeyRepository;

import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

//@RunWith(SpringRunner.class)
//@ActiveProfiles({"componenttest"})
//@PropertySource("classpath:application-componenttest.properties")
public class GovPayAuthUtilTest {
    private final String divorceKey = "divorce-gov-pay-key";
    private final String cmcKey = "cmc-gov-pay-key";
    private final String ccdKey = "ccd-gov-pay-key";

    @Mock
    private GovPayKeyRepository govPayKeyRepository;

    @InjectMocks
    private GovPayAuthUtil govPayAuthUtil;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(govPayKeyRepository.getKey("divorce")).thenReturn(divorceKey);
        when(govPayKeyRepository.getKey("cmc")).thenReturn(cmcKey);
        when(govPayKeyRepository.getKey("ccd")).thenReturn(ccdKey);

        ReflectionTestUtils.setField(govPayAuthUtil, "operationalServices", Arrays.asList("ccd"));
    }

    @Test
    public void returnPaymentServiceKeyWhenCallingServiceNameEqualsPaymentService() {
        String caller = "divorce";
        String paymentService = "divorce";

        assertEquals(govPayAuthUtil.getServiceToken(caller, paymentService), divorceKey);
    }

    @Test
    public void returnPaymentServiceKeyWhenCallingServiceIsOneOfOperationalServices(){
        String caller = "ccd";
        String paymentService = "divorce";

        assertEquals(govPayAuthUtil.getServiceToken(caller, paymentService), divorceKey);
    }

    @Test
    public void returnCallingServiceKeyWhenItIsNotPartOfOperationalServicesAndNotEqualToPaymentService() {
        String caller = "cmc";
        String paymentService = "divorce";

        assertEquals(govPayAuthUtil.getServiceToken(caller, paymentService), cmcKey);
    }

}
