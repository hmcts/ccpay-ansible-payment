package uk.gov.hmcts.payment.api.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "report2DtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BulkScanningReportDto {

    private String respServiceId;

    private String respServiceName;

    private String allocationStatus;

    private String receivingOffice;

    private String allocationReason;

    private String ccdExceptionReference;

    private String ccdCaseReference;

    private Date dateBanked;

    private String bgcBatch;

    private String paymentAssetDCN;

    private String paymentMethod;

    private String amount;
}
