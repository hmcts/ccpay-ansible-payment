package uk.gov.hmcts.payment.api.domain.service;

import uk.gov.hmcts.payment.api.model.IdempotencyKeys;

import java.util.Optional;

public interface IdempotencyService {
    Optional<IdempotencyKeys> findTheRecordByIdempotencyKey(String idempotencyKeyToCheck);
}
