package uk.gov.hmcts.payment.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AppInsightsAuditRepository implements AuditRepository {

    private static final Logger LOG = LoggerFactory.getLogger(AppInsightsAuditRepository.class);

    private ObjectMapper mapper = new ObjectMapper();

    private final TelemetryClient telemetry;

    @Autowired
    public AppInsightsAuditRepository(TelemetryClient telemetry) {
        telemetry.getContext().getComponent().setVersion(getClass().getPackage().getImplementationVersion());
        this.telemetry = telemetry;
    }

    @Override
    public void trackEvent(String name, Map<String, String> properties) {
        telemetry.trackEvent(name, properties,null);
    }

    @Override
    public void trackPaymentEvent(String name, Payment payment, List<PaymentFee> fees) {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
            .put("paymentReference", payment.getReference())
            .put("amount", payment.getAmount().toString())
            .put("serviceType", payment.getServiceType())
            .put("status", payment.getStatus())
            .put("fees", fees != null ? toFeeDtoJson(fees) : "")
            .build();
        telemetry.trackEvent(name, properties,null);
    }

    private String toFeeDtoJson(List<PaymentFee> fees) {
        List<FeeDto> feeDtos = fees.stream()
            .map(a -> toFeeDto(a))
            .collect(Collectors.toList());
        try {
            return mapper.writeValueAsString(feeDtos);
        } catch (JsonProcessingException ex) {
            LOG.error("Error Json processing feeDtos:{} with message:{}", feeDtos, ex.getMessage());
            return null;
        }
    }

    private FeeDto toFeeDto(PaymentFee paymentFee) {
        return new FeeDto.FeeDtoBuilder()
            .code(paymentFee.getCode())
            .version(paymentFee.getVersion())
            .volume(paymentFee.getVolume())
            .build();
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static final class FeeDto {
        private String code;
        private String version;
        private Integer volume;
    }
}
