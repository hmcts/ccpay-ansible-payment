package uk.gov.hmcts.payment.api.mapper;

import org.ff4j.FF4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.fees2.register.data.model.Fee;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.reports.FeesService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaymentDtoMapperTest {

    @Mock
    FeesService feesService;

    @Mock
    LaunchDarklyFeatureToggler featureToggler;

    @Mock
    FF4j ff4j;

    @InjectMocks
    PaymentDtoMapper paymentDtoMapper = new PaymentDtoMapper();

    PaymentFeeLink paymentFeeLink;

    Payment payment1;

    List<PaymentFee> paymentFees;

    PaymentAllocation allocation1;

    @Before
    public void initiate(){
        List<Payment> payments = new ArrayList<Payment>();
        List<PaymentAllocation> paymentAllocations1 = new ArrayList<PaymentAllocation>();
        allocation1 = PaymentAllocation.paymentAllocationWith()
            .receivingOffice("receiving-office")
            .id(1)
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
        PaymentFee fee = PaymentFee.feeWith()
            .feeAmount(new BigDecimal("100.00"))
            .ccdCaseNumber("ccd-case-number")
            .calculatedAmount(new BigDecimal("100.00"))
            .code("FEE123")
            .version("1")
            .volume(1)
            .build();
        paymentFees = new ArrayList<PaymentFee>();
        paymentFees.add(fee);
        payment1 = Payment.paymentWith()
            .siteId("siteId")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("bulk scan").build())
            .serviceType("service-type")
            .caseReference("case-reference")
            .reference("RC-1612-3710-5335-6484")
            .ccdCaseNumber("ccd-case-number")
            .status("success")
            .bankedDate(new Date(2021,1,1))
            .documentControlNumber("document-control-number")
            .paymentMethod(PaymentMethod.paymentMethodWith().name("pay-method").build())
            .amount(new BigDecimal("100.00"))
            .paymentStatus(PaymentStatus.SUCCESS)
            .dateCreated(new Date(2020,10,1))
            .paymentAllocation(paymentAllocations1)
            .currency("GBP")
            .statusHistories(statusHistories)
            .id(1).build();
        payments.add(payment1);
        paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
                            .paymentReference("group-reference")
                            .dateCreated(new Date(2021,1,1))
                            .fees(paymentFees)
                            .payments(payments).build();
        payment1.setPaymentLink(paymentFeeLink);
    }

    @Test
    public void testToCardPaymentDto(){
        PaymentDto paymentDto = paymentDtoMapper.toCardPaymentDto(paymentFeeLink);
        assertEquals("RC-1612-3710-5335-6484",paymentDto.getReference());
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
    }

    @Test
    public void testToCardPaymentDtoWithPaymentGroupReference(){
        PaymentDto paymentDto = paymentDtoMapper.toCardPaymentDto(payment1,"group-reference");
        assertEquals("RC-1612-3710-5335-6484",paymentDto.getReference());
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
    }

    @Test
    public void testToBulkScanPaymentDto(){
        PaymentDto paymentDto = paymentDtoMapper.toBulkScanPaymentDto(payment1,"group-reference");
        assertEquals("RC-1612-3710-5335-6484",paymentDto.getReference());
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
    }

    @Test
    public void testToBulkScanPaymentStrategicDto(){
        PaymentDto paymentDto = paymentDtoMapper.toBulkScanPaymentStrategicDto(payment1,"group-reference");
        assertEquals("RC-1612-3710-5335-6484",paymentDto.getReference());
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
    }

    @Test
    public void testToPciPalCardPaymentDto(){
        PaymentDto paymentDto = paymentDtoMapper.toPciPalCardPaymentDto(paymentFeeLink,"http://hmcts.internal");
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("http://hmcts.internal",paymentDto.getLinks().getNextUrl().getHref());
    }

    @Test
    public void testToPciPalCardPaymentDtoWithPaymentLink(){
        PaymentDto paymentDto = paymentDtoMapper.toPciPalCardPaymentDto(paymentFeeLink,payment1, "http://hmcts.internal");
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("http://hmcts.internal",paymentDto.getLinks().getNextUrl().getHref());
    }

    @Test
    public void testToResponseDto(){
        PaymentDto paymentDto = paymentDtoMapper.toResponseDto(paymentFeeLink,payment1);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("service-type",paymentDto.getServiceName());
    }

    @Test
    public void testToRetrieveCardPaymentResponseDto(){
        PaymentDto paymentDto = paymentDtoMapper.toRetrieveCardPaymentResponseDto(paymentFeeLink);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("service-type",paymentDto.getServiceName());
    }

    @Test
    public void testToRetrievePaymentStatusesDto(){
        PaymentDto paymentDto = paymentDtoMapper.toRetrievePaymentStatusesDto(paymentFeeLink);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("100.00",paymentDto.getAmount().toString());
    }

    @Test
    public void testToPaymentStatusesDto(){
        PaymentDto paymentDto = paymentDtoMapper.toPaymentStatusesDto(payment1);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("100.00",paymentDto.getAmount().toString());

    }

    @Test
    public void testToGetPaymentResponseDtos(){
        PaymentDto paymentDto = paymentDtoMapper.toGetPaymentResponseDtos(payment1);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("service-type",paymentDto.getServiceName());
    }

    @Test
    public void testToReconciliationResponseDto(){
        PaymentDto paymentDto = paymentDtoMapper.toReconciliationResponseDto(paymentFeeLink);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());


    }

    @Test
    public void testToReconciliationResponseDtoForLibereta(){
        when(ff4j.check(any(String.class))).thenReturn(true);
        PaymentDto paymentDto = paymentDtoMapper.toReconciliationResponseDtoForLibereta(payment1,"group-reference",paymentFees,ff4j,true);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
    }

    @Test
    public void testToCreateRecordPaymentResponse(){
        PaymentDto paymentDto = paymentDtoMapper.toCreateRecordPaymentResponse(paymentFeeLink);
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
        assertEquals("group-reference",paymentDto.getPaymentGroupReference());
    }

    @Test
    public void testToPaymentAllocationDto(){
        PaymentAllocationDto paymentAllocationDto = paymentDtoMapper.toPaymentAllocationDto(allocation1);
        assertEquals("1",paymentAllocationDto.getId());
        assertEquals("receiving-office",paymentAllocationDto.getReceivingOffice());
    }

    @Test
    public void testToPaymentAllocationDtoForLiberata(){
        PaymentAllocationDto paymentAllocationDto = paymentDtoMapper.toPaymentAllocationDtoForLibereta(allocation1);
        assertEquals("transferred",paymentAllocationDto.getAllocationStatus());
        assertEquals("receiving-office",paymentAllocationDto.getReceivingOffice());
    }

    @Test
    public void testToPaymentAllocationDtos(){
        PaymentAllocationDto paymentAllocationDto = paymentDtoMapper.toPaymentAllocationDtos(allocation1);
        assertEquals("Transferred",paymentAllocationDto.getAllocationStatus());
    }

}
