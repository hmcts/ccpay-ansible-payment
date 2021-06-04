package uk.gov.hmcts.payment.api.configuration;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource("classpath:application-test.properties")
public class LaunchDarklyFeatureTogglerTest {

    @Mock
    LDClientInterface ldClient;

    @InjectMocks
    LaunchDarklyFeatureToggler launchDarklyFeatureToggler;

    @Test
    public void testGetBooleanValue(){
        LDUser user = new LDUser("testUser");
        when(ldClient.boolVariation("key",user,true)).thenReturn(false);
        boolean response = launchDarklyFeatureToggler.getBooleanValue("key",true);
        assertEquals(false,response);
    }

}
