package uk.gov.hmcts.payment.api.model;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentFeeRepository extends CrudRepository<PaymentFee, Integer>, JpaSpecificationExecutor<PaymentFee> {

    <S extends PaymentFee> S save(S entity);

    Optional<List<PaymentFee>> findByPaymentLinkId(Integer id);

    Optional<List<PaymentFee>> findByCcdCaseNumber(String ccdCaseNumber);

    Optional<PaymentFee> findById(Integer id);

}
