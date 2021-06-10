package uk.gov.hmcts.payment.api.service;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


@Service
public class UserAwareDelegatingCreditAccountPaymentService implements CreditAccountPaymentService<PaymentFeeLink, String> {

    private static final Logger LOG = LoggerFactory.getLogger(UserAwareDelegatingCreditAccountPaymentService.class);

    private final static String PAYMENT_CHANNEL_ONLINE = "online";

    private final static String PAYMENT_METHOD = "payment by account";

    private final static String PAYMENT_METHOD_BY_ACCOUNT = "payment by account";

    private final PaymentFeeLinkRepository paymentFeeLinkRepository;
    private final PaymentStatusRepository paymentStatusRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final Payment2Repository paymentRespository;
    private final ReferenceUtil referenceUtil;
    private final ServiceIdSupplier serviceIdSupplier;
    private final UserIdSupplier userIdSupplier;

    private final OrderCaseUtil orderCaseUtil;

    @Autowired
    public UserAwareDelegatingCreditAccountPaymentService(PaymentFeeLinkRepository paymentFeeLinkRepository,
                                                          PaymentChannelRepository paymentChannelRepository,
                                                          PaymentMethodRepository paymentMethodRepository, PaymentProviderRepository paymentProviderRepository,
                                                          PaymentStatusRepository paymentStatusRepository, Payment2Repository paymentRespository,
                                                          ReferenceUtil referenceUtil, ServiceIdSupplier serviceIdSupplier, UserIdSupplier userIdSupplier,
                                                          OrderCaseUtil orderCaseUtil) {
        this.paymentFeeLinkRepository = paymentFeeLinkRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentStatusRepository = paymentStatusRepository;
        this.paymentRespository = paymentRespository;
        this.referenceUtil = referenceUtil;
        this.serviceIdSupplier = serviceIdSupplier;
        this.userIdSupplier = userIdSupplier;
        this.orderCaseUtil = orderCaseUtil;
    }


    @Override
    public PaymentFeeLink create(Payment creditAccount, List<PaymentFee> fees, String paymentGroupRef) throws CheckDigitException {
        LOG.debug("Create credit account payment with PaymentGroupReference: {}", paymentGroupRef);

        Payment payment = null;
        try {
            payment = Payment.paymentWith()
                .userId(userIdSupplier.get())
                .amount(creditAccount.getAmount())
                .description(creditAccount.getDescription())
                .returnUrl(creditAccount.getReturnUrl())
                .ccdCaseNumber(creditAccount.getCcdCaseNumber())
                .caseReference(creditAccount.getCaseReference())
                .currency(creditAccount.getCurrency())
                .siteId(creditAccount.getSiteId())
                .serviceType(creditAccount.getServiceType())
                .s2sServiceName(serviceIdSupplier.get())
                .customerReference(creditAccount.getCustomerReference())
                .organisationName(creditAccount.getOrganisationName())
                .pbaNumber(creditAccount.getPbaNumber())
                .paymentChannel(paymentChannelRepository.findByNameOrThrow(PAYMENT_CHANNEL_ONLINE))
                .paymentMethod(paymentMethodRepository.findByNameOrThrow(PAYMENT_METHOD_BY_ACCOUNT))
                .paymentStatus(paymentStatusRepository.findByNameOrThrow(creditAccount.getPaymentStatus().getName()))
                .reference(referenceUtil.getNext("RC"))
                .statusHistories(creditAccount.getStatusHistories() == null ? Arrays.asList(StatusHistory.statusHistoryWith()
                    .status(paymentStatusRepository.findByNameOrThrow(creditAccount.getPaymentStatus().getName()).getName())
                    .build())
                    : creditAccount.getStatusHistories())
                .build();
        } catch (CheckDigitException e) {
            LOG.error("Error in generating check digit for the payment reference, {}", e);
        }

        PaymentFeeLink result = paymentFeeLinkRepository.save(orderCaseUtil.enhanceWithOrderCaseDetails(PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference(paymentGroupRef)
            .payments(Arrays.asList(payment))
            .fees(fees)
            .build(), payment));

        return result;
    }

    @Override
    public PaymentFeeLink retrieveByPaymentGroupReference(String paymentGroupReference) {
        return paymentFeeLinkRepository.findByPaymentReference(paymentGroupReference).orElseThrow(PaymentNotFoundException::new);
    }

    @Override
    public PaymentFeeLink retrieveByPaymentReference(String paymentReference) {
        Payment payment = findSavedPayment(paymentReference);
        return payment.getPaymentLink();
    }

    @Override
    public List<PaymentFeeLink> search(Date startDate, Date endDate) {
        LOG.info("Search for payments between " + startDate + " and " + endDate);
        List<PaymentFeeLink> paymentFeeLinks = paymentFeeLinkRepository.findAll(findCreditAccountPaymentsByBetweenDates(startDate, endDate));
        return paymentFeeLinks;
    }

    private Payment findSavedPayment(@NotNull String paymentReference) {
        return paymentRespository.findByReferenceAndPaymentMethod(paymentReference,
            PaymentMethod.paymentMethodWith().name(PAYMENT_METHOD).build()).orElseThrow(PaymentNotFoundException::new);
    }

    private static Specification findCreditAccountPaymentsByBetweenDates(Date fromDate, Date toDate) {
        return Specification
            .where(isEquals(PaymentMethod.paymentMethodWith().name(PAYMENT_METHOD).build()))
            .and(isBetween(fromDate, toDate));
    }

    private static Specification isEquals(PaymentMethod paymentMethod) {
        return ((root, query, cb) -> {
            Join<PaymentFeeLink, Payment> paymentJoin = root.join("payments", JoinType.LEFT);
            return cb.equal(paymentJoin.get("paymentMethod").get("name"), paymentMethod.getName());
        });

    }

    private static Specification isBetween(Date startDate, Date endDate) {

        return ((root, query, cb) -> {
            Join<PaymentFeeLink, Payment> paymentJoin = root.join("payments", JoinType.LEFT);
            return cb.between(paymentJoin.get("dateUpdated"), startDate, endDate);
        });
    }
}
