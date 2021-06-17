package uk.gov.hmcts.payment.api.model;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentFeeLinkRepository extends CrudRepository<PaymentFeeLink, Integer>, JpaSpecificationExecutor<PaymentFeeLink> {

    <S extends PaymentFeeLink> S save(S entity);

    Optional<PaymentFeeLink> findByPaymentReference(String id);

    Optional<List<PaymentFeeLink>> findByCcdCaseNumber(String ccdCaseNumber);

}
