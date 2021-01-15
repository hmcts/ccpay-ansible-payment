package uk.gov.hmcts.payment.api.componenttests;

import org.assertj.core.util.Lists;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.Reference;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.componenttests.TestUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static uk.gov.hmcts.payment.api.model.Payment.paymentWith;
import static uk.gov.hmcts.payment.api.model.PaymentFee.feeWith;

public class PaymentServiceTest extends TestUtil {

    @Autowired
    @Qualifier("paymentServiceImpl")
    protected PaymentService paymentService;

    private PaymentsDataUtil paymentsDataUtil;

    @Test
    public void testListInitiatedStatusPaymentsReferences() throws Exception {
        paymentsDataUtil = new PaymentsDataUtil();
        // given DB has 4 payments
        List<Payment> payments = Lists.newArrayList(
            getPaymentWithStatus("created"),
            getPaymentWithStatus("submitted"),
            // final states
            getPaymentWithStatus("success"),
            getPaymentWithStatus("cancelled"),
            getPaymentWithStatus("failed"),
            getPaymentWithStatus("error"));

        PaymentFeeLink paymentFeeLink = PaymentFeeLink.paymentFeeLinkWith()
            .payments(payments)
            .fees(paymentsDataUtil.getFeesData())
            .build();

        paymentFeeLinkRepository.save(paymentFeeLink);

        // when
        List<Reference> paymentsReferences = paymentService.listInitiatedStatusPaymentsReferences();

        // then
        assertThat(paymentsReferences).hasSize(2);
    }

    private Payment getPaymentWithStatus(String paymentStatus) {
        return Payment.paymentWith()
            .amount(new BigDecimal("99.99"))
            .ccdCaseNumber("ccdCaseNumber")
            .description("Test payments statuses for ")
            .serviceType("CMC")
            .currency("GBP")
            .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
            .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
            .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
            .paymentStatus(PaymentStatus.paymentStatusWith().name(paymentStatus).build())
            .reference("RC-1519-9028-2432-0002" + paymentStatus)
            .build();
    }

    @Test
    public void testSearchAllPaymentBetweenDatesShouldPass() throws Exception {
        Payment payment = paymentWith().amount(BigDecimal.valueOf(10000).movePointRight(2)).reference("reference1").description("desc1").returnUrl("https://www.moneyclaims.service.gov.uk")
            .ccdCaseNumber("ccdCaseNo1").caseReference("caseRef1").serviceType("cmc").currency("GBP")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                .externalStatus("created")
                .status("Initiated")
                .build()))
            .build();
        PaymentFee fee = feeWith().code("FEE0111").version("1").build();

        PaymentFeeLink saved = paymentFeeLinkRepository.save(PaymentFeeLink.paymentFeeLinkWith().paymentReference("00000005")
            .payments(Arrays.asList(payment))
            .fees(Arrays.asList(fee))
            .build());

        List<PaymentFeeLink> paymentFeeLinks =
            paymentService.search(
                PaymentSearchCriteria.searchCriteriaWith()
                    .startDate(LocalDateTime.now().minusMinutes(1).toDate())
                    .endDate(LocalDateTime.now().toDate())
                    .build()
            );
        assertNotNull(paymentFeeLinks);
        assertThat(paymentFeeLinks.get(0).getPayments().size()).isEqualTo(1);

    }
}
