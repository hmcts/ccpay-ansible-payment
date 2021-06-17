package uk.gov.hmcts.payment.api.componenttests.serviceanduser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.client.LinkDiscoverer;
import org.springframework.http.MediaType;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;


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
        

    }

    @Bean
    public PluginRegistry<LinkDiscoverer, MediaType> discoverers(
        OrderAwarePluginRegistry<LinkDiscoverer, MediaType> relProviderPluginRegistry) {
        return relProviderPluginRegistry;
    }

}

