package uk.gov.hmcts.payment.api.v1.model.exceptions;

public class OrderExceptionForNoAmountDue extends PaymentException {

    public OrderExceptionForNoAmountDue() {}

    public OrderExceptionForNoAmountDue(String message) {
        super(message);
    }

}
