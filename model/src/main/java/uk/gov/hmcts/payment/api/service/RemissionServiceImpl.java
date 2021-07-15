package uk.gov.hmcts.payment.api.service;

import com.google.common.collect.Lists;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.dto.RemissionServiceRequest;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.InvalidPaymentGroupReferenceException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentFeeNotFoundException;

import javax.transaction.Transactional;
import java.util.Collections;

@Service
public class RemissionServiceImpl implements RemissionService {

    private final PaymentFeeLinkRepository paymentFeeLinkRepository;
    private final ReferenceUtil referenceUtil;

    private final OrderCaseUtil orderCaseUtil;

    @Autowired
    public RemissionServiceImpl(PaymentFeeLinkRepository paymentFeeLinkRepository,
                                ReferenceUtil referenceUtil, OrderCaseUtil orderCaseUtil) {
        this.paymentFeeLinkRepository = paymentFeeLinkRepository;
        this.referenceUtil = referenceUtil;
        this.orderCaseUtil = orderCaseUtil;
    }

    @Override
    public PaymentFeeLink createRemission(RemissionServiceRequest remissionServiceRequest) throws CheckDigitException {
        String remissionReference = referenceUtil.getNext("RM");
        remissionServiceRequest.setRemissionReference(remissionReference);

        Remission remission = buildRemission(remissionServiceRequest);
        PaymentFee fee = remissionServiceRequest.getFee();

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference(remissionServiceRequest.getPaymentGroupReference())
            .remissions(Collections.singletonList(remission))
            .fees(Collections.singletonList(fee))
            .build();
        remission.setPaymentFeeLink(paymentFeeLink);
        fee.setRemissions(Collections.singletonList(remission));

        return paymentFeeLinkRepository.save(orderCaseUtil.enhanceWithOrderCaseDetails(paymentFeeLink, remissionServiceRequest));

    }

    @Override
    @Transactional
    public PaymentFeeLink createRetrospectiveRemission(RemissionServiceRequest remissionServiceRequest, String paymentGroupReference, Integer feeId) throws CheckDigitException {
        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.findByPaymentReference(paymentGroupReference)
            .orElseThrow(() -> new InvalidPaymentGroupReferenceException("Payment group " + paymentGroupReference + " does not exists."));

        orderCaseUtil.updateOrderCaseDetails(paymentFeeLink, remissionServiceRequest);

        // Tactical check where feeId is null
        PaymentFee fee = feeId != null ? paymentFeeLink.getFees().stream().filter(f -> f.getId().equals(feeId))
            .findAny()
            .orElseThrow(() -> new PaymentFeeNotFoundException("Fee with id " + feeId + " does not exists.")) :
            paymentFeeLink.getFees().stream().filter(f -> f.getCode().equals(remissionServiceRequest.getFee().getCode()))
                .findAny()
                .orElseThrow(() -> new PaymentFeeNotFoundException("Fee with code " + remissionServiceRequest.getFee().getCode() + " does not exists."));

        String remissionReference = referenceUtil.getNext("RM");
        remissionServiceRequest.setRemissionReference(remissionReference);

        Remission remission = buildRemission(remissionServiceRequest);

        paymentFeeLink.setRemissions(Lists.newArrayList(remission));

        fee.setNetAmount(fee.getCalculatedAmount().subtract(remission.getHwfAmount()));

        if(remissionServiceRequest.isRetroRemission()){
            paymentFeeLink.getPayments().stream().forEach(p-> {
                if(p.getPaymentStatus().getName().equalsIgnoreCase("success")){
                    fee.setAmountDue(fee.getCalculatedAmount().subtract(remission.getHwfAmount()).subtract(p.getAmount()));
                }else{
                    fee.setAmountDue(fee.getCalculatedAmount().subtract(remission.getHwfAmount()));
                }
            });
        }

        if (fee.getRemissions() == null) {
            fee.setRemissions(Lists.newArrayList(remission));
        } else {
            fee.getRemissions().add(remission);
        }
        remission.setPaymentFeeLink(paymentFeeLink);
        remission.setFee(fee);

        return paymentFeeLink;
    }

    private Remission buildRemission(RemissionServiceRequest remissionServiceRequest) {
        return Remission.remissionWith()
            .remissionReference(remissionServiceRequest.getRemissionReference())
            .hwfReference(remissionServiceRequest.getHwfReference())
            .hwfAmount(remissionServiceRequest.getHwfAmount())
            .beneficiaryName(remissionServiceRequest.getBeneficiaryName())
            .ccdCaseNumber(remissionServiceRequest.getCcdCaseNumber())
            .caseReference(remissionServiceRequest.getCaseReference())
            .siteId(remissionServiceRequest.getSiteId())
            .build();
    }

}
