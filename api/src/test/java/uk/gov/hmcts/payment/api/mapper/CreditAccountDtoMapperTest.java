package uk.gov.hmcts.payment.api.mapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentAllocation;
import uk.gov.hmcts.payment.api.model.PaymentAllocationStatus;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.Remission;
import uk.gov.hmcts.payment.api.model.StatusHistory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CreditAccountDtoMapperTest {
    @Mock
    LaunchDarklyFeatureToggler featureToggler;

    @InjectMocks
    CreditAccountDtoMapper creditAccountDtoMapper = new CreditAccountDtoMapper();

    List<Payment> payments = new ArrayList<Payment>();

    Payment payment1;

    PaymentFeeLink feeLink;

    @Before
    public void initiate(){
        List<PaymentAllocation> paymentAllocations1 = new ArrayList<PaymentAllocation>();
        PaymentAllocation allocation1 = PaymentAllocation.paymentAllocationWith()
            .receivingOffice("receiving-office")
            .unidentifiedReason("reason")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Transferred").build()).build();
        paymentAllocations1.add(allocation1);
        StatusHistory statusHistory = StatusHistory.statusHistoryWith()
                                                .status("success")
                                                .externalStatus("external-status")
                                                .errorCode("")
                                                .message("")
                                                .dateCreated(new Date(2021,1,1))
                                                .dateUpdated(new Date(2021,1,10))
                                                .build();
        List<StatusHistory> statusHistories = new ArrayList<StatusHistory>();
        statusHistories.add(statusHistory);
        payment1 = Payment.paymentWith()
            .siteId("siteId")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .serviceType("service-type")
            .caseReference("case-reference")
            .reference("RC-1612-3710-5335-6484")
            .ccdCaseNumber("ccd-case-number")
            .bankedDate(new Date(2021,1,1))
            .documentControlNumber("document-control-number")
            .paymentMethod(PaymentMethod.paymentMethodWith().name("pay-method").build())
            .amount(new BigDecimal("100.00"))
            .paymentStatus(PaymentStatus.SUCCESS)
            .dateCreated(new Date(2020,10,1))
            .currency("GBP")
            .statusHistories(statusHistories)
            .id(1).paymentAllocation(paymentAllocations1).build();
        payments.add(payment1);
        PaymentFee fee = PaymentFee.feeWith().feeAmount(new BigDecimal("100.00")).ccdCaseNumber("ccd-case-number").calculatedAmount(new BigDecimal("100.00")).build();
        List<PaymentFee> paymentFees = new ArrayList<PaymentFee>();
        paymentFees.add(fee);
        Remission remission = Remission.remissionWith()
            .hwfAmount(new BigDecimal("10.00"))
            .build();
        List<Remission> remissionList = new ArrayList<Remission>();
        remissionList.add(remission);
        feeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("payment-reference")
            .fees(paymentFees)
            .payments(payments)
            .remissions(remissionList)
            .build();
        payment1.setPaymentLink(feeLink);

    }


    @Test
    public void testToPaymentRequest(){
        CreditAccountPaymentRequest creditAccountPayment = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
                                                                .amount(new BigDecimal("100.00"))
                                                                .description("description")
                                                                .service("CMC")
                                                                .ccdCaseNumber("1234567890123")
                                                                .currency(CurrencyCode.GBP)
                                                                .siteId("site-id").build();
        Payment payment = creditAccountDtoMapper.toPaymentRequest(creditAccountPayment);
        assertEquals("1234567890123",payment.getCcdCaseNumber());
        assertEquals("site-id",payment.getSiteId());
    }

    @Test
    public void testToRetrievePaymentGroupReferenceResponse(){
        PaymentGroupDto payment = creditAccountDtoMapper.toRetrievePaymentGroupReferenceResponse(feeLink);
        assertEquals("payment-reference",payment.getPaymentGroupReference());
    }

    @Test
    public void testToRetrievePaymentResponse(){
        PaymentFee fee = PaymentFee.feeWith().feeAmount(new BigDecimal("100.00")).ccdCaseNumber("ccd-case-number").calculatedAmount(new BigDecimal("100.00")).build();
        List<PaymentFee> paymentFees = new ArrayList<PaymentFee>();
        paymentFees.add(fee);
        PaymentDto paymentDto = creditAccountDtoMapper.toRetrievePaymentResponse(payment1,paymentFees);
        assertEquals(new BigDecimal("100.00"),paymentDto.getAmount());
        assertEquals("ccd-case-number",paymentDto.getCcdCaseNumber());
    }

    @Test
    public void testToCreateCreditAccountPaymentResponse(){
        PaymentDto paymentDto = creditAccountDtoMapper.toCreateCreditAccountPaymentResponse(feeLink);
        assertEquals("RC-1612-3710-5335-6484",paymentDto.getReference());
    }

    @Test
    public void testToRetrievePaymentStatusResponse(){
        PaymentDto paymentDto = creditAccountDtoMapper.toRetrievePaymentStatusResponse(payment1);
        assertEquals(new BigDecimal("100.00"),paymentDto.getAmount());
        assertEquals("RC-1612-3710-5335-6484",paymentDto.getReference());
    }

    @Test
    public void testToFee(){
        FeeDto feeDto = FeeDto.feeDtoWith()
                        .calculatedAmount(new BigDecimal(("100.00")))
                        .code("FEE123")
                        .version("1")
                        .volume(1)
                        .feeAmount(new BigDecimal("50.00"))
                        .netAmount(new BigDecimal("50.00"))
                        .dateCreated(new Date(2020,10,1))
                        .ccdCaseNumber("123456789012345")
                        .build();
        when(featureToggler.getBooleanValue(Mockito.any(String.class),Mockito.anyBoolean())).thenReturn(true);
        PaymentFee paymentFee = creditAccountDtoMapper.toFee(feeDto);
        assertEquals("123456789012345",paymentFee.getCcdCaseNumber());
        assertEquals("FEE123",paymentFee.getCode());

    }
}
