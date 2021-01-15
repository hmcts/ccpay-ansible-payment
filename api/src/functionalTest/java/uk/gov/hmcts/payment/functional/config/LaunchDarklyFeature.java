package uk.gov.hmcts.payment.functional.config;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LaunchDarklyFeature implements FeatureToggler {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchDarklyFeature.class);

    @Value("${launch.darkly.user.name}")
    private String userName;

    private LDClientInterface ldClient;

    public LaunchDarklyFeature(LDClientInterface ldClient) {
        this.ldClient = ldClient;
    }

    public boolean getBooleanValue(String key, Boolean defaultValue) {

        LOG.info("userName in LaunchDarklyFeatureToggler: {}", userName);
        LDUser user = new LDUser(userName);

        return ldClient.boolVariation(
            key,
            user,
            defaultValue
        );
    }

}
