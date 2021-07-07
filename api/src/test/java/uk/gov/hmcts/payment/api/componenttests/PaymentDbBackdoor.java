package uk.gov.hmcts.payment.api.componenttests;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.model.FeePayApportion.FeePayApportionBuilder;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink.PaymentFeeLinkBuilder;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

@Component
public class PaymentDbBackdoor {

    @Autowired
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Autowired
    private Payment2Repository paymentRepository;

    @Autowired
    private FeePayApportionRepository feePayApportionRepository;


    public PaymentFeeLink create(PaymentFeeLinkBuilder paymentFeeLink) {
        return paymentFeeLinkRepository.save(paymentFeeLink.build());
    }

    public PaymentFeeLink findByReference(String reference) {
        return paymentFeeLinkRepository.findByPaymentReference(reference).orElseThrow(PaymentNotFoundException::new);
    }

    public FeePayApportion createApportionDetails(FeePayApportionBuilder apportionBuilder)
    {
        return feePayApportionRepository.save(apportionBuilder.build());
    }

}
