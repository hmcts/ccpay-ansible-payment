package uk.gov.hmcts.payment.api.contract.util;

import org.junit.Test;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ServiceTest {

    @Test
    public void testService(){
        assertEquals("Civil Money Claims",Service.CMC.getName());
    }

    @Test
    public void testCardPaymentRequestService(){
        CardPaymentRequest cardPaymentRequest  = CardPaymentRequest.createCardPaymentRequestDtoWith()
                                                    .service(Service.DIGITAL_BAR).build();
        assertEquals("Digital Bar",cardPaymentRequest.getService().getName());
    }
}
