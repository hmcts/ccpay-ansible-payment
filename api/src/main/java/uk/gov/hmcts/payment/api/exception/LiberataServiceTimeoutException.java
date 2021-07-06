package uk.gov.hmcts.payment.api.exception;

public class LiberataServiceTimeoutException extends RuntimeException {
    public LiberataServiceTimeoutException(String message) {
        super(message);
    }
}
