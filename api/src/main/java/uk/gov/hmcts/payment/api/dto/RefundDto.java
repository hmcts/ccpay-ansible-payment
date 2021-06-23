package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import uk.gov.hmcts.payment.api.dto.order.OrderFeeDto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "refundDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefundDto {

    @NotNull
    private String reason;

    @NotBlank
    private String hwfReference;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private OrderFeeDto fee;
}
