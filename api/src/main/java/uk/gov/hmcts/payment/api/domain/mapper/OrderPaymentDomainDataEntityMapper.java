package uk.gov.hmcts.payment.api.domain.mapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.domain.model.Error;
import uk.gov.hmcts.payment.api.domain.model.OrderPaymentBo;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OrderPaymentDomainDataEntityMapper {

    private final static String PAYMENT_CHANNEL_ONLINE = "online";

    private final static String PAYMENT_METHOD_BY_ACCOUNT = "payment by account";

    @Autowired
    private PaymentFeeLinkRepository paymentFeeLinkRepository;

    @Autowired
    private PaymentStatusRepository paymentStatusRepository;

    @Autowired
    private PaymentChannelRepository paymentChannelRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private Payment2Repository paymentRespository;

    @Autowired
    private ServiceIdSupplier serviceIdSupplier;

    @Autowired
    private UserIdSupplier userIdSupplier;

    public Payment toEntity(OrderPaymentBo paymentBo, PaymentFeeLink order) {
        CaseDetails caseDetails = order.getCaseDetails().stream().findAny().orElse(new CaseDetails());

        return Payment.paymentWith()
            .userId(userIdSupplier.get())
            .s2sServiceName(serviceIdSupplier.get())
            .paymentChannel(paymentChannelRepository.findByNameOrThrow(PAYMENT_CHANNEL_ONLINE))
            .paymentMethod(paymentMethodRepository.findByNameOrThrow(PAYMENT_METHOD_BY_ACCOUNT))
            .paymentStatus(paymentStatusRepository.findByNameOrThrow(paymentBo.getStatus()))
            .reference(paymentBo.getPaymentReference())
            .status(paymentBo.getStatus())
            .amount(paymentBo.getAmount())
            .pbaNumber(paymentBo.getAccountNumber())
            .currency(paymentBo.getCurrency().getCode())
            .customerReference(paymentBo.getCustomerReference())
            .caseReference(StringUtils.isNotEmpty(caseDetails.getCaseReference()) ? caseDetails.getCaseReference() : null)
            .ccdCaseNumber(StringUtils.isNotEmpty(caseDetails.getCcdCaseNumber()) ? caseDetails.getCcdCaseNumber() : null)
            .statusHistories(paymentBo.getStatusHistories() == null ? Arrays.asList(StatusHistory.statusHistoryWith()
                .status(paymentStatusRepository.findByNameOrThrow(paymentBo.getStatus()).getName())
                .build())
                : paymentBo.getStatusHistories())
            .build();
    }

    public OrderPaymentBo toDomain(Payment payment) {
        AtomicReference<Error> error = new AtomicReference<>();

        if (Optional.ofNullable(payment.getStatusHistories()).isPresent()) {
            Optional<StatusHistory> statusHistoryOptional =
                payment.getStatusHistories().stream()
                    .filter(statusHistory -> statusHistory.getErrorCode() != null).findFirst();

            statusHistoryOptional.ifPresent(statusHistory -> {
                error.set(Error.errorWith().errorCode(statusHistory.getErrorCode()).errorMessage(statusHistory.getMessage()).build());
            });
        }

        return OrderPaymentBo.orderPaymentBoWith()
            .paymentReference(payment.getReference())
            .status(payment.getPaymentStatus().getName())
            .error(error.get() != null ? error.get() : null)
            .dateCreated(payment.getDateCreated().toString())
            .build();
    }
}
