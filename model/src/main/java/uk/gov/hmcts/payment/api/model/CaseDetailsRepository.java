package uk.gov.hmcts.payment.api.model;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CaseDetailsRepository extends CrudRepository<CaseDetails, Integer> {

    <S extends CaseDetails> S save(S entity);

    Optional<CaseDetails> findByCcdCaseNumber(String ccdCaseNumber);
}


