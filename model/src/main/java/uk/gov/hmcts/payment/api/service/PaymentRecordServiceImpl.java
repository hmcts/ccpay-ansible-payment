package uk.gov.hmcts.payment.api.service;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;

import java.util.Arrays;
import java.util.List;

@Service
public class PaymentRecordServiceImpl implements PaymentRecordService<PaymentFeeLink, String> {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentRecordServiceImpl.class);

    private final static String PAYMENT_CHANNEL_DIGITAL_BAR = "digital bar";
    private final static String PAYMENT_METHOD_CASH = "cash";
    private final static String PAYMENT_STATUS_SUCCESS = "success";
    private final static String PAYMENT_STATUS_PENDING = "pending";

    private final PaymentFeeLinkRepository paymentFeeLinkRepository;
    private final PaymentStatusRepository paymentStatusRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ReferenceUtil referenceUtil;
    private final UserIdSupplier userIdSupplier;
    private final ServiceIdSupplier serviceIdSupplier;

    private final OrderCaseUtil orderCaseUtil;

    @Autowired
    public PaymentRecordServiceImpl(PaymentFeeLinkRepository paymentFeeLinkRepository,

                                    PaymentChannelRepository paymentChannelRepository,
                                    PaymentMethodRepository paymentMethodRepository,
                                    PaymentStatusRepository paymentStatusRepository,
                                    ReferenceUtil referenceUtil,
                                    UserIdSupplier userIdSupplier, ServiceIdSupplier serviceIdSupplier, OrderCaseUtil orderCaseUtil) {
        this.paymentFeeLinkRepository = paymentFeeLinkRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentStatusRepository = paymentStatusRepository;
        this.referenceUtil = referenceUtil;
        this.userIdSupplier = userIdSupplier;
        this.serviceIdSupplier = serviceIdSupplier;
        this.orderCaseUtil = orderCaseUtil;
    }


    @Override
    public PaymentFeeLink recordPayment(Payment recordPayment, List<PaymentFee> fees, String paymentGroupReference) throws CheckDigitException {
        LOG.debug("Record payment with PaymentGroupReference: {}", paymentGroupReference);

        PaymentFeeLink paymentFeeLink = populatePaymentDetails(recordPayment, fees, paymentGroupReference);
        paymentFeeLink.getPayments().get(0).setPaymentLink(paymentFeeLink);

        return  paymentFeeLinkRepository.save(orderCaseUtil.enhanceWithOrderCaseDetails(paymentFeeLink, recordPayment));
    }

    private PaymentFeeLink populatePaymentDetails(Payment payment, List<PaymentFee> fees, String paymentGroupRef) throws CheckDigitException {

        return PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference(paymentGroupRef)
            .payments(Arrays.asList(Payment.paymentWith()
                .amount(payment.getAmount())
                .caseReference(payment.getCaseReference())
                .currency(payment.getCurrency())
                .siteId(payment.getSiteId())
                .paymentProvider(payment.getPaymentProvider())
                .externalReference(payment.getExternalReference())
                .giroSlipNo(payment.getGiroSlipNo())
                .serviceType(payment.getServiceType())
                .s2sServiceName(serviceIdSupplier.get())
                .userId(userIdSupplier.get())
                .reportedDateOffline(payment.getReportedDateOffline())
                .paymentChannel(paymentChannelRepository.findByNameOrThrow(PAYMENT_CHANNEL_DIGITAL_BAR))
                .paymentStatus(paymentStatusRepository.findByNameOrThrow(getPaymentStatus(payment.getPaymentMethod().getName())))
                .paymentMethod(paymentMethodRepository.findByNameOrThrow(payment.getPaymentMethod().getName()))
                .reference(referenceUtil.getNext("RC"))
                .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                    .status(paymentStatusRepository.findByNameOrThrow(PAYMENT_STATUS_PENDING).getName())
                    .build()))
                .build()))
            .fees(fees)
            .build();
    }

    private String getPaymentStatus(String paymentMethod) {
        switch (paymentMethod) {
            case "card":
            case "cash":
                return PAYMENT_STATUS_SUCCESS;
            case "cheque":
            case "postal order":
                return PAYMENT_STATUS_PENDING;
            default:
                throw new PaymentException("Invalid payment method: " + paymentMethod);

        }
    }
}
