package uk.gov.hmcts.payment.api.service;

import com.google.common.collect.ImmutableMap;
import net.logstash.logback.argument.StructuredArguments;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.http.MethodNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.PaymentServiceRequest;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;

import java.util.List;

@Component
public class LoggingPaymentService implements DelegatingPaymentService<PaymentFeeLink, String> {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPaymentService.class);

    private static final String PAYMENT_ID = "paymentId";
    private static final String USER_ID = "userId";
    private static final String EVENT_TYPE = "eventType";
    private static final String AMOUNT = "amount";
    private static final String REFERENCE = "reference";
    private static final String CCD_CASE_NUMBER = "ccdCaseNumber";
    private static final String CASE_REFERENCE = "caseReference";
    private static final String CURRENCY = "currency";
    private static final String FEES = "fees";


    private final UserIdSupplier userIdSupplier;
    private final DelegatingPaymentService<PaymentFeeLink, String> delegate;

    public LoggingPaymentService(UserIdSupplier userIdSupplier, DelegatingPaymentService<PaymentFeeLink, String> delegate) {
        this.userIdSupplier = userIdSupplier;
        this.delegate = delegate;
    }

    @Override
    public PaymentFeeLink create(PaymentServiceRequest paymentServiceRequest) throws CheckDigitException {
        PaymentFeeLink paymentFeeLink = delegate.create(paymentServiceRequest);

        Payment payment = paymentFeeLink.getPayments().get(0);
        LOG.info("Payment event", StructuredArguments.entries(ImmutableMap.of(
            PAYMENT_ID, payment.getId(),
            USER_ID, userIdSupplier.get(),
            EVENT_TYPE, "create",
            AMOUNT, payment.getAmount(),
            REFERENCE, payment.getReference()
        )));
        return paymentFeeLink;
    }

    @Override
    public PaymentFeeLink update(PaymentServiceRequest paymentServiceRequest) throws CheckDigitException, MethodNotSupportedException {
        PaymentFeeLink paymentFeeLink = delegate.update(paymentServiceRequest);
        Payment payment = paymentFeeLink.getPayments().get(0);
        LOG.info("Payment event", StructuredArguments.entries(ImmutableMap.of(
            PAYMENT_ID, payment.getId(),
            USER_ID, userIdSupplier.get(),
            EVENT_TYPE, "create",
            AMOUNT, payment.getAmount(),
            REFERENCE, payment.getReference()
        )));
        return paymentFeeLink;
    }

    @Override
    public PaymentFeeLink retrieve(String paymentReference) {
        return delegate.retrieve(paymentReference);
    }

    @Override
    public PaymentFeeLink retrieve(String s, String paymentTargetService) {
        return delegate.retrieve(s, paymentTargetService);
    }

    @Override
    public List<PaymentFeeLink> search(PaymentSearchCriteria searchCriteria) {

        if (searchCriteria.getStartDate() != null || searchCriteria.getEndDate() != null) {
            LOG.info("Searching for payments between {} and {}", searchCriteria.getStartDate(), searchCriteria.getEndDate());
        }

        List<PaymentFeeLink> paymentFeeLinks = delegate.search(searchCriteria);

        LOG.info("PaymentFeeLinks found: {}", paymentFeeLinks.size());

        return paymentFeeLinks;
    }

    @Override
    public List<Payment> searchByCriteria(PaymentSearchCriteria searchCriteria) {
        if (searchCriteria.getStartDate() != null || searchCriteria.getEndDate() != null) {
            LOG.info("Searching for payments between {} and {}", searchCriteria.getStartDate(), searchCriteria.getEndDate());
        }

        List<Payment> payments = delegate.searchByCriteria(searchCriteria);
        return payments;
    }

    @Override
    public void cancel(String paymentReference) {
        LOG.info("Cancel payment for supplied payment reference : {}", paymentReference);
    }
}
