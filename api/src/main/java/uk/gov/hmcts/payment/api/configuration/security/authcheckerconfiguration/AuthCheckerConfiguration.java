package uk.gov.hmcts.payment.api.configuration.security.authcheckerconfiguration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.payment.api.configuration.security.ServicePaymentFilter;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.authorisation.validators.ServiceAuthTokenValidator;
import uk.gov.hmcts.reform.idam.client.IdamApi;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Feign client to fetch s2s apis
 */
@Configuration
@Lazy
@EnableFeignClients(basePackageClasses = {IdamApi.class, ServiceAuthorisationApi.class})
public class AuthCheckerConfiguration {

    /**
     * Auth token generator for s2s api
     * @param secret
     * @param microService
     * @param serviceAuthorisationApi
     * @return
     */

    @Bean
    public AuthTokenGenerator authTokenGenerator(
        @Value("${idam.s2s-auth.totp_secret}") final String secret,
        @Value("${idam.s2s-auth.microservice}") final String microService,
        final ServiceAuthorisationApi serviceAuthorisationApi
    ) {
        return AuthTokenGeneratorFactory.createDefaultGenerator(secret, microService, serviceAuthorisationApi);
    }

    @Bean
    public AuthTokenValidator authTokenValidator(ServiceAuthorisationApi serviceAuthorisationApi) {
        return new ServiceAuthTokenValidator(serviceAuthorisationApi);

    }

    /**
     * Extracts and validates user id from request uri if required
     * @return
     */

    @Bean
    public Function<HttpServletRequest, Optional<String>> userIdExtractor() {
        Pattern pattern = Pattern.compile("^/users/([^/]+)/.+$");

        return (request) -> {
            Matcher matcher = pattern.matcher(request.getRequestURI());
            boolean matched = matcher.find();
            if(matched){
                return Optional.of(matcher.group(1));
            }else {
                return Optional.empty();
            }
        };
    }

    /**
     * Bean to specify authorised roles for Fees&Pay
     * @return
     */
    @Bean
    public Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor() {
        return (any) -> Stream.of("payments", "citizen","pui-finance-manager","caseworker-cmc-solicitor","caseworker-publiclaw-solicitor","caseworker-probate-solicitor","caseworker-financialremedy-solicitor","caseworker-divorce-solicitor")
            .collect(Collectors.toList());
    }

    @Bean
    @Profile({"!componenttest"})
    public ServicePaymentFilter servicePaymentFilter(ServiceAuthorisationApi serviceAuthorisationApi) {
        return new ServicePaymentFilter(authTokenValidator(serviceAuthorisationApi));
    }
}
