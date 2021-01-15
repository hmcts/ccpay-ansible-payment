package uk.gov.hmcts.payment.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.StatusHistory;
import uk.gov.hmcts.payment.api.util.PayStatusToPayHubStatus;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import javax.validation.constraints.NotNull;
import java.util.Collections;

@Service
public class ReplayCreditAccountPaymentServiceImpl implements ReplayCreditAccountPaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayCreditAccountPaymentServiceImpl.class);

    private final Payment2Repository paymentRespository;

    @Autowired
    public ReplayCreditAccountPaymentServiceImpl(Payment2Repository paymentRespository){
        this.paymentRespository = paymentRespository;
    }

    @Override
    public void updatePaymentStatusByReference(String paymentReference, PaymentStatus paymentStatus, String message) {

        Payment payment = findSavedPayment(paymentReference);
        payment.setPaymentStatus(paymentStatus);
        payment.setStatus(paymentStatus.getName());
        payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
            .status(paymentStatus.getName())
            .message(message)
            .build()));
    }

    private Payment findSavedPayment(@NotNull String paymentReference) {
        return paymentRespository.findByReference(paymentReference).orElseThrow(PaymentNotFoundException::new);
    }
}
