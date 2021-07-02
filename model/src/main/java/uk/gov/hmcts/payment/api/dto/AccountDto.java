package uk.gov.hmcts.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.payment.api.util.AccountStatus;

import java.math.BigDecimal;
import java.util.Date;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "accountDtoWith")
@Data
public class AccountDto {

    private String accountNumber;

    private String accountName;

    private BigDecimal creditLimit;

    private BigDecimal availableBalance;

    private AccountStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "GMT")
    private Date effectiveDate;

}
