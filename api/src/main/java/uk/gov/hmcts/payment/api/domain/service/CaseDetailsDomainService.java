package uk.gov.hmcts.payment.api.domain.service;

import uk.gov.hmcts.payment.api.model.CaseDetails;

public interface CaseDetailsDomainService {
    CaseDetails findByCcdCaseNumber(String ccdCaseNumber);
}
