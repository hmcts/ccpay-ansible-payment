package uk.gov.hmcts.payment.api.controllers.utils;

import org.apache.commons.lang.math.RandomUtils;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.componenttests.util.CSVUtil;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;

import javax.validation.constraints.NotNull;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class ReplayCreditAccountPaymentUtils {

    public CreditAccountPaymentRequest getPBAPayment(Double calculatedAmount, List<FeeDto> fees) {
        return CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
            .amount(new BigDecimal(calculatedAmount))
            .ccdCaseNumber("1607065" + RandomUtils.nextInt(999999999))
            .accountNumber("\"PBA0073" + RandomUtils.nextInt(999) + "\"")
            .description("Money Claim issue fee")
            .caseReference("\"9eb95270-7fee-48cf-afa2-e6c58ee" + RandomUtils.nextInt(999) + "ba\"")
            .service("CMC")
            .currency(CurrencyCode.GBP)
            .customerReference("DEA2682/1/SWG" + RandomUtils.nextInt(999))
            .organisationName("\"Slater & Gordon" + RandomUtils.nextInt(999) + "\"")
            .siteId("Y689")
            .fees(fees)
            .build();
    }

    @NotNull
    public List<FeeDto> getFees(Double calculatedAmount) {
        List<FeeDto> fees = new ArrayList<>();
        fees.add(FeeDto.feeDtoWith()
            .code("FEE020" + RandomUtils.nextInt(9))
            .version(Integer.toString(RandomUtils.nextInt(9)))
            .calculatedAmount(new BigDecimal(calculatedAmount)).build());
        return fees;
    }

    public String setCreditAccountPaymentLiberataCheckFeature(boolean enabled) throws Exception {
        String url = "/api/ff4j/store/features/credit-account-payment-liberata-check/";
        if (enabled) {
            url += "enable";
        } else {
            url += "disable";
        }
        return url;
    }

    public void createCSV(Map<String, CreditAccountPaymentRequest> csvParseMap, String fileName) throws IOException {
        String csvFile = "src/test/resources/" +fileName;
        FileWriter writer = new FileWriter(csvFile);

        //for header
        CSVUtil.writeLine
            (writer, Arrays.asList("index_col", "impacted.payment.reference", "payment.amount", "payment.ccd_case_number",
                "payment.pba_number", "payment.description", "payment.case_reference", "payment.service",
                "payment.currency", "payment.customer_reference", "payment.organisation_name", "payment.site_id",
                "fee.code", "fee.calculated_amount", "fee.version"));

        csvParseMap.entrySet().stream().forEach(paymentRequestEntry ->
            {
                CreditAccountPaymentRequest request = paymentRequestEntry.getValue();
                List<String> list = new ArrayList<>();
                list.add("");
                list.add(paymentRequestEntry.getKey());
                list.add(request.getAmount().toString());
                list.add(request.getCcdCaseNumber());
                list.add(request.getAccountNumber());
                list.add(request.getDescription());
                list.add(request.getCaseReference());
                list.add("CMC");
                list.add("GBP");
                list.add(request.getCustomerReference());
                list.add(request.getOrganisationName());
                list.add(request.getSiteId());
                list.add(request.getFees().get(0).getCode());
                list.add(request.getFees().get(0).getCalculatedAmount().toString());
                list.add(request.getFees().get(0).getVersion());

                try {
                    CSVUtil.writeLine(writer, list);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        );

        writer.flush();
        writer.close();
    }

}
