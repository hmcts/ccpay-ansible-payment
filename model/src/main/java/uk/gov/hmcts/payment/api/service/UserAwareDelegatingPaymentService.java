package uk.gov.hmcts.payment.api.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.http.MethodNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.payment.api.audit.AuditRepository;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.PaymentServiceRequest;
import uk.gov.hmcts.payment.api.dto.PciPalPayment;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.external.client.dto.Link;
import uk.gov.hmcts.payment.api.external.client.exceptions.GovPayPaymentNotFoundException;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.PayStatusToPayHubStatus;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.exceptions.InvalidPaymentGroupReferenceException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;
import uk.gov.hmcts.payment.api.v1.model.govpay.GovPayAuthUtil;

import javax.persistence.criteria.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.*;

@Service
@Primary
public class UserAwareDelegatingPaymentService implements DelegatingPaymentService<PaymentFeeLink, String> {
    private static final Logger LOG = LoggerFactory.getLogger(UserAwareDelegatingPaymentService.class);

    private final static String PAYMENT_CHANNEL_TELEPHONY = "telephony";
    private final static String PAYMENT_PROVIDER_PCI_PAL = "pci pal";
    private final static String PAYMENT_METHOD = "paymentMethod";
    private final static String PAYMENT_STATUS_CREATED = "created";
    private final static String PAYMENT_METHOD_CARD = "card";

    private final UserIdSupplier userIdSupplier;
    private final PaymentFeeLinkRepository paymentFeeLinkRepository;
    private final DelegatingPaymentService<GovPayPayment, String> delegateGovPay;
    private final DelegatingPaymentService<PciPalPayment, String> delegatePciPal;
    private final PaymentStatusRepository paymentStatusRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final PaymentProviderRepository paymentProviderRespository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final Payment2Repository paymentRespository;
    private final ReferenceUtil referenceUtil;
    private final GovPayAuthUtil govPayAuthUtil;
    private final ServiceIdSupplier serviceIdSupplier;
    private final AuditRepository auditRepository;
    private final CallbackService callbackService;
    private final FeePayApportionRepository feePayApportionRepository;
    private final PaymentFeeRepository paymentFeeRepository;
    private final FeePayApportionService feePayApportionService;
    private final LaunchDarklyFeatureToggler featureToggler;

    private final OrderCaseUtil orderCaseUtil;

    private static final Predicate[] REF = new Predicate[0];

    @Value("${gov.pay.url}") String govpayUrl;

    @Autowired
    public UserAwareDelegatingPaymentService(UserIdSupplier userIdSupplier,
                                             PaymentFeeLinkRepository paymentFeeLinkRepository,
                                             DelegatingPaymentService<GovPayPayment, String> delegateGovPay,
                                             DelegatingPaymentService<PciPalPayment, String> delegatePciPal,
                                             PaymentChannelRepository paymentChannelRepository,
                                             PaymentMethodRepository paymentMethodRepository,
                                             PaymentProviderRepository paymentProviderRepository,
                                             PaymentStatusRepository paymentStatusRepository,
                                             Payment2Repository paymentRespository,
                                             ReferenceUtil referenceUtil,
                                             GovPayAuthUtil govPayAuthUtil,
                                             ServiceIdSupplier serviceIdSupplier,
                                             AuditRepository auditRepository,
                                             CallbackService callbackService,
                                             FeePayApportionRepository feePayApportionRepository,
                                             PaymentFeeRepository paymentFeeRepository,
                                             FeePayApportionService feePayApportionService,
                                             LaunchDarklyFeatureToggler featureToggler,
                                             OrderCaseUtil orderCaseUtil) {
        this.userIdSupplier = userIdSupplier;
        this.paymentFeeLinkRepository = paymentFeeLinkRepository;
        this.delegateGovPay = delegateGovPay;
        this.delegatePciPal = delegatePciPal;
        this.paymentChannelRepository = paymentChannelRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentProviderRespository = paymentProviderRepository;
        this.paymentStatusRepository = paymentStatusRepository;
        this.paymentRespository = paymentRespository;
        this.referenceUtil = referenceUtil;
        this.govPayAuthUtil = govPayAuthUtil;
        this.serviceIdSupplier = serviceIdSupplier;
        this.auditRepository = auditRepository;
        this.callbackService = callbackService;
        this.feePayApportionRepository = feePayApportionRepository;
        this.paymentFeeRepository = paymentFeeRepository;
        this.feePayApportionService = feePayApportionService;
        this.featureToggler = featureToggler;
        this.orderCaseUtil = orderCaseUtil;
    }

    @Override
    public PaymentFeeLink create(PaymentServiceRequest paymentServiceRequest)
        throws CheckDigitException {
        String paymentReference = referenceUtil.getNext("RC");
        paymentServiceRequest.setPaymentReference(paymentReference);

        Payment payment = buildPayment(paymentReference, paymentServiceRequest);
        if (PAYMENT_CHANNEL_TELEPHONY.equals(paymentServiceRequest.getChannel()) &&
            PAYMENT_PROVIDER_PCI_PAL.equals(paymentServiceRequest.getProvider())) {
            PciPalPayment pciPalPayment = delegatePciPal.create(paymentServiceRequest);
            fillTransientDetails(payment, pciPalPayment);
            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .externalStatus(pciPalPayment.getState().getStatus().toLowerCase())
                .status(PayStatusToPayHubStatus.valueOf(pciPalPayment.getState().getStatus().toLowerCase()).getMappedStatus())
                .errorCode(pciPalPayment.getState().getCode())
                .message(pciPalPayment.getState().getMessage())
                .build()));
        } else {
            GovPayPayment govPayPayment = delegateGovPay.create(paymentServiceRequest);
            fillTransientDetails(payment, govPayPayment);
            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .externalStatus(govPayPayment.getState().getStatus().toLowerCase())
                .status(PayStatusToPayHubStatus.valueOf(govPayPayment.getState().getStatus().toLowerCase()).getMappedStatus())
                .errorCode(govPayPayment.getState().getCode())
                .message(govPayPayment.getState().getMessage())
                .build()));
        }

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference(paymentServiceRequest.getPaymentGroupReference())
            .payments(Collections.singletonList(payment))
            .fees(paymentServiceRequest.getFees())
            .build();

        payment.setPaymentLink(paymentFeeLink);

        paymentFeeLink = paymentFeeLinkRepository.save(orderCaseUtil.enhanceWithOrderCaseDetails(paymentFeeLink, payment));

        auditRepository.trackPaymentEvent("CREATE_CARD_PAYMENT", payment, paymentServiceRequest.getFees());
        return paymentFeeLink;
    }

    @Override
    public PaymentFeeLink update(PaymentServiceRequest paymentServiceRequest)
        throws CheckDigitException, MethodNotSupportedException {

        Payment payment = buildPayment(paymentServiceRequest.getPaymentReference(), paymentServiceRequest);
        if (PAYMENT_CHANNEL_TELEPHONY.equals(paymentServiceRequest.getChannel()) &&
            PAYMENT_PROVIDER_PCI_PAL.equals(paymentServiceRequest.getProvider())) {
            PciPalPayment pciPalPayment = delegatePciPal.create(paymentServiceRequest);
            fillTransientDetails(payment, pciPalPayment);
            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .externalStatus(pciPalPayment.getState().getStatus().toLowerCase())
                .status(PayStatusToPayHubStatus.valueOf(pciPalPayment.getState().getStatus().toLowerCase()).getMappedStatus())
                .errorCode(pciPalPayment.getState().getCode())
                .message(pciPalPayment.getState().getMessage())
                .build()));
        } else {
            throw new MethodNotSupportedException("Only Telephony payments are supported");
        }

        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.findByPaymentReference(paymentServiceRequest.
            getPaymentGroupReference()).orElseThrow(InvalidPaymentGroupReferenceException::new);

        orderCaseUtil.updateOrderCaseDetails(paymentFeeLink, payment);

        payment.setPaymentLink(paymentFeeLink);

        if(paymentFeeLink.getPayments() != null){
            paymentFeeLink.getPayments().addAll(Lists.newArrayList(payment));
        } else {
            paymentFeeLink.setPayments(Lists.newArrayList(payment));
        }

        auditRepository.trackPaymentEvent("CREATE_CARD_PAYMENT", payment, paymentFeeLink.getFees());
        return paymentFeeLink;
    }

    private Payment buildPayment(String paymentReference, PaymentServiceRequest paymentServiceRequest) {
        return Payment.paymentWith().userId(userIdSupplier.get())
            .amount(paymentServiceRequest.getAmount())
            .description(paymentServiceRequest.getDescription()).returnUrl(paymentServiceRequest.getReturnUrl())
            .ccdCaseNumber(paymentServiceRequest.getCcdCaseNumber())
            .caseReference(paymentServiceRequest.getCaseReference()).currency(paymentServiceRequest.getCurrency())
            .siteId(paymentServiceRequest.getSiteId())
            .serviceType(paymentServiceRequest.getServiceType())
            .s2sServiceName(serviceIdSupplier.get())
            .paymentChannel(paymentChannelRepository.findByNameOrThrow(paymentServiceRequest.getChannel()))
            .paymentMethod(paymentMethodRepository.findByNameOrThrow(PAYMENT_METHOD_CARD))
            .paymentProvider(paymentProviderRespository.findByNameOrThrow(paymentServiceRequest.getProvider()))
            .paymentStatus(paymentStatusRepository.findByNameOrThrow(PAYMENT_STATUS_CREATED))
            .reference(paymentReference)
            .serviceCallbackUrl(paymentServiceRequest.getServiceCallbackUrl())
            .build();
    }

    private PaymentFeeLink retrieve(String paymentReference, boolean shouldCallBack) {

        final Payment payment = findSavedPayment(paymentReference);

        final PaymentFeeLink paymentFeeLink = payment.getPaymentLink();

        String paymentService = payment.getS2sServiceName();

        if (null == paymentService || paymentService.trim().equals("")) {
            LOG.error("Unable to determine the payment service which created this payment-Ref: {}",paymentReference);
        }

        paymentService = govPayAuthUtil.getServiceName(serviceIdSupplier.get(), paymentService);

        try {
            GovPayPayment govPayPayment = delegateGovPay.retrieve(payment.getExternalReference(), paymentService);

            fillTransientDetails(payment, govPayPayment);

            // Checking if the gov pay status already exists.
            boolean statusExists = payment.getStatusHistories().stream()
                .map(StatusHistory::getExternalStatus)
                .anyMatch(govPayPayment.getState().getStatus().toLowerCase()::equals);
            LOG.debug("Payment status exists in status history: {}", statusExists);

            if (!statusExists) {

                payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                    .externalStatus(govPayPayment.getState().getStatus())
                    .status(PayStatusToPayHubStatus.valueOf(govPayPayment.getState().getStatus().toLowerCase()).getMappedStatus())
                    .errorCode(govPayPayment.getState().getCode())
                    .message(govPayPayment.getState().getMessage())
                    .build()));

                //1. Update Fee Amount Due as Payment Status received from GovPAY as SUCCESS
                boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
                LOG.info("ApportionFeature Flag Value in UserAwareDelegatingPaymentService : {}", apportionFeature);
                if(apportionFeature) {
                    if(govPayPayment.getState().getStatus().toLowerCase().equalsIgnoreCase("success")) {
                        LOG.info("Update Fee Amount Due as Payment Status received from GovPAY as SUCCESS!!!");
                        feePayApportionService.updateFeeAmountDue(payment);
                    }
                }

                if (shouldCallBack && payment.getServiceCallbackUrl() != null) {
                    callbackService.callback(paymentFeeLink, payment);
                }
            }
        } catch (GovPayPaymentNotFoundException | NullPointerException pnfe) {
            LOG.error("Gov Pay payment not found id is:{} and govpay id is:{}", payment.getExternalReference(), paymentReference);
        }

        return paymentFeeLink;
    }

    @Override
    @Transactional
    public PaymentFeeLink retrieve(String paymentReference) {
        return retrieve(paymentReference, false);
    }

    @Override
    @Transactional
    public PaymentFeeLink retrieveWithCallBack(String paymentReference) {
        return retrieve(paymentReference, true);

    }

    @Override
    public PaymentFeeLink retrieve(String s, String paymentTargetService) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PaymentFeeLink> search(PaymentSearchCriteria searchCriteria) {
        return paymentFeeLinkRepository.findAll(findPayments(searchCriteria));
    }

    @Override
    public List<Payment> searchByCriteria(PaymentSearchCriteria searchCriteria) {
        return paymentRespository.findAll(constructPaymentSpecification(searchCriteria));
    }

    private static final Specification constructPaymentSpecification(final PaymentSearchCriteria searchCriteria) {
        return ((root, query, cb) -> constructPredicate(root, cb, searchCriteria, query));
    }

    private static Predicate constructPredicate(final Root<Payment> root,
                                                final CriteriaBuilder cb,
                                                final PaymentSearchCriteria searchCriteria,
                                                final CriteriaQuery<?> query) {
        final List<Predicate> predicates = new ArrayList<>();
        final Expression<Date> dateUpdatedExpr = cb.function("date_trunc", Date.class, cb.literal("seconds"), root.get("dateUpdated"));

        if (searchCriteria.getCcdCaseNumber() != null) {
            predicates.add(cb.equal(root.get("ccdCaseNumber"), searchCriteria.getCcdCaseNumber()));
        }
        if (searchCriteria.getStartDate() != null && searchCriteria.getEndDate() != null) {
            predicates.add(cb.between(dateUpdatedExpr, searchCriteria.getStartDate(), searchCriteria.getEndDate()));
        } else if (searchCriteria.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(dateUpdatedExpr, searchCriteria.getStartDate()));
        } else if (searchCriteria.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(dateUpdatedExpr, searchCriteria.getEndDate()));
        }
        if (searchCriteria.getPaymentMethod() != null) {
            if(searchCriteria.getPaymentMethod().equalsIgnoreCase("all"))
            {
                Predicate predicateCheque = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("cheque").build());
                Predicate predicateCard = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("card").build());
                Predicate predicateDD = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("direct debit").build());
                Predicate predicateCash = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("cash").build());
                Predicate predicatePBA = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("payment by account").build());
                Predicate predicateAllPay = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("all pay").build());
                Predicate predicatePO = cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("postal order").build());
                Predicate predicateFinal = cb.or(predicateCheque,predicateCard,predicateDD,predicateCash,predicatePBA,predicateAllPay,predicatePO);
                predicates.add(predicateFinal);
            }
            else
            {
                predicates.add(cb.equal(root.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name(searchCriteria.getPaymentMethod()).build()));
            }

        }
        if (searchCriteria.getServiceType() != null) {
            predicates.add(cb.equal(root.get("serviceType"), searchCriteria.getServiceType()));
        }
        if (searchCriteria.getPbaNumber() != null) {
            predicates.add(cb.equal(root.get("pbaNumber"), searchCriteria.getPbaNumber()));
        }

        query.groupBy(root.get("id"));
        return cb.and(predicates.toArray(REF));
    }


    @Override
    public void cancel(String paymentReference) {
        Payment payment = findSavedPayment(paymentReference);
        delegateGovPay.cancel(govpayUrl + "/" + payment.getExternalReference() + "/cancel");
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
            .put("paymentReference", payment.getReference())
            .put("amount", payment.getAmount().toString())
            .put("CcdCaseNumber", payment.getCcdCaseNumber())
            .put("ExternalReference", payment.getExternalReference())
            .build();
        auditRepository.trackEvent("CANCEL_CARD_PAYMENT", properties);
    }

    private static Specification findPayments(PaymentSearchCriteria searchCriteria) {
        return ((root, query, cb) -> getPredicate(root, cb, searchCriteria, query));
    }

    private static Predicate getPredicate(
        Root<Payment> root,
        CriteriaBuilder cb,
        PaymentSearchCriteria searchCriteria, CriteriaQuery<?> query) {
        List<Predicate> predicates = new ArrayList<>();

        Join<PaymentFeeLink, Payment> paymentJoin = root.join("payments", JoinType.LEFT);

        if (searchCriteria.getPaymentMethod() != null) {
            if(searchCriteria.getPaymentMethod().equalsIgnoreCase("all"))
            {
                Predicate predicateCheque = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("cheque").build());
                Predicate predicateCard = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("card").build());
                Predicate predicateDD = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("direct debit").build());
                Predicate predicateCash = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("cash").build());
                Predicate predicatePBA = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("payment by account").build());
                Predicate predicateAllPay = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("all pay").build());
                Predicate predicatePO = cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name("postal order").build());
                Predicate predicateFinal = cb.or(predicateCheque,predicateCard,predicateDD,predicateCash,predicatePBA,predicateAllPay,predicatePO);
                predicates.add(predicateFinal);
            }
            else
            {
                predicates.add(cb.equal(paymentJoin.get(PAYMENT_METHOD), PaymentMethod.paymentMethodWith().name(searchCriteria.getPaymentMethod()).build()));
            }

        }

        Expression<Date> dateUpdatedExpr = cb.function("date_trunc", Date.class, cb.literal("seconds"), paymentJoin.get("dateUpdated"));

        if (searchCriteria.getStartDate() != null && searchCriteria.getEndDate() != null) {
            predicates.add(cb.between(dateUpdatedExpr, searchCriteria.getStartDate(), searchCriteria.getEndDate()));
        } else if (searchCriteria.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(dateUpdatedExpr, searchCriteria.getStartDate()));
        } else if (searchCriteria.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(dateUpdatedExpr, searchCriteria.getEndDate()));
        }

        if (searchCriteria.getCcdCaseNumber() != null) {
            predicates.add(cb.equal(paymentJoin.get("ccdCaseNumber"), searchCriteria.getCcdCaseNumber()));
        }

        if (searchCriteria.getServiceType() != null) {
            predicates.add(cb.equal(paymentJoin.get("serviceType"), searchCriteria.getServiceType()));
        }

        if (searchCriteria.getPbaNumber() != null) {
            predicates.add(cb.equal(paymentJoin.get("pbaNumber"), searchCriteria.getPbaNumber()));
        }
        query.groupBy(root.get("id"));
        return cb.and(predicates.toArray(REF));
    }

    private Payment findSavedPayment(@NotNull String paymentReference) {
        return paymentRespository.findByReference(paymentReference).orElseThrow(PaymentNotFoundException::new);
    }

    private void fillTransientDetails(Payment payment, GovPayPayment govPayPayment) {
        BigDecimal amountInPounds = new BigDecimal(govPayPayment.getAmount());
        amountInPounds = amountInPounds.divide(new BigDecimal(100));
        payment.setAmount(amountInPounds);
        payment.setStatus(govPayPayment.getState().getStatus());
        payment.setPaymentStatus(PaymentStatus.paymentStatusWith().name(govPayPayment.getState().getStatus().toLowerCase()).build());
        payment.setFinished(govPayPayment.getState().getFinished());
        payment.setExternalReference(govPayPayment.getPaymentId());
        payment.setDescription(govPayPayment.getDescription());
        payment.setReturnUrl(govPayPayment.getReturnUrl());
        payment.setNextUrl(hrefFor(govPayPayment.getLinks().getNextUrl()));
        payment.setCancelUrl(hrefFor(govPayPayment.getLinks().getCancel()));
        payment.setRefundsUrl(hrefFor(govPayPayment.getLinks().getRefunds()));
    }

    private void fillTransientDetails(Payment payment, PciPalPayment pciPalPayment) {
        payment.setStatus(pciPalPayment.getState().getStatus());
    }

    private String hrefFor(Link url) {
        return url == null ? null : url.getHref();
    }

}
