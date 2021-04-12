package uk.gov.hmcts.payment.api.contract.util;

import org.junit.*;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.AssertFalse;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LanguageTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    CardPaymentRequest validLanguageRequest;
    CardPaymentRequest emptyLanguageRequest;
    CardPaymentRequest invalidLanguageRequest;
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
    public void testValidLanguageCode(){
        validLanguageRequest = new CardPaymentRequest();
        validLanguageRequest.setLanguage("cy");
        validLanguageRequest.setCcdCaseNumber("ccd-number");
        validLanguageRequest.setSiteId("site-id");
        validLanguageRequest.setDescription("description");
        Set<ConstraintViolation<CardPaymentRequest>> violations = validator.validate(validLanguageRequest);
        assertFalse(violations.isEmpty());
    }


    @Test
    public void testEmtpyLanguageCode(){
        emptyLanguageRequest = new CardPaymentRequest();
        emptyLanguageRequest.setLanguage("");
        emptyLanguageRequest.setCcdCaseNumber("ccd-number");
        emptyLanguageRequest.setSiteId("site-id");
        emptyLanguageRequest.setDescription("description");
        Set<ConstraintViolation<CardPaymentRequest>> violations = validator.validate(emptyLanguageRequest);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid value for language attribute.")){
                    assertEquals("Invalid value for language attribute.",v.getMessage());
                }
            }
        );
    }

    @Test
    public void testInvalidLanguageCode(){
        invalidLanguageRequest = new CardPaymentRequest();
        invalidLanguageRequest.setLanguage("invalid");
        invalidLanguageRequest.setCcdCaseNumber("ccd-number");
        invalidLanguageRequest.setSiteId("site-id");
        invalidLanguageRequest.setDescription("description");
        Set<ConstraintViolation<CardPaymentRequest>> violations = validator.validate(invalidLanguageRequest);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid value for language attribute.")){
                    assertEquals("Invalid value for language attribute.",v.getMessage());
                }
            }
        );
    }

}
