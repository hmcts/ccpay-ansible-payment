package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import uk.gov.hmcts.payment.api.contract.FeeDto;

import java.math.BigDecimal;
import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "remissionDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RemissionDto {

    private String id;

    private String remissionReference;

    private String hwfReference;

    private BigDecimal hwfAmount;

    private String beneficiaryName;

    private String ccdCaseNumber;

    private String caseReference;

    private String paymentGroupReference;

    private String paymentReference;

    private String feeCode;

    private FeeDto fee;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;
}
