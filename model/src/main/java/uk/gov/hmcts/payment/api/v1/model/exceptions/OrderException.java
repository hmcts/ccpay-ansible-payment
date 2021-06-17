package uk.gov.hmcts.payment.api.v1.model.exceptions;

public class OrderException extends PaymentException {

    public OrderException() {}

    public OrderException(String message) {
        super(message);
    }

}
