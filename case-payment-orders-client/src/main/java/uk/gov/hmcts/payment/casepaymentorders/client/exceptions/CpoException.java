package uk.gov.hmcts.payment.casepaymentorders.client.exceptions;

public class CpoException extends RuntimeException {

    public CpoException(String message, Throwable cause) {
        super(message, cause);
    }
}
