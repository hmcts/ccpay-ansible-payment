package uk.gov.hmcts.payment.api.service;

import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;

import java.util.List;
import java.util.Map;

public interface ReferenceDataService<T> {
    List<T> getSiteIDs();

    OrganisationalServiceDto getOrganisationalDetail(String caseType, MultiValueMap<String, String> headers);

}
