package uk.gov.hmcts.payment.api.contract.util;

import org.junit.Test;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;

import static org.junit.Assert.assertEquals;

public class CurrencyCodeTest {

    @Test
    public void testGBPCurrencyCode(){
        assertEquals("GBP",CurrencyCode.GBP.getCode());
    }

    @Test
    public void testCardPaymentRequestCurrencyCode(){
        CardPaymentRequest cardPaymentRequest = CardPaymentRequest.createCardPaymentRequestDtoWith()
                                                    .currency(CurrencyCode.GBP)
                                                    .build();
        assertEquals(cardPaymentRequest.getCurrency().getCode(),CurrencyCode.GBP.getCode());
    }
}
