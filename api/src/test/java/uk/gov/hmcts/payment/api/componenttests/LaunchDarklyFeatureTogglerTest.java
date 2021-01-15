package uk.gov.hmcts.payment.api.componenttests;

import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@ActiveProfiles({"local", "componenttest"})
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = MOCK)
public class LaunchDarklyFeatureTogglerTest {

    @Mock
    private LDClientInterface ldClient;

    @Autowired
    private LaunchDarklyFeatureToggler launchDarklyFeatureToggler;

    @Test
    public void should_return_default_value_when_key_does_not_exist() {
        String sampleKey = "sample-key";
        assertTrue(launchDarklyFeatureToggler.getBooleanValue(sampleKey, true));
    }

    @Test
    public void should_return_value_when_key_exists() {
        String sampleKey = "sample-key";
        assertFalse(launchDarklyFeatureToggler.getBooleanValue(sampleKey, false));
    }

}

