package uk.gov.hmcts.payment.api.mapper;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.LoggingEvent;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static uk.gov.hmcts.payment.api.util.AccountStatus.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({LoggerFactory.class})
public class PBAStatusErrorMapperTest {
    private static CreditAccountPaymentRequest creditAccountPaymentRequest;

    private static Payment payment;

    private static AccountDto activeAccountDetails;
    private static AccountDto activeAccountInsufficientDetails;
    private static AccountDto onHoldAccountDetails;
    private static AccountDto deletedAccountDetails;

    private static Logger mockLOG;


    @BeforeClass
    public static void initiate(){
        mockStatic(LoggerFactory.class);
        mockLOG = mock(Logger.class);
        when(LoggerFactory.getLogger(any(Class.class))).thenReturn(mockLOG);
        creditAccountPaymentRequest = CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
                                        .amount(new BigDecimal("10.00"))
                                        .build();
        payment = Payment.paymentWith().ccdCaseNumber("ccd-number").build();
        activeAccountDetails = AccountDto.accountDtoWith()
                            .accountName("account-name")
                            .availableBalance(new BigDecimal("100.00"))
                            .status(ACTIVE).build();
        activeAccountInsufficientDetails = AccountDto.accountDtoWith()
            .accountName("account-name")
            .availableBalance(new BigDecimal("9.00"))
            .build();
        onHoldAccountDetails = AccountDto.accountDtoWith()
            .accountName("account-name")
            .availableBalance(new BigDecimal("100.00"))
            .status(AccountStatus.ON_HOLD).build();
        deletedAccountDetails = AccountDto.accountDtoWith()
            .accountName("account-name")
            .availableBalance(new BigDecimal("100.00"))
            .status(AccountStatus.DELETED).build();

    }


    @Test
    public void testSetPaymentStatusWithOnHold() {
        PBAStatusErrorMapper pbaStatusErrorMapper = new PBAStatusErrorMapper();
        String expected = "CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance InSufficient!!!";
        pbaStatusErrorMapper.setPaymentStatus(creditAccountPaymentRequest, payment, onHoldAccountDetails);
        verify(mockLOG).info(expected,"ccd-number",ON_HOLD,"failed");
    }

    @Test
    public void testSetPaymentStatusWithActiveAndSufficientBalance() {
        PBAStatusErrorMapper pbaStatusErrorMapper = new PBAStatusErrorMapper();
        String expected = "CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance Sufficient!!!";
        pbaStatusErrorMapper.setPaymentStatus(creditAccountPaymentRequest,payment,activeAccountDetails);
        verify(mockLOG).info(expected,"ccd-number",ACTIVE,"success");
    }

    @Test
    public void testSetPaymentStatusWithDeletedStatus() {
        PBAStatusErrorMapper pbaStatusErrorMapper = new PBAStatusErrorMapper();
        String expected = "CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance InSufficient!!!";
        pbaStatusErrorMapper.setPaymentStatus(creditAccountPaymentRequest,payment,deletedAccountDetails);
        verify(mockLOG).info(expected,"ccd-number",DELETED,"failed");
    }


}
