package uk.gov.hmcts.payment.api.domain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;
import uk.gov.hmcts.reform.ccd.client.model.SearchCriteria;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentDomainServiceImpl implements PaymentDomainService{

    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;

    @Override
    public Payment getPaymentByApportionment(FeePayApportion feePayApportion) {
        return paymentService.getPaymentById(feePayApportion.getPaymentId());
    }

    @Override
    public Payment getPaymentByReference(String reference) {
        return paymentService.findSavedPayment(reference);
    }

    public List<FeePayApportion> getFeePayApportionByPaymentId(Integer paymentId){
        return paymentService.findByPaymentId(paymentId);
    }
}
