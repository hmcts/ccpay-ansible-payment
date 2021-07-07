package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import java.util.List;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "supplementaryPaymentDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SupplementaryPaymentDto {
    private List<PaymentDto> payments;
    private List<SupplementaryInfo> supplementaryInfo;
 }
