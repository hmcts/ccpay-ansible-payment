package uk.gov.hmcts.payment.api.unit;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import lombok.SneakyThrows;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;
import uk.gov.hmcts.payment.api.configuration.SecurityUtils;
import uk.gov.hmcts.payment.api.configuration.security.ServiceAndUserAuthFilter;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.payment.referencedata.service.SiteServiceImpl;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;


@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = MOCK)
public class FeeCacheTest {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(9190);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Autowired
    private FeesService feesService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private SiteServiceImpl siteService;

    @Autowired
    private ServiceAuthFilter serviceAuthFilter;

    @InjectMocks
    private ServiceAndUserAuthFilter serviceAndUserAuthFilter;

    @MockBean
    private SecurityUtils securityUtils;


    @Before
    public void setUp() {
        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

        this.cacheManager.getCache("feesDtoMap").clear();
    }

    /**
     *
     * Test cached fees when fees-register returns
     * valid fees
     *
     * */
    @Test
    @WithMockUser(authorities = "payments")
    public void testCacheWithValidFees() throws  Exception {
        // Wire-mock fees-register response
        stubFor(get(urlPathMatching("/fees-register/fees"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(contentsOf("fees-register-responses/allfees.json"))));

        // Invoke fees-register service
        feesService.getFeesDtoMap();

        // Validate cached fees
        Cache cache = this.cacheManager.getCache("feesDtoMap");
        Map<String, Fee2Dto> feesDtoMap = (HashMap<String, Fee2Dto>)cache.get("getFeesDtoMap").get();

        assertThat(feesDtoMap).isNotNull();
        assertThat(feesDtoMap.size()).isEqualTo(337);
        Optional<Fee2Dto> optionalFeeDto = feesDtoMap.values().stream().filter(m -> m.getCode().equals("FEE0339")).findAny();
        if (optionalFeeDto.isPresent()) {
            Fee2Dto feeDto = optionalFeeDto.get();
            assertThat(feeDto.getCode()).isEqualTo("FEE0339");
            assertThat(feeDto.getFeeType()).isEqualTo("fixed");
            assertThat(feeDto.getChannelTypeDto().getName()).isEqualTo("default");
            assertThat(feeDto.getEventTypeDto().getName()).isEqualTo("miscellaneous");
            assertThat(feeDto.getJurisdiction1Dto().getName()).isEqualTo("family");
            assertThat(feeDto.getJurisdiction2Dto().getName()).isEqualTo("family court");
            assertThat(feeDto.getCurrentVersion().getNaturalAccountCode()).isEqualTo("4481102174");
            assertThat(feeDto.getCurrentVersion().getMemoLine()).isEqualTo("RECEIPT OF FEES - Family misc private");
        }
    }

    /**
     *
     * Test cached fees when fees-register returns
     * no fees
     *
     * */
    @Test
    @WithMockUser(authorities = "payments")
    public void testCacheWithInValidFeesOrNoFees() throws Exception {
        // Wire-mock fees-register response
        stubFor(get(urlPathMatching("/fees-register/fees"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("")));

        // Invoke fees-register service
        feesService.getFeesDtoMap();

        // Validate cached fees
        Cache cache = this.cacheManager.getCache("feesDtoMap");
        assertThat(cache.get("getFeesDtoMap")).isNull();
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void testCacheForSiteIds() throws Exception {
        // Invoke site service
        siteService.getAllSites();

        // Test cache for site ids
        Cache cache = this.cacheManager.getCache("sites");
        List<Site> sites = (List<Site>) cache.get("getAllSites").get();
        assertThat(sites).isNotEmpty();
        Optional<Site> optSite = sites.stream().filter(s -> s.getSiteId().equals("Y431")).findAny();
        if (optSite.isPresent()) {
            Site site = optSite.get();
            assertThat(site.getSiteId()).isEqualTo("Y431");
            assertThat(site.getName()).isEqualTo("Bromley County Court");
            assertThat(site.getSopReference()).isEqualTo("10251430");
            assertThat(site.getService()).isEqualTo("County");
        }
    }

    @SneakyThrows
    protected String contentsOf(String fileName) {
        String content = new String(Files.readAllBytes(Paths.get(ResourceUtils.getURL("classpath:" + fileName).toURI())));
        return resolvePlaceholders(content);
    }


    protected String resolvePlaceholders(String content) {
        return configurableListableBeanFactory.resolveEmbeddedValue(content);
    }
}
