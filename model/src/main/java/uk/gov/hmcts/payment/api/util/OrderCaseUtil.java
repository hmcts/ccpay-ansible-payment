package uk.gov.hmcts.payment.api.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.dto.RemissionServiceRequest;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeLinkRepository;

@Component
public class OrderCaseUtil {

    @Autowired
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    public void updateOrderCaseDetails(PaymentFeeLink paymentFeeLink, Payment payment) {

        if (payment.getCcdCaseNumber() != null) {
            paymentFeeLink.setCcdCaseNumber(payment.getCcdCaseNumber());
        }
        if (payment.getCaseReference() != null) {
            paymentFeeLink.setCaseReference(payment.getCaseReference());
        }
        paymentFeeLink.setEnterpriseServiceName(payment.getServiceType());
        paymentFeeLink.setOrgId(payment.getSiteId());
    }

    public void updateOrderCaseDetails(PaymentFeeLink paymentFeeLink, RemissionServiceRequest remission) {
        if (remission.getCcdCaseNumber() != null) {
            paymentFeeLink.setCcdCaseNumber(remission.getCcdCaseNumber());
        }
        if (remission.getCaseReference() != null) {
            paymentFeeLink.setCaseReference(remission.getCaseReference());
        }
        paymentFeeLink.setOrgId(remission.getSiteId());
    }

    public PaymentFeeLink enhanceWithOrderCaseDetails(PaymentFeeLink paymentFeeLink, Payment payment) {

        paymentFeeLink.setOrgId(payment.getSiteId());
        paymentFeeLink.setEnterpriseServiceName(payment.getServiceType());
        if (payment.getCcdCaseNumber() != null) {
            paymentFeeLink.setCcdCaseNumber(payment.getCcdCaseNumber());
        }
        if (payment.getCaseReference() != null) {
            paymentFeeLink.setCaseReference(payment.getCaseReference());
        }
        return paymentFeeLink;
    }

    public PaymentFeeLink enhanceWithOrderCaseDetails(PaymentFeeLink paymentFeeLink, RemissionServiceRequest remission) {

        paymentFeeLink.setOrgId(remission.getSiteId());
        if (remission.getCcdCaseNumber() != null) {
            paymentFeeLink.setCcdCaseNumber(remission.getCcdCaseNumber());
        }
        if (remission.getCaseReference() != null) {
            paymentFeeLink.setCaseReference(remission.getCaseReference());
        }
        return paymentFeeLink;
    }
}
