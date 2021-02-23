package uk.gov.hmcts.payment.api.contract;

import com.opencsv.bean.CsvBindByName;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReplayCreditAccountPaymentRequest {

    @CsvBindByName(column = "impacted.payment.reference")
    @NotNull
    private String existingPaymentReference;

    @CsvBindByName(column = "payment.amount")
    @NotNull
    private BigDecimal amount;

    @CsvBindByName(column = "payment.ccd_case_number")
    @NotNull
    private String ccdCaseNumber;

    @CsvBindByName(column = "payment.pba_number")
    @NotNull
    private String pbaNumber;

    @CsvBindByName(column = "payment.description")
    @NotNull
    private String description;

    @CsvBindByName(column = "payment.case_reference")
    @NotNull
    private String caseReference;

    @CsvBindByName(column = "payment.service")
    @NotNull
    private String service;

    @CsvBindByName(column = "payment.currency")
    @NotNull
    private String currency;

    @CsvBindByName(column = "payment.customer_reference")
    @NotNull
    private String customerReference;

    @CsvBindByName(column = "payment.organisation_name")
    @NotNull
    private String organisationName;

    @CsvBindByName(column = "payment.site_id")
    @NotNull
    private String siteId;

    @CsvBindByName(column = "fee.code")
    @NotNull
    private String code;

    @CsvBindByName(column = "fee.calculated_amount")
    @NotNull
    private BigDecimal calculatedAmount;

    @CsvBindByName(column = "fee.version")
    @NotNull
    private String version;


}

