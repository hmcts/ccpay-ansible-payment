package uk.gov.hmcts.payment.api.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "orderFeeBoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderFeeBo {

    private String code;

    private String version;

    private Integer volume;

    private BigDecimal calculatedAmount;

    private String ccdCaseNumber; // Will be removed after get api's work without ccd dependency

    private BigDecimal amountDue;
    //private List<RemissionDto> remissions;
}
