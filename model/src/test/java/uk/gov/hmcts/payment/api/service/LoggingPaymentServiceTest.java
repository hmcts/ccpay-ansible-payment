package uk.gov.hmcts.payment.api.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.payment.api.dto.PaymentServiceRequest;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.v1.model.UserIdSupplier;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

public class LoggingPaymentServiceTest {

    @Mock
    private UserIdSupplier userIdSupplier;

    @Mock
    private DelegatingPaymentService delegatingPaymentService;

    @InjectMocks
    private LoggingPaymentService loggingPaymentService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createCardPaymentTest() throws Exception {
        when(userIdSupplier.get()).thenReturn("USER_ID");
        when(delegatingPaymentService.create(new PaymentServiceRequest("paymentGroupReference", "paymentReference", "description",
            "https://www.moneyclaims.service.gov.uk", "ccdCaseNumber", "caseReference",
            "GBP", "siteId", "divorce",
            Arrays.asList(PaymentFee.feeWith().calculatedAmount(new BigDecimal(10000)).code("X0001").version("1").build()),
            new BigDecimal("10000"), null, null, null,null)
        )).thenReturn(PaymentFeeLink.paymentFeeLinkWith().id(1)
            .payments(Arrays.asList(Payment.paymentWith()
                .id(1)
                .amount(new BigDecimal("10000"))
                .status("created")
                .reference("RC-1518-9479-8089-4415")
                .currency("GBP")
                .siteId("siteId")
                .build()))
            .fees(Arrays.asList(PaymentFee.feeWith()
                .id(1)
                .calculatedAmount(new BigDecimal("10000"))
                .code("X0001")
                .version("1")
                .build()))
            .build());

        PaymentFeeLink paymentFeeLink = loggingPaymentService.create(new PaymentServiceRequest("paymentGroupReference", "paymentReference",
            "description", "https://www.moneyclaims.service.gov.uk", "ccdCaseNumber",
            "caseReference", "GBP", "siteId", "divorce",
            Arrays.asList(PaymentFee.feeWith().calculatedAmount(new BigDecimal(10000)).code("X0001").version("1").build()),
            new BigDecimal("10000"), null, null, null,null)
        );
        assertNotNull(paymentFeeLink);
        paymentFeeLink.getPayments().stream().forEach(p -> {
            assertEquals(p.getReference(), "RC-1518-9479-8089-4415");
            assertEquals(p.getAmount(), new BigDecimal("10000"));
            assertEquals(p.getStatus(), "created");
        });
    }
}
