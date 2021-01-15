package uk.gov.hmcts.payment.api.v1.model.govpay;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import uk.gov.hmcts.payment.api.external.client.GovPayClient;
import uk.gov.hmcts.payment.api.external.client.dto.CreatePaymentRequest;
import uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment;
import uk.gov.hmcts.payment.api.external.client.dto.Link;
import uk.gov.hmcts.payment.api.external.client.dto.RefundPaymentRequest;
import uk.gov.hmcts.payment.api.v1.model.ServiceIdSupplier;

import static org.mockito.Mockito.*;
import static uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment.Links.linksWith;
import static uk.gov.hmcts.payment.api.external.client.dto.GovPayPayment.govPaymentWith;


public class GovPayPaymentServiceTest {

    private static final String SERVICE_ID = "SERVICE_ID";
    private static final String GOV_PAY_KEY = "GOV_PAY_KEY";

    private final ServiceIdSupplier serviceIdSupplier = () -> SERVICE_ID;
    private final GovPayKeyRepository govPayKeyRepository = mock(GovPayKeyRepository.class);
    private final GovPayClient govPayClient = mock(GovPayClient.class);
    private final GovPayPaymentService govPayPaymentService = new GovPayPaymentService(govPayKeyRepository, govPayClient, serviceIdSupplier);

    @Before
    public void setUp() throws Exception {
        when(govPayKeyRepository.getKey(SERVICE_ID)).thenReturn(GOV_PAY_KEY);
    }

    @Test
    public void createShouldDelegateToClient() {
        govPayPaymentService.create(100, "reference", "description", "https://www.moneyclaims.service.gov.uk","language");
        verify(govPayClient).createPayment(GOV_PAY_KEY, new CreatePaymentRequest(100, "reference", "description", "https://www.moneyclaims.service.gov.uk","language"));
    }

    @Test
    public void retrieveShouldDelegateToClient() {
        govPayPaymentService.retrieve("ID");
        verify(govPayClient).retrievePayment(GOV_PAY_KEY, "ID");
    }

    @Test
    public void cancelShouldDelegateToClient() {
        GovPayPayment govPayPayment = govPaymentWith()
                .links(linksWith()
                        .cancel(new Link("any", ImmutableMap.of(), "cancelHref", "any")).build())
                .build();
        when(govPayClient.retrievePayment(GOV_PAY_KEY, "ID")).thenReturn(govPayPayment);
        govPayPaymentService.cancel("ID");
        verify(govPayClient).cancelPayment(GOV_PAY_KEY, "cancelHref");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cancelShouldThrowExceptionIfLinkIsNotAvailable() {
        GovPayPayment govPayPayment = govPaymentWith().links(linksWith().build()).build();
        when(govPayClient.retrievePayment(GOV_PAY_KEY, "ID")).thenReturn(govPayPayment);
        govPayPaymentService.cancel("ID");
    }

    @Test
    public void refundShouldDelegateToClient() {
        GovPayPayment govPayPayment = govPaymentWith()
                .links(linksWith()
                        .refunds(new Link("any", ImmutableMap.of(), "refundsHref", "any")).build())
                .build();
        when(govPayClient.retrievePayment(GOV_PAY_KEY, "ID")).thenReturn(govPayPayment);
        govPayPaymentService.refund("ID", 100, 999);
        verify(govPayClient).refundPayment(GOV_PAY_KEY, "refundsHref", new RefundPaymentRequest(100, 999));
    }

}
