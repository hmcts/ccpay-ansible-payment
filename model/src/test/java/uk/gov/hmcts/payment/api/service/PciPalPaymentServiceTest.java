package uk.gov.hmcts.payment.api.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.hmcts.payment.api.dto.PciPalPaymentRequest;

import java.net.URISyntaxException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;


public class PciPalPaymentServiceTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private PciPalPaymentService pciPalPaymentService;

    private String url = "";
    private String callbackUrl = "www.callback.url.com";

    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() throws Exception {
        this.url = "http://localhost:" + wireMockRule.port();

        pciPalPaymentService = new PciPalPaymentService(
            url,
            callbackUrl,httpClient,objectMapper
        );
    }

    @Test
    public void getPciPalLink() throws URISyntaxException {
        StringBuilder sb = new StringBuilder();
        sb.append("ppAccountID&orderAmount=20000&orderReference=orderReference&callbackURL=www.callback.url.com&customData2");
        stubFor(
            post(urlEqualTo("/"))
                .withRequestBody(
                    new EqualToPattern(sb.toString())
                )
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBodyFile("pci-pal-response.txt")
                )
        );

        PciPalPaymentRequest request = PciPalPaymentRequest.pciPalPaymentRequestWith()
            .orderAmount("200")
            .orderCurrency("GBP")
            .orderReference("orderReference")
            .build();

        String response = pciPalPaymentService.getPciPalLink(request, "Specified Money Claims");

        URIBuilder uriBuilder = new URIBuilder(url + "?" + sb.toString());
        HttpGet getRequest = new HttpGet(uriBuilder.build());
        assertThat(response).isEqualTo(getRequest.getURI().toString());
    }
}
