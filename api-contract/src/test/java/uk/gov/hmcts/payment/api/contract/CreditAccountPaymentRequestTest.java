package uk.gov.hmcts.payment.api.contract;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CreditAccountPaymentRequestTest {
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
    public void testAmountFractionInCreditAccountPaymentRequest(){
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setAmount(BigDecimal.valueOf(100.1234));
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Payment amount cannot have more than 2 decimal places")){
                    assertEquals("Payment amount cannot have more than 2 decimal places",v.getMessage());
                }
            }
        );
    }

    @Test
    public void testNegativeAmountInCreditAccountPaymentRequest(){
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setAmount(BigDecimal.valueOf(-100.12));
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("must be greater than 0")){
                    assertEquals("must be greater than 0",v.getMessage());
                }
            }
        );
    }

    @Test
    public void testValidateSiteIdForUnspecCmc(){
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setService("CMC");
        request.setSiteId("invalid-site-id");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid Site ID (URN) provided for UNSPEC CMC. Accepted values are AAA7")){
                    assertEquals("Invalid Site ID (URN) provided for UNSPEC CMC. Accepted values are AAA7",v.getMessage());
                }
            }
        );
    }

    @Test
    public void testValidateSiteIdForIac(){
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setService("IAC");
        request.setSiteId("invalid-site-id");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid Site ID (URN) provided for IAC. Accepted values are BFA1")){
                    assertEquals("Invalid Site ID (URN) provided for IAC. Accepted values are BFA1",v.getMessage());
                }
            }
        );
    }

    @Test
    public void testValidateSiteIdForFpl() {
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setService("IAC");
        request.setSiteId("invalid-site-id");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid Site ID (URN) provided for FPL. Accepted values are ABA3")){
                    assertEquals("Invalid Site ID (URN) provided for FPL. Accepted values are ABA3",v.getMessage());
                }
            }
        );
    }




}
