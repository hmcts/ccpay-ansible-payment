package uk.gov.hmcts.payment.api.model;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface FeePayApportionRepository extends CrudRepository<FeePayApportion, Integer> {

    Optional<FeePayApportion> findByFeeIdAndPaymentId(Integer feeId, Integer paymentId);

    Optional<List<FeePayApportion>> findByFeeId(Integer feeId);

    Optional<List<FeePayApportion>> findByPaymentId(Integer paymentId);
}
