package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import javax.validation.constraints.NotNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "supplementaryInfoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SupplementaryInfo {

    @NotNull
    private String ccdCaseNumber;

    @NotNull
    private SupplementaryDetails supplementaryDetails;
}
