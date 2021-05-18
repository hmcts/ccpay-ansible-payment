package uk.gov.hmcts.payment.api.exception;

public class CaseDetailsNotFoundException extends RuntimeException{
    public CaseDetailsNotFoundException(String message) {super(message);}
}
