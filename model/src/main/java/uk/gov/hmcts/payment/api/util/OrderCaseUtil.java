package uk.gov.hmcts.payment.api.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.dto.RemissionServiceRequest;
import uk.gov.hmcts.payment.api.model.CaseDetails;
import uk.gov.hmcts.payment.api.model.CaseDetailsRepository;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;

import java.util.Collections;
import java.util.Optional;

@Component
public class OrderCaseUtil {

    @Autowired
    private CaseDetailsRepository caseDetailsRepository;

    public void updateOrderCaseDetails(PaymentFeeLink paymentFeeLink, Payment payment) {
        if(paymentFeeLink.getCaseDetails().isEmpty() && payment.getCcdCaseNumber() != null) {
            paymentFeeLink.setCaseDetails(Collections.singleton(CaseDetails.caseDetailsWith()
                .ccdCaseNumber(payment.getCcdCaseNumber())
                .caseReference(payment.getCaseReference())
                .build()));
        } else {
            paymentFeeLink.getCaseDetails().stream().forEach(caseDetails -> {
                caseDetails.setCcdCaseNumber(payment.getCcdCaseNumber());
            });
        }

        paymentFeeLink.setEnterpriseServiceName(payment.getServiceType());
        paymentFeeLink.setOrgId(payment.getSiteId());
    }

    public void updateOrderCaseDetails(PaymentFeeLink paymentFeeLink, RemissionServiceRequest remission) {
        if(paymentFeeLink.getCaseDetails().isEmpty() && remission.getCcdCaseNumber() != null) {
            paymentFeeLink.setCaseDetails(Collections.singleton(CaseDetails.caseDetailsWith()
                .ccdCaseNumber(remission.getCcdCaseNumber())
                .caseReference(remission.getCaseReference())
                .build()));
        }

        paymentFeeLink.setOrgId(remission.getSiteId());
    }

    public PaymentFeeLink enhanceWithOrderCaseDetails(PaymentFeeLink paymentFeeLink, Payment payment) {

        if(payment.getCcdCaseNumber() != null) {
            Optional<CaseDetails> existingCaseDetails = caseDetailsRepository.findByCcdCaseNumber(payment.getCcdCaseNumber());
            if(existingCaseDetails.isPresent()) {
                paymentFeeLink.setCaseDetails(Collections.singleton(existingCaseDetails.get()));
            } else {
                paymentFeeLink.setCaseDetails(Collections.singleton(CaseDetails.caseDetailsWith()
                    .ccdCaseNumber(payment.getCcdCaseNumber())
                    .caseReference(payment.getCaseReference())
                    .build()));
            }
        }

        paymentFeeLink.setEnterpriseServiceName(payment.getServiceType());
        paymentFeeLink.setOrgId(payment.getSiteId());

        return paymentFeeLink;
    }

    public PaymentFeeLink enhanceWithOrderCaseDetails(PaymentFeeLink paymentFeeLink, RemissionServiceRequest remission) {

        if(remission.getCcdCaseNumber() != null) {
            Optional<CaseDetails> existingCaseDetails = caseDetailsRepository.findByCcdCaseNumber(remission.getCcdCaseNumber());
            if(existingCaseDetails.isPresent()) {
                paymentFeeLink.setCaseDetails(Collections.singleton(existingCaseDetails.get()));
            } else {
                paymentFeeLink.setCaseDetails(Collections.singleton(CaseDetails.caseDetailsWith()
                    .ccdCaseNumber(remission.getCcdCaseNumber())
                    .caseReference(remission.getCaseReference())
                    .build()));
            }
        }

        paymentFeeLink.setOrgId(remission.getSiteId());

        return paymentFeeLink;
    }
}
