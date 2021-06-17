package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Size;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(builderMethodName = "migrationSingleDataDto")
public class MigrationSingleDataDto {

    @Size(min = 16, max = 16,message = "ccdcase_number should be of size 16")
    private String ccdCaseNumber;

    private String caseReference;

    @JsonProperty("org_id")
    private String siteId;

    @JsonProperty("enterprise_service_name")
    private String serviceType;
}
