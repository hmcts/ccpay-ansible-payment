package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import lombok.experimental.Wither;
import javax.validation.Valid;
import java.util.List;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@With
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "createIacSupplementaryRequestWith")
@Wither
public class IacSupplementaryRequest {

    @Valid
    private List<String> ccdCaseNumbers;
}
