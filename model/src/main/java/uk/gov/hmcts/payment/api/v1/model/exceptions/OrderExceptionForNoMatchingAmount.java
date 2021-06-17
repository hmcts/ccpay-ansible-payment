package uk.gov.hmcts.payment.api.v1.model.exceptions;

public class OrderExceptionForNoMatchingAmount extends PaymentException {

    public OrderExceptionForNoMatchingAmount() {}

    public OrderExceptionForNoMatchingAmount(String message) {
        super(message);
    }

}
