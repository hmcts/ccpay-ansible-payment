package uk.gov.hmcts.payment.api.domain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentFeeNotFoundException;

import java.util.Collections;
import java.util.List;
@Service
public class FeeDomainServiceImpl implements FeeDomainService{

    @Autowired
    private FeePayApportionService feePayApportionService;

    @Autowired
    private PaymentFeeRepository paymentFeeRepository;

    @Override
    public List<FeePayApportion> getFeePayApportionsByFee(PaymentFee fee) {
        return feePayApportionService.getFeePayApportionByFeeId(fee.getId());
    }

    @Override
    public PaymentFee getPaymentFeeById(Integer feeId) {
        return paymentFeeRepository.findById(feeId).orElseThrow(PaymentFeeNotFoundException::new);

    }


}
