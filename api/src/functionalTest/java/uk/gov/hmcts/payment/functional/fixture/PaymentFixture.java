package uk.gov.hmcts.payment.functional.fixture;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomUtils;
import org.joda.time.DateTime;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.PaymentRecordRequest;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import java.math.BigDecimal;

public class PaymentFixture {

    public static CardPaymentRequest aCardPaymentRequest(String amountString) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(Service.CMC)
            .currency(CurrencyCode.GBP)
            .siteId("AA101")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                .calculatedAmount(new BigDecimal(amountString))
                .code("FEE0001")
                .version("1")
                .build())
            )
            .build();
    }

    public static CardPaymentRequest cardPaymentRequestProbate(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("AA08")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CardPaymentRequest cardPaymentRequestall(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CardPaymentRequest.createCardPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("description")
            .caseReference("telRefNumber")
            .ccdCaseNumber(ccdCaseNumber)
            .service(service)
            .siteId("string")
            .currency(CurrencyCode.GBP)
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0002")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequest(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("AA101")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequestForCivil(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("AAA7")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequestForDivorce(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("AA08")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0002")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequestForIAC(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("BFA1")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequestForFPL(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("ABA3")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequestForProbate(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static CreditAccountPaymentRequest aPbaPaymentRequestForProbateForSuccessLiberataValidation(String amountString, Service service) {
        String ccdCaseNumber = "1111-CC12-" + RandomUtils.nextInt();
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .description("New passport application")
            .ccdCaseNumber(ccdCaseNumber)
            .caseReference("aCaseReference")
            .service(service)
            .currency(CurrencyCode.GBP)
            .siteId("ABA6")
            .customerReference("CUST101")
            .organisationName("ORG101")
            .accountNumber("PBAFUNC12345")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0226")
                    .version("1")
                    .build())
            )
            .build();
    }

    public static PaymentRecordRequest aBarPaymentRequest(String amountString) {
        return  PaymentRecordRequest.createPaymentRecordRequestDtoWith()
            .amount(new BigDecimal(amountString))
            .paymentMethod(PaymentMethodType.CASH)
            .reference("case_ref_123")
            .service(Service.DIGITAL_BAR)
            .currency(CurrencyCode.GBP)
            .giroSlipNo("12345")
            .reportedDateOffline(DateTime.now().toString())
            .siteId("AA07")
            .fees(Lists.newArrayList(
                FeeDto.feeDtoWith()
                    .calculatedAmount(new BigDecimal(amountString))
                    .code("FEE0001")
                    .version("1")
                    .build())
            )
            .build();
    }
}
