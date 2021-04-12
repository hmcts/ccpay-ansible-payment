package uk.gov.hmcts.payment.api.contract;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class UpdatePaymentRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeClass
    public static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterClass
    public static void close() {
        validatorFactory.close();
    }


    @Test
    public void testOnlyCCDNumberOrCaseReferenceRequired(){
        UpdatePaymentRequest updatePaymentRequest = new UpdatePaymentRequest();
        updatePaymentRequest.setCaseReference("case-reference");
        updatePaymentRequest.setCcdCaseNumber("ccd-number");
        Set<ConstraintViolation<UpdatePaymentRequest>> violations = validator.validate(updatePaymentRequest);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Either ccdCaseNumber or caseReference is required, and cannot be empty.")){
                    assertEquals("Either ccdCaseNumber or caseReference is required, and cannot be empty.",v.getMessage());
                }
            }
        );

    }
}
