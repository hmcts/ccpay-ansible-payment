package uk.gov.hmcts.payment.api.v1.model.exceptions;

public class GatewayTimeoutException extends RuntimeException{

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
