package uk.gov.hmcts.payment.api.mapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.fees.register.legacymodel.Fee;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.dto.mapper.RemissionDtoMapper;
import uk.gov.hmcts.payment.api.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class RemissionDtoMapperTest {

    List<Payment> payments = new ArrayList<Payment>();
    PaymentFeeLink feeLink;
    Payment payment1;

    RemissionDtoMapper remissionDtoMapper = new RemissionDtoMapper();

    @Before
    public void initiate(){
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
            .id(1).build();
        payments.add(payment1);
        PaymentFee fee = PaymentFee.feeWith()
                                    .feeAmount(new BigDecimal("100.00"))
                                    .ccdCaseNumber("ccd-case-number")
                                    .calculatedAmount(new BigDecimal("100.00"))
                                    .code("FEE123")
                                    .version("1")
                                    .volume(1)
                                    .build();
        List<PaymentFee> paymentFees = new ArrayList<PaymentFee>();
        paymentFees.add(fee);
        Remission remission = Remission.remissionWith()
            .remissionReference("remission-reference")
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
    }

    @Test
    public void testToCreateRemissionResponse(){
        RemissionDto remissionDto = remissionDtoMapper.toCreateRemissionResponse(feeLink);
        assertEquals("remission-reference",remissionDto.getRemissionReference());
    }

    @Test
    public  void testToFeeDto(){
        List<FeeDto> feeDtos = new ArrayList<FeeDto>();
        FeeDto  feeDto = FeeDto.feeDtoWith()
                            .calculatedAmount(new BigDecimal("100.00"))
                            .code("FEE123")
                            .ccdCaseNumber("1234123412341324")
                            .version("1")
                            .volume(1)
                            .netAmount(new BigDecimal("100.00"))
                            .reference("RC-1612-3710-5335-6484")
                            .build();
        feeDtos.add(feeDto);
        List<PaymentFee> fees = remissionDtoMapper.toFees(feeDtos);
        assertEquals("1234123412341324",fees.get(0).getCcdCaseNumber());
        assertEquals("RC-1612-3710-5335-6484",fees.get(0).getReference());
    }

}
