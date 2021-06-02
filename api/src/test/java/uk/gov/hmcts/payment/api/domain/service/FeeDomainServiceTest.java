package uk.gov.hmcts.payment.api.domain.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentFeeNotFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class FeeDomainServiceTest {

    @Mock
    private FeePayApportionService feePayApportionService;

    @Mock
    private PaymentFeeRepository paymentFeeRepository;

    @InjectMocks
    FeeDomainServiceImpl feeDomainService;

    @Test
    public void testGetFeePayApportionsByFee(){
        List<FeePayApportion> mockFeeApportionList = Arrays.asList(FeePayApportion.feePayApportionWith()
                                                                    .paymentId(1)
                                                                    .build());
        when(feePayApportionService.getFeePayApportionByFeeId(anyInt())).thenReturn(mockFeeApportionList);
        List<FeePayApportion> resultFeeApportionList = feePayApportionService.getFeePayApportionByFeeId(1);
        assertThat(resultFeeApportionList.get(0).getPaymentId()).isEqualTo(1);
    }

    @Test
    public void testGetPaymentFeeById(){
        PaymentFee mockPaymentFee = PaymentFee.feeWith().id(1).build();
        when(paymentFeeRepository.findById(anyInt())).thenReturn(Optional.of(mockPaymentFee));
        PaymentFee resultFee = paymentFeeRepository.findById(1).get();
        assertThat(feeDomainService.getPaymentFeeById(1).getId()).isEqualTo(1);
    }

    @Test(expected = PaymentFeeNotFoundException.class)
    public void testGetPaymentFeeByIdShouldThrowExceptionForUnavailablePaymentFee(){
        when(paymentFeeRepository.findById(anyInt())).thenThrow(new PaymentFeeNotFoundException());
        paymentFeeRepository.findById(1);
    }


}
