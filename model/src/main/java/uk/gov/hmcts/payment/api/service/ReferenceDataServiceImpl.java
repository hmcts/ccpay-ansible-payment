package uk.gov.hmcts.payment.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.v1.model.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.NoServiceFoundException;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;
import uk.gov.hmcts.payment.referencedata.model.Site;
import uk.gov.hmcts.payment.referencedata.service.SiteService;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;

import java.util.Collections;
import java.util.List;

@Service
public class ReferenceDataServiceImpl implements ReferenceDataService<SiteDTO> {

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceDataService.class);

    @Autowired
    private SiteService<Site, String> siteService;

    @Autowired()
    @Qualifier("restTemplatePaymentGroup")
    private RestTemplate restTemplatePaymentGroup;

    @Value("${rd.location.url}")
    private String rdBaseUrl;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    private static final String RD_ENDPOINT = "/refdata/location/orgServices";

    @Override
    public List<SiteDTO> getSiteIDs() {
        return SiteDTO.fromSiteList(siteService.getAllSites());
    }

    @Override
    public OrganisationalServiceDto getOrganisationalDetail(String caseType, MultiValueMap<String, String> headers) {

        MultiValueMap<String, String> headerMultiValueMapForOrganisationalDetail = new LinkedMultiValueMap<String, String>();
        List<OrganisationalServiceDto> orgServiceResponse;
        try {
            String serviceAuthorisation = authTokenGenerator.generate();
            headerMultiValueMapForOrganisationalDetail.put("Content-Type", headers.get("content-type"));
            String userAuthorization = headers.get("authorization") != null ? headers.get("authorization").get(0) : headers.get("Authorization").get(0);
            headerMultiValueMapForOrganisationalDetail.put("Authorization", Collections.singletonList(userAuthorization.startsWith("Bearer ")
                ? userAuthorization : "Bearer ".concat(userAuthorization)));
            headerMultiValueMapForOrganisationalDetail.put("ServiceAuthorization", Collections.singletonList(serviceAuthorisation));
            HttpHeaders httpHeaders = new HttpHeaders(headerMultiValueMapForOrganisationalDetail);
            final HttpEntity<String> entity = new HttpEntity<>(httpHeaders);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(rdBaseUrl + RD_ENDPOINT)
                .queryParam("ccdCaseType", caseType);
            LOG.debug("builder.toUriString() : {}", builder.toUriString());
            ResponseEntity<List<OrganisationalServiceDto>> responseEntity = restTemplatePaymentGroup
                .exchange(builder.toUriString(), HttpMethod.GET, entity, new ParameterizedTypeReference<List<OrganisationalServiceDto>>() {
            });
            orgServiceResponse = responseEntity.hasBody() ? responseEntity.getBody() : null;
            if (orgServiceResponse == null || orgServiceResponse.isEmpty()) {
                throw new NoServiceFoundException("No Service found for given CaseType");
            }
            return orgServiceResponse.get(0);
        } catch (HttpClientErrorException e) {
            LOG.error("client err ", e);
            throw new NoServiceFoundException("No Service found for given CaseType");
        } catch (HttpServerErrorException e) {
            LOG.error("server err ", e);
            throw new GatewayTimeoutException("Unable to retrieve service information. Please try again later");
        }
    }
}
