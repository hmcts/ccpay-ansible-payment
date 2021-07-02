package uk.gov.hmcts.payment.casepaymentorders.client.exceptions;

public class CpoBadRequestException extends CpoException {

    public CpoBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
