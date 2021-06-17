package uk.gov.hmcts.payment.api.configuration.security;

import org.apache.commons.lang3.StringUtils;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.payment.api.configuration.IdamRepository;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.authcheckerconfiguration.AuthCheckerConfiguration;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.validators.ServiceAuthTokenValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@ActiveProfiles({"local", "test"})
public class SpringSecurityTestConfiguration {

    @Bean
    public ServiceAuthFilter serviceAuthFilter() {
        ServiceAuthorisationApi serviceAuthorisationApi = new ServiceAuthorisationApi() {
            @Override
            public String serviceToken(Map<String, String> signIn) {
                return null;
            }

            @Override
            public void authorise(String authHeader, String[] roles) {

            }

            @Override
            public String getServiceName(String authHeader) {
                if (StringUtils.containsIgnoreCase(authHeader, "cmc")) {
                    return "cmc";
                }
                else if (StringUtils.containsIgnoreCase(authHeader, "divorce")) {
                    return "divorce";
                }
                    else {
                    return "invalid-service";
                }
            }
        };

        //Test Authorised services
        List<String> authorisedServices = new ArrayList<>();
        authorisedServices.add("cmc");
        authorisedServices.add("divorce");
        ServiceAuthTokenValidator serviceAuthTokenValidator = new ServiceAuthTokenValidator(serviceAuthorisationApi);
        return new ServiceAuthFilter(serviceAuthTokenValidator, authorisedServices);
    }

    @Bean
    public IdamRepository idamRepository() {
        IdamRepository idamRepository = Mockito.mock(IdamRepository.class);
        return idamRepository;
    }

    @Bean
    SecurityUtils securityUtils() {
        AuthTokenGenerator authTokenGenerator = new AuthTokenGenerator() {
            @Override
            public String generate() {
                return "testToken";
            }
        };

        SecurityUtils securityUtils = new SecurityUtils(authTokenGenerator, idamRepository());
        return securityUtils;
    }

    @Bean
    public ServiceAndUserAuthFilter serviceAndUserAuthFilter() {
        AuthCheckerConfiguration authCheckerConfiguration = new AuthCheckerConfiguration();

        return new ServiceAndUserAuthFilter(
            authCheckerConfiguration.userIdExtractor(),
            authCheckerConfiguration.authorizedRolesExtractor(),
            securityUtils()
        );
    }

    @Bean
    public SiteService siteServiceMock() {
        return Mockito.mock(SiteService.class);
    }

    @Bean
    @Profile({"local","componenttest"})
    public ServicePaymentFilter servicePaymentFilter() {
        ServiceAuthorisationApi serviceAuthorisationApi = new ServiceAuthorisationApi() {
            @Override
            public String serviceToken(Map<String, String> signIn) {
                return null;
            }
            @Override
            public void authorise(String authHeader, String[] roles) {
            }
            @Override
            public String getServiceName(String authHeader) {
                if (StringUtils.containsIgnoreCase(authHeader, "cmc")) {
                    return "cmc";
                }
                else if (StringUtils.containsIgnoreCase(authHeader, "divorce")) {
                    return "divorce";
                }
                else {
                    return "invalid-service";
                }
            }
        };
        //Test Authorised services
        List<String> authorisedServices = new ArrayList<>();
        authorisedServices.add("cmc");
        authorisedServices.add("divorce");
        ServiceAuthTokenValidator serviceAuthTokenValidator = new ServiceAuthTokenValidator(serviceAuthorisationApi);
        return new ServicePaymentFilter(serviceAuthTokenValidator);
    }
}
