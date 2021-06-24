package uk.gov.hmcts.payment.api.contract;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    public void testValidateSiteIdForCivil(){
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setService(Service.CMC);
        request.setSiteId("invalid-site-id");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid Site ID (URN) provided for Civil. Accepted values are AAA7")){
                    assertEquals("Invalid Site ID (URN) provided for Civil. Accepted values are AAA7",v.getMessage());
                }
            }
        );
    }

    @Test
    public void testValidateSiteIdForIac(){
        CreditAccountPaymentRequest request = new CreditAccountPaymentRequest();
        request.setService(Service.IAC);
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
        request.setService(Service.FPL);
        request.setSiteId("invalid-site-id");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(request);
        violations.stream().forEach(v->{
                if(v.getMessage().equals("Invalid Site ID (URN) provided for FPL. Accepted values are ABA3")){
                    assertEquals("Invalid Site ID (URN) provided for FPL. Accepted values are ABA3",v.getMessage());
                }
            }
        );
    }

    @Test
    public void test_valid_credit_account_payment_request() {
        CreditAccountPaymentRequest creditAccountPaymentRequest  = credit_account_payment_request("1111222233334444");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(creditAccountPaymentRequest);
        assertEquals(0,violations.size()); //No Violations should be present as this is a valid payload
    }

    @Test
    public void test_invalid_credit_account_payment_request() {
        //Data Length less than 16
        CreditAccountPaymentRequest creditAccountPaymentRequest  = credit_account_payment_request("111122223333444");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations = validator.validate(creditAccountPaymentRequest);
        assertEquals(1,violations.size());
        violations.stream().forEach(v->{
                assertEquals("ccd_case_number should be 16 digit and numeric",v.getMessage());
            }
        );

        //Data Length > 16
        CreditAccountPaymentRequest creditAccountPaymentRequest1  = credit_account_payment_request("11112222333344444");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations1 = validator.validate(creditAccountPaymentRequest1);
        assertEquals(1,violations1.size());
        violations.stream().forEach(v->{
                assertEquals("ccd_case_number should be 16 digit and numeric",v.getMessage());
            }
        );

        //Data with special charectars
        CreditAccountPaymentRequest creditAccountPaymentRequest2  = credit_account_payment_request("111-222-333-4444");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations2 = validator.validate(creditAccountPaymentRequest2);
        assertEquals(1,violations2.size());
        violations.stream().forEach(v->{
                assertEquals("ccd_case_number should be 16 digit and numeric",v.getMessage());
            }
        );

        //Data with invalid charectar.....
        CreditAccountPaymentRequest creditAccountPaymentRequest3  = credit_account_payment_request("111 222233334444");
        Set<ConstraintViolation<CreditAccountPaymentRequest>> violations3 = validator.validate(creditAccountPaymentRequest3);
        assertEquals(1,violations3.size());
        violations.stream().forEach(v->{
                assertEquals("ccd_case_number should be 16 digit and numeric",v.getMessage());
            }
        );
    }

    private static CreditAccountPaymentRequest credit_account_payment_request(final String ccdCaseNumber) {
        FeeDto feeDto = FeeDto
            .feeDtoWith()
            .code("Code")
            .version("Version")
            .volume(1)
            .calculatedAmount(BigDecimal.valueOf(10.12))
            .netAmount(BigDecimal.valueOf(10.12))
            .build();
        CreditAccountPaymentRequest creditAccountPaymentRequest = CreditAccountPaymentRequest
            .createCreditAccountPaymentRequestDtoWith()
            .amount(BigDecimal.valueOf(10.12))
            .ccdCaseNumber(ccdCaseNumber)
            .siteId("AA06")
            .description("Description")
            .caseReference("Reference")
            .service(Service.CMC)
            .customerReference("CustomerReference")
            .accountNumber("AccountNumber")
            .organisationName("OrganisationName")
            .currency(CurrencyCode.GBP)
            .fees(List.of(feeDto)) //Add the Fee Dto was created earlier....
            .build();
        return creditAccountPaymentRequest;
    }
}
