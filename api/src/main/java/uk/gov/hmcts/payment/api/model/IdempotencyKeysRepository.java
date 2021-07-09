package uk.gov.hmcts.payment.api.model;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface IdempotencyKeysRepository extends CrudRepository<IdempotencyKeys, IdempotencyKeysPK>, JpaSpecificationExecutor<IdempotencyKeys> {

    <S extends IdempotencyKeys> S save(S entity);

    Optional<IdempotencyKeys> findByIdempotencyKey(String id);

}
