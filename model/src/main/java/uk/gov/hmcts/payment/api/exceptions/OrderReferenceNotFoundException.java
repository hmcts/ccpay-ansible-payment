package uk.gov.hmcts.payment.api.exceptions;

public class OrderReferenceNotFoundException extends RuntimeException {
    public OrderReferenceNotFoundException(String message) {
        super(message);
    }
}
