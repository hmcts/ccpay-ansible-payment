package uk.gov.hmcts.payment.api.v1.model.exceptions;

public class NoServiceFoundException extends RuntimeException{

    public NoServiceFoundException(String message) {
        super(message);
    }
}
