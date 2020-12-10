package uk.gov.hmcts.payment.api.v1.controllers;

import java.lang.reflect.Method;
import lombok.SneakyThrows;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.v1.contract.PaymentOldDto;
import uk.gov.hmcts.payment.api.v1.model.PaymentOld;

@Component
public class PaymentDtoFactory {

    public PaymentOldDto toDto(PaymentOld paymentOld) {
        return PaymentOldDto.paymentDtoWith()
                .id(paymentOld.getId().toString())
                .amount(paymentOld.getAmount())
                .state(toStateDto(paymentOld.getStatus(), paymentOld.getFinished()))
                .description(paymentOld.getDescription())
                .reference(paymentOld.getReference())
                .dateCreated(paymentOld.getDateCreated())
                .links(new PaymentOldDto.LinksDto(
                        paymentOld.getNextUrl() == null ? null : new PaymentOldDto.LinkDto(paymentOld.getNextUrl(), "GET"),
                        paymentOld.getCancelUrl() == null ? null : cancellationLink(paymentOld.getUserId(), paymentOld.getId())
                ))
                .build();
    }

    private PaymentOldDto.StateDto toStateDto(String status, Boolean finished) {
        return new PaymentOldDto.StateDto(status, finished);
    }

    @SneakyThrows(NoSuchMethodException.class)
    private PaymentOldDto.LinkDto cancellationLink(String userId, Integer paymentId) {
        Method method = PaymentOldController.class.getMethod("cancel", String.class, Integer.class);
        return new PaymentOldDto.LinkDto(WebMvcLinkBuilder.linkTo(method, userId, paymentId).toString(), "POST");
    }

}
