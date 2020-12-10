package uk.gov.hmcts.payment.api.componenttests.serviceanduser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.configuration.security.AuthCheckerServiceAndAnonymousUserFilter;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.ServiceResolverBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.UserResolverBackdoor;
import uk.gov.hmcts.reform.auth.checker.core.RequestAuthorizer;
import uk.gov.hmcts.reform.auth.checker.core.SubjectResolver;
import uk.gov.hmcts.reform.auth.checker.core.service.Service;
import uk.gov.hmcts.reform.auth.checker.core.user.User;
import uk.gov.hmcts.reform.auth.checker.spring.serviceanduser.ServiceAndUserDetails;
import org.springframework.hateoas.client.LinkDiscoverer;
import org.springframework.http.MediaType;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

@SpringBootApplication
public class ServiceAndUserTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceAndUserTestApplication.class, args);
    }

    @RestController
    public static class TestController {
        @RequestMapping("/test")
        public String publicEndpoint() {
            ServiceAndUserDetails details = (ServiceAndUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return details.getUsername() + "@" + details.getServicename();
        }
    }

    @Configuration
    @EnableWebSecurity
    public class AuthCheckerConfiguration {
        @Bean
        public Function<HttpServletRequest, Optional<String>> userIdExtractor() {
            return (request) -> Optional.of("1");
        }

        @Bean
        public Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor() {
            return (any) -> Collections.singletonList("citizen");
        }

        @Bean
        public Function<HttpServletRequest, Collection<String>> authorizedServicesExtractor() {
            return (any) -> Collections.singletonList("divorce");
        }

        @Bean
        public SubjectResolver<Service> serviceResolver() {
            return new ServiceResolverBackdoor();
        }

        @Bean
        public SubjectResolver<User> userResolver() {
            return new UserResolverBackdoor();
        }

        @Bean
        public AuthCheckerServiceAndAnonymousUserFilter authCheckerServiceAndUserFilter(RequestAuthorizer<User> userRequestAuthorizer,
                                                                               RequestAuthorizer<Service> serviceRequestAuthorizer,
                                                                               AuthenticationManager authenticationManager) {
            AuthCheckerServiceAndAnonymousUserFilter filter = new AuthCheckerServiceAndAnonymousUserFilter(serviceRequestAuthorizer, userRequestAuthorizer);
            filter.setAuthenticationManager(authenticationManager);
            return filter;
        }
    }

    @Configuration
    @EnableWebSecurity
    public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

        @Autowired
        private AuthCheckerServiceAndAnonymousUserFilter filter;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .addFilter(filter)
                .authorizeRequests().anyRequest().authenticated();
        }
    }

    @Bean
    public PluginRegistry<LinkDiscoverer, MediaType> discoverers(
        OrderAwarePluginRegistry<LinkDiscoverer, MediaType> relProviderPluginRegistry) {
        return relProviderPluginRegistry;
    }
}

