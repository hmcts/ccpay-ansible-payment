package uk.gov.hmcts.payment.casepaymentorders.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClientConfig {

    @Bean(value = "restTemplateCpoClient")
    public RestTemplate restTemplateCpoClient() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }
}
