package uk.gov.hmcts.payment.api.audit;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.payment.api.model.Payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
public class AuditInsightsAuditRepositoryTest {
    @Spy
    private TelemetryClient telemetry = new TelemetryClient();;

    @InjectMocks
    AppInsightsAuditRepository appInsightsAuditRepository;

    @Test
    public void testTrackEvent(){
        Map<String, String> properties = new HashMap<>();
        properties.put("key","value");
        Mockito.doNothing().when(telemetry).trackEvent(Mockito.any(String.class),Mockito.any(Map.class),Mockito.any(Map.class));
        appInsightsAuditRepository.trackEvent("name",properties);
        Mockito.verify(telemetry).trackEvent("name",properties,null);
    }

    @Test
    public  void testTrackPaymentEvent(){
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
            .put("paymentReference", "reference")
            .put("amount", "100")
            .put("serviceType", "service-type")
            .put("status", "status")
            .put("fees","")
            .build();
        Payment payment = Payment.paymentWith()
                            .reference("reference")
                            .amount(BigDecimal.valueOf(100))
                            .serviceType("service-type")
                            .status("status")
                            .build();
        Mockito.doNothing().when(telemetry).trackEvent(Mockito.any(String.class),Mockito.any(Map.class),Mockito.any(Map.class));
        appInsightsAuditRepository.trackPaymentEvent("name",payment,null);
        Mockito.verify(telemetry).trackEvent("name",properties,null);
    }
}
