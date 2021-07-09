package uk.gov.hmcts.payment.api.domain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.model.*;

import java.util.Optional;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    @Autowired
    private IdempotencyKeysRepository idempotencyKeysRepository;

    @Override
    public Optional<IdempotencyKeys> findTheRecordByIdempotencyKey(String idempotencyKeyToCheck) {
        return idempotencyKeysRepository.findByIdempotencyKey(idempotencyKeyToCheck);
    }
}
