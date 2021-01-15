package uk.gov.hmcts.payment.api.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;

@Configuration
public class ServiceAuthTokenConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceAuthTokenConfiguration.class);
    @Bean
    public AuthTokenGenerator authTokenGenerator(
        @Value("${idam.s2s-auth.totp_secret}") final String secret,
        @Value("${idam.s2s-auth.microservice}") final String microservice,
        ServiceAuthorisationApi serviceAuthorisationApi) {
        return AuthTokenGeneratorFactory
            .createDefaultGenerator(
                secret,
                microservice,
                serviceAuthorisationApi);
    }
}
