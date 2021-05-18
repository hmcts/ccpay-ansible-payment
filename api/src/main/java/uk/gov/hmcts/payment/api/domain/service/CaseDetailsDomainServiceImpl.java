package uk.gov.hmcts.payment.api.domain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.exception.CaseDetailsNotFoundException;
import uk.gov.hmcts.payment.api.model.CaseDetails;
import uk.gov.hmcts.payment.api.model.CaseDetailsRepository;

import java.util.Optional;
@Service
public class CaseDetailsDomainServiceImpl implements CaseDetailsDomainService {

    @Autowired
    private CaseDetailsRepository caseDetailsRepository;

    @Override
    public CaseDetails findByCcdCaseNumber(String ccdCaseNumber) {
        Optional<CaseDetails> caseDetails = caseDetailsRepository.findByCcdCaseNumber(ccdCaseNumber);
        return caseDetails.orElseThrow(()->{return new CaseDetailsNotFoundException("Case Details Not found for"+ccdCaseNumber);});
    }
}
