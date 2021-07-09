package uk.gov.hmcts.payment.casepaymentorders.client.exceptions;

public class CpoInternalServerErrorException extends CpoException {

    public CpoInternalServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
