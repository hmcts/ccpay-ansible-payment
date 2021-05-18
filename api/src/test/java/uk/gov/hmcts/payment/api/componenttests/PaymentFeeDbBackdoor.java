package uk.gov.hmcts.payment.api.componenttests;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentFeeNotFoundException;

import java.util.List;
import java.util.Optional;

@Component
public class PaymentFeeDbBackdoor {

    @Autowired
    private PaymentFeeRepository paymentFeeRepository;

    public PaymentFee findByPaymentLinkId(Integer id) {
        Optional<List<PaymentFee>> listOfPaymentFee = paymentFeeRepository.findByPaymentLinkId(id);

        if (listOfPaymentFee.isPresent() && listOfPaymentFee.get().get(0) != null) {
            return paymentFeeRepository.findByPaymentLinkId(id).get().get(0);
        } else {
            throw new PaymentFeeNotFoundException();
        }
    }
}
