package uk.gov.hmcts.payment.api.v1.componenttests;

import org.junit.Test;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class PaymentsRefDataComponentTest extends PaymentsComponentTest {

    @Test
    public void testFindAllPaymentChannels() throws Exception {

        restActions
            .get("/refdata/channels")
            .andExpect(status().isOk())
            .andExpect(body().asListOf(PaymentChannel.class, paymentChannels -> {
                assertThat(paymentChannels).anySatisfy(paymentChannel -> {
                    assertThat(paymentChannel.getName()).isEqualTo("Online");
                });
            }));
    }

    @Test
    public void testFindAllPaymentMethods() throws Exception {

        restActions
            .get("/refdata/methods")
            .andExpect(status().isOk())
            .andExpect(body().asListOf(PaymentMethod.class, paymentMethods -> {
                assertThat(paymentMethods).anySatisfy(paymentMethod -> {
                    assertThat(paymentMethod.getName()).isEqualTo("Card");
                });
            }));
    }

    @Test
    public void testFindAllPaymentProviders() throws Exception {

        restActions
            .get("/refdata/providers")
            .andExpect(status().isOk())
            .andExpect(body().asListOf(PaymentProvider.class, paymentProviders -> {
                assertThat(paymentProviders).anySatisfy(paymentProvider -> {
                    assertThat(paymentProvider.getName()).isEqualTo("GovPay");
                });
            }));
    }

    @Test
    public void testFindAllPaymentStatuses() throws Exception {

        restActions
            .get("/refdata/status")
            .andExpect(status().isOk())
            .andExpect(body().asListOf(PaymentStatus.class, paymentStatuses -> {
                assertThat(paymentStatuses).anySatisfy(paymentStatus -> {
                    assertThat(paymentStatus.getName()).isEqualTo("Pending");
                });
            }));
    }
}
