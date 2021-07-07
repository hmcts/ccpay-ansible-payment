package uk.gov.hmcts.payment.api.configuration;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfiguration {

    @Value("${iac.connect.timeout}")
    private String iacConnectTimeout;

    @Value("${iac.read.timeout}")
    private String iacReadTimeout;

    @Bean(name = {"paymentsHttpClient", "serviceTokenParserHttpClient", "userTokenParserHttpClient"})
    public CloseableHttpClient paymentsHttpClient() {
        return HttpClients.custom()
            .useSystemProperties()
            .build();
    }

    @Bean (value = "restTemplatePaymentGroup")
    public RestTemplate restTemplatePaymentGroup() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    @Bean (value = "restTemplateIacSupplementaryInfo")
    public RestTemplate restTemplateIacSupplementaryInfo() {
        var factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Integer.parseInt(iacConnectTimeout));
        factory.setReadTimeout((Integer.parseInt(iacReadTimeout)));
        return new RestTemplate(factory);
    }
}
