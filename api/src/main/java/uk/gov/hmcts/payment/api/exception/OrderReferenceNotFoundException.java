package uk.gov.hmcts.payment.api.exception;

public class OrderReferenceNotFoundException extends RuntimeException {
    public OrderReferenceNotFoundException(String message) {
        super(message);
    }
}
