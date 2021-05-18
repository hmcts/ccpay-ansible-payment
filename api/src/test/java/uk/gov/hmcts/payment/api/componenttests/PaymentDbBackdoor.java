package uk.gov.hmcts.payment.api.componenttests;

import ch.qos.logback.core.db.dialect.DBUtil;

import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.model.FeePayApportion.FeePayApportionBuilder;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink.PaymentFeeLinkBuilder;
import uk.gov.hmcts.payment.api.util.DateUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PaymentDbBackdoor {

    @Autowired
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Autowired
    private Payment2Repository paymentRepository;

    @Autowired
    private FeePayApportionRepository feePayApportionRepository;

    @Autowired
    private CaseDetailsRepository caseDetailsRepository;


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

    public List<Payment> findByCcdCaseNumber(String ccdCaseNumber) {
        return paymentRepository.findByCcdCaseNumber(ccdCaseNumber).orElseThrow(PaymentNotFoundException::new);
    }


}
