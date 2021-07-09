package uk.gov.hmcts.payment.casepaymentorders.client.exceptions;

public class CpoClientException extends CpoException {
    private final int status;

    public CpoClientException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
