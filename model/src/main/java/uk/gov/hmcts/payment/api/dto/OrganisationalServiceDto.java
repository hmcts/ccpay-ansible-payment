package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.Date;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "orgServiceDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OrganisationalServiceDto {
    private String jurisdiction;
    private String serviceId;
    private String orgUnit;
    private String businessArea;
    private String subBusinessArea;
    private String serviceDescription;
    private String serviceCode;
    private String serviceShortDescription;
    private String ccdServiceName;
    private Date lastUpdate;
    private List<String> ccdCaseTypes;
}
