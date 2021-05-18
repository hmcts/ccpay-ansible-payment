package uk.gov.hmcts.payment.api.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.StatusHistory;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.Collections;

@Component
public class PBAStatusErrorMapper {

    private static final String FAILED = "failed";
    private static final Logger LOG = LoggerFactory.getLogger(PBAStatusErrorMapper.class);

    public void setPaymentStatus(CreditAccountPaymentRequest creditAccountPaymentRequest, Payment payment, AccountDto accountDetails) {
        if (accountDetails.getStatus() == AccountStatus.ACTIVE && isAccountBalanceSufficient(accountDetails.getAvailableBalance(),
            creditAccountPaymentRequest.getAmount())) {
            payment.setPaymentStatus(PaymentStatus.paymentStatusWith().name("success").build());
            LOG.info("CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance Sufficient!!!", payment.getCcdCaseNumber(), accountDetails.getStatus(), payment.getPaymentStatus().getName());
        } else if (accountDetails.getStatus() == AccountStatus.ACTIVE) {
            payment.setPaymentStatus(PaymentStatus.paymentStatusWith().name(FAILED).build());
            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .status(payment.getPaymentStatus().getName())
                .errorCode("CA-E0001")
                .message("You have insufficient funds available")
                .message("Payment request failed. PBA account " + accountDetails.getAccountName()
                    + " have insufficient funds available").build()));
            LOG.info("Payment request failed. PBA account {} has insufficient funds available." +
                    " Requested payment was {} where available balance is {}",
                accountDetails.getAccountName(), creditAccountPaymentRequest.getAmount(),
                accountDetails.getAvailableBalance());
            LOG.info("CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance InSufficient!!!", payment.getCcdCaseNumber(), accountDetails.getStatus(), payment.getPaymentStatus().getName());
        } else if (accountDetails.getStatus() == AccountStatus.ON_HOLD) {
            payment.setPaymentStatus(PaymentStatus.paymentStatusWith().name(FAILED).build());
            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .status(payment.getPaymentStatus().getName())
                .errorCode("CA-E0003")
                .message("Your account is on hold")
                .build()));
            LOG.info("CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance InSufficient!!!", payment.getCcdCaseNumber(), accountDetails.getStatus(), payment.getPaymentStatus().getName());
        } else if (accountDetails.getStatus() == AccountStatus.DELETED) {
            payment.setPaymentStatus(PaymentStatus.paymentStatusWith().name(FAILED).build());
            payment.setStatusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .status(payment.getPaymentStatus().getName())
                .errorCode("CA-E0004")
                .message("Your account is deleted")
                .build()));
            LOG.info("CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {} PaymentStatus : {} - Account Balance InSufficient!!!", payment.getCcdCaseNumber(), accountDetails.getStatus(), payment.getPaymentStatus().getName());
        }
    }

    public void setOrderPaymentStatus(BigDecimal amount, Payment payment, AccountDto accountDetails) {
        setPaymentStatus(CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith().amount(amount).build(), payment, accountDetails);
    }


    private boolean isAccountBalanceSufficient(BigDecimal availableBalance, BigDecimal paymentAmount) {
        return availableBalance.compareTo(paymentAmount) >= 0;
    }
}
