package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "report2DtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BulkScanningUnderOverPaymentDto {

    private String respServiceId;

    private String respServiceName;

    private String surplusShortfall;

    private BigDecimal balance;

    private BigDecimal paymentAmount;

    private String ccdCaseReference;

    private Date processedDate;

    private String reason;

    private String explanation;

    private String userName;

    private String ccdExceptionReference;
}
