package uk.gov.hmcts.payment.api.v1.componenttests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilter;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;
import uk.gov.hmcts.payment.api.service.UserAwareDelegatingCreditAccountPaymentService;
import uk.gov.hmcts.payment.api.service.UserAwareDelegatingPaymentService;
import uk.gov.hmcts.payment.api.v1.componenttests.backdoors.DbBackdoor;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.CustomResultMatcher;
import uk.gov.hmcts.payment.api.v1.componenttests.sugar.RestActions;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
@Transactional
@EnableFeignClients
@AutoConfigureMockMvc
public class TestUtil {

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(9190);

    @Autowired
    protected DbBackdoor db;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Autowired
    protected PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Autowired
    protected UserAwareDelegatingPaymentService cardPaymentService;

    @Autowired
    protected UserAwareDelegatingCreditAccountPaymentService creditAccountPaymentService;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ServiceAuthFilter serviceAuthFilter;

    @InjectMocks
    private ServiceAndUserAuthFilter serviceAndUserAuthFilter;

    @MockBean
    private SecurityUtils securityUtils;

    RestActions restActions;

    @Before
    public void setUp() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, objectMapper);
    }

    CustomResultMatcher body() {
        return new CustomResultMatcher(objectMapper);
    }

    @SneakyThrows
    String contentsOf(String fileName) {
        String content = new String(Files.readAllBytes(Paths.get(ResourceUtils.getURL("classpath:" + fileName).toURI())));
        return resolvePlaceholders(content);
    }

    String resolvePlaceholders(String content) {
        return configurableListableBeanFactory.resolveEmbeddedValue(content);
    }
}
