package uk.gov.hmcts.payment.api.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.payment.api.audit.AuditRepository;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.Reference;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.*;


@Service
public class PaymentServiceImpl implements PaymentService<PaymentFeeLink, String> {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final PaymentStatus SUCCESS = new PaymentStatus("success", "success");
    private static final PaymentStatus FAILED = new PaymentStatus("failed", "failed");
    private static final PaymentStatus CANCELLED = new PaymentStatus("cancelled", "cancelled");
    private static final PaymentStatus ERROR = new PaymentStatus("error", "error");
    private static final String PCI_PAL = "pci pal";

    private final Payment2Repository paymentRepository;
    private final DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;
    private final CallbackService callbackService;
    private final PaymentStatusRepository paymentStatusRepository;
    private final TelephonyRepository telephonyRepository;
    private final AuditRepository paymentAuditRepository;
    private final FeePayApportionService feePayApportionService;
    private final FeePayApportionRepository feePayApportionRepository;
    private final LaunchDarklyFeatureToggler featureToggler;
    private final PaymentFeeRepository paymentFeeRepository;

    @Value("${callback.payments.cutoff.time.in.minutes:2}")
    private int paymentsCutOffTime;

    @Autowired
    public PaymentServiceImpl(@Qualifier("loggingPaymentService") DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService,
                              Payment2Repository paymentRepository, CallbackService callbackService, PaymentStatusRepository paymentStatusRepository,
                              TelephonyRepository telephonyRepository, AuditRepository paymentAuditRepository,
                              FeePayApportionService feePayApportionService,
                              FeePayApportionRepository feePayApportionRepository,
                              PaymentFeeRepository paymentFeeRepository,
                              LaunchDarklyFeatureToggler featureToggler) {
        this.paymentRepository = paymentRepository;
        this.delegatingPaymentService = delegatingPaymentService;
        this.callbackService = callbackService;
        this.paymentStatusRepository = paymentStatusRepository;
        this.telephonyRepository = telephonyRepository;
        this.paymentAuditRepository = paymentAuditRepository;
        this.feePayApportionService = feePayApportionService;
        this.feePayApportionRepository = feePayApportionRepository;
        this.paymentFeeRepository = paymentFeeRepository;
        this.featureToggler = featureToggler;
    }

    @Override
    public PaymentFeeLink retrieve(String reference) {
        Payment payment = findSavedPayment(reference);

        return payment.getPaymentLink();
    }

    @Override
    @Transactional
    public void updateTelephonyPaymentStatus(String paymentReference, String status, String payload) {
        Payment payment = paymentRepository.findByReferenceAndPaymentProvider(paymentReference,
            PaymentProvider.paymentProviderWith().name(PCI_PAL).build()).orElseThrow(PaymentNotFoundException::new);
        if(payment.getPaymentStatus() != null && !payment.getPaymentStatus().getName().equalsIgnoreCase(PaymentStatus.SUCCESS.getName())){
            payment.setPaymentStatus(paymentStatusRepository.findByNameOrThrow(status));

            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .status(status)
                .build()));
            if (payment.getServiceCallbackUrl() != null) {
                callbackService.callback(payment.getPaymentLink(), payment);
            }
            telephonyRepository.save(TelephonyCallback.telephonyCallbackWith().paymentReference(paymentReference).payload(payload).build());
            //1. Update Fee Amount Due as Payment Status received from PCI PAL as SUCCESS
            //2. Rollback Fees already Apportioned for Payments in FAILED status based on launch darkly feature flag
            boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
            LOG.info("ApportionFeature Flag Value in UserAwareDelegatingPaymentService : {}", apportionFeature);
            if(apportionFeature) {
                if(status.equalsIgnoreCase("success")) {
                    LOG.info("Update Fee Amount Due as Payment Status received from GovPAY as SUCCESS!!!");
                    feePayApportionService.updateFeeAmountDue(payment);
                }
            }
        }else {
            Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("PaymentReference", paymentReference)
                .put("RequestedStatus", status)
                .put("Payload", payload)
                .put("ExistingStatus", payment.getPaymentStatus().getName())
                .build();
            paymentAuditRepository.trackEvent("DUPLICATE_STATUS_UPDATE", properties);
        }
    }

    @Override
    public List<Payment> getPayments(Date atStartOfDay, Date atEndOfDay) {
        return paymentRepository.findAllByDateCreatedBetween(atStartOfDay, atEndOfDay).orElse(Collections.EMPTY_LIST);
    }

    @Override
    public List<Reference> listInitiatedStatusPaymentsReferences() {
        Date targetTime = DateUtils.addMinutes(new Date(), -1 * paymentsCutOffTime);
        return paymentRepository.findReferencesByPaymentProviderAndPaymentStatusNotInAndDateCreatedLessThan(
            PaymentProvider.GOV_PAY,
            Lists.newArrayList(SUCCESS, FAILED, ERROR, CANCELLED), targetTime
        );
    }

    @Override
    public List<PaymentFeeLink> search(PaymentSearchCriteria searchCriteria) {
        return delegatingPaymentService.search(searchCriteria);
    }

    @Override
    public List<FeePayApportion> findByPaymentId(Integer paymentId)
    {
        return feePayApportionRepository.findByPaymentId(paymentId).orElse(Collections.EMPTY_LIST);
    }

    @Override
    public void getApportionedDetails(List<PaymentFee> fees, List<FeePayApportion> feePayApportionList) {
        LOG.info("Getting Apportionment Details!!!");
        for (FeePayApportion feePayApportion : feePayApportionList)
        {
            Optional<PaymentFee> apportionedFee = paymentFeeRepository.findById(feePayApportion.getFeeId());
            if(apportionedFee.isPresent())
            {
                LOG.info("Apportioned fee is present");
                PaymentFee fee = apportionedFee.get();
                if(feePayApportion.getApportionAmount() != null) {
                    LOG.info("Apportioned Amount is available!!!");
                    BigDecimal allocatedAmount = feePayApportion.getApportionAmount()
                        .add(feePayApportion.getCallSurplusAmount() != null
                            ? feePayApportion.getCallSurplusAmount()
                            : BigDecimal.valueOf(0));
                    LOG.info("Allocated amount in PaymentController: {}", allocatedAmount);
                    fee.setAllocatedAmount(allocatedAmount);
                    fee.setDateApportioned(feePayApportion.getDateCreated());
                }
                fees.add(fee);
            }
        }
    }

    private Payment findSavedPayment(@NotNull String paymentReference) {
        return paymentRepository.findByReference(paymentReference).orElseThrow(PaymentNotFoundException::new);
    }
}
