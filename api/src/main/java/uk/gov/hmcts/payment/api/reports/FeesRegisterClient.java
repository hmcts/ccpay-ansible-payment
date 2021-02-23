package uk.gov.hmcts.payment.api.reports;

import org.slf4j.Logger;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@FeignClient(name = "fees-register-client", url = "${fees.register.url}")
public interface FeesRegisterClient {
    Logger LOG = getLogger(FeesRegisterClient.class);

    @GetMapping(value = "/fees-register/fees")
    List<Fee2Dto> getFeesData();

    default Optional<Map<String,Fee2Dto>> getFeesDataAsMap() {
        LOG.info("Inside getFeesDataAsMap in FeesRegisterClient!!!");
        return Optional.ofNullable(getFeesData().stream()
            .collect(Collectors.toMap(
                Fee2Dto::getCode,
                fee ->fee,
                (fee1, fee2) -> {
                    LOG.warn("duplicate feeCode key found :{}", fee2);
                    return fee1;
                }
            )));
        }

}
