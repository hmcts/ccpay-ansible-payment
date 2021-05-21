package uk.gov.hmcts.payment.api.mapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.domain.service.FeeDomainService;
import uk.gov.hmcts.payment.api.domain.service.PaymentDomainService;
import uk.gov.hmcts.payment.api.dto.RetrieveOrderPaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentGroupDtoMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentAllocation;
import uk.gov.hmcts.payment.api.model.PaymentAllocationStatus;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.Remission;
import uk.gov.hmcts.payment.api.reports.FeesService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaymentGroupDtoMapperTest {
    @Mock
    FeesService feesService;


    @Mock
    LaunchDarklyFeatureToggler featureToggler;

    @Mock
    FeeDomainService feeDomainService;

    @Mock
    PaymentDomainService paymentDomainService;

    @InjectMocks
    PaymentGroupDtoMapper paymentGroupDtoMapper = new PaymentGroupDtoMapper();

    PaymentFeeLink feeLink;

    @Before
    public void initiate(){
        List<Payment> payments = new ArrayList<Payment>();
        payments.add(getPayment());
        PaymentFee fee = PaymentFee.feeWith().feeAmount(new BigDecimal("100.00")).ccdCaseNumber("ccd-case-number").calculatedAmount(new BigDecimal("100.00")).build();
        Remission remission = Remission.remissionWith()
            .hwfAmount(new BigDecimal("10.00"))
            .fee(fee)
            .build();
        List<Remission> remissionList = new ArrayList<Remission>();
        remissionList.add(remission);

        List<PaymentFee> paymentFees = new ArrayList<PaymentFee>();
        paymentFees.add(fee);
        feeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference("payment-reference")
            .fees(paymentFees)
            .payments(payments)
            .remissions(remissionList)
            .build();
    }


    @Test
    public void testToPaymentGroupDtoForFeePayApportionment(){
        RetrieveOrderPaymentGroupDto paymentGroupDto = RetrieveOrderPaymentGroupDto.paymentGroupDtoWith().build();
        Payment payment = getPayment();
        FeeVersionDto feeVersionDto = FeeVersionDto.feeVersionDtoWith().build();
        Mockito.when(feesService.getFeeVersion(anyString(), anyString())).thenReturn(getPaymentFeeDto());
        Mockito.when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(true);
        Mockito.when(paymentDomainService.getFeePayApportionByPaymentId(anyInt())).thenReturn(Arrays.asList(getFeePayApportion()));
        when(feeDomainService.getPaymentFeeById(anyInt())).thenReturn(getPaymentFee());
        RetrieveOrderPaymentGroupDto resultPaymentGroupDto = paymentGroupDtoMapper.toPaymentGroupDtoForFeePayApportionment(paymentGroupDto,payment);
        assertEquals(resultPaymentGroupDto.getPayments().get(0).getAmount(),new BigDecimal("100.00"));

    }

    @Test
    public void testToPaymentFee(){
        Mockito.when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(false);
        FeeDto feeDto = FeeDto.feeDtoWith()
            .calculatedAmount(new BigDecimal(("100.00")))
            .code("FEE123")
            .version("1")
            .volume(1)
            .feeAmount(new BigDecimal("50.00"))
            .netAmount(new BigDecimal("50.00"))
            .dateCreated(new Date(2020,10,1))
            .ccdCaseNumber("123456789012345")
            .reference("RC-1612-3710-5335-6484")
            .build();
        PaymentFee paymentFee = paymentGroupDtoMapper.toPaymentFee(feeDto);
        assertEquals("123456789012345",paymentFee.getCcdCaseNumber());
        assertEquals("FEE123",paymentFee.getCode());
    }

    private Payment getPayment(){
        List<PaymentAllocation> paymentAllocations1 = new ArrayList<PaymentAllocation>();
        PaymentAllocation allocation1 = PaymentAllocation.paymentAllocationWith()
            .receivingOffice("receiving-office")
            .unidentifiedReason("reason")
            .paymentAllocationStatus(PaymentAllocationStatus.paymentAllocationStatusWith().name("Transferred").build()).build();
        paymentAllocations1.add(allocation1);
        return Payment.paymentWith()
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
            .paymentAllocation(paymentAllocations1)
            .id(1).build();
    }


    private FeePayApportion getFeePayApportion(){
        return FeePayApportion.feePayApportionWith()
            .apportionAmount(new BigDecimal("99.99"))
            .apportionType("AUTO")
            .feeId(1)
            .paymentId(1)
            .feeAmount(new BigDecimal("99.99"))
            .paymentId(1)
            .build();
    }

    private PaymentFee getPaymentFee(){
        return PaymentFee.feeWith()
            .calculatedAmount(new BigDecimal("99.99"))
            .version("1").code("FEE0001").volume(1)
            .paymentLink(PaymentFeeLink.paymentFeeLinkWith()
                            .paymentReference("payment-reference")
                            .dateCreated(new Date(2021,1,1))
                            .dateUpdated(new Date(2021,1,1))
                            .build())
            .remissions(Arrays.asList(
                Remission.remissionWith()
                    .remissionReference("remission-reference")
                    .beneficiaryName("beneficiary-name")
                    .ccdCaseNumber("ccd_case_number")
                    .caseReference("case_reference")
                    .hwfReference("hwf-reference")
                    .hwfAmount(new BigDecimal("100.00"))
                    .fee(PaymentFee.feeWith().feeAmount(new BigDecimal("100.00")).build())
                    .dateCreated(new Date(2021,1,1))
                    .build()
                ))
            .build();
    }

    private Optional<FeeVersionDto> getPaymentFeeDto(){
        FeeVersionDto feeVersionDto = FeeVersionDto.feeVersionDtoWith()
                                        .memoLine("Memo Line")
                                        .naturalAccountCode("acc-code")
                                        .description("description")
                                        .build();

        return Optional.of(feeVersionDto);
    }

}
