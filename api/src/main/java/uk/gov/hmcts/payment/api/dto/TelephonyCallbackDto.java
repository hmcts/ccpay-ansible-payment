package uk.gov.hmcts.payment.api.dto;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "telephonyCallbackWith")
public class TelephonyCallbackDto {

    private String orderCurrency;
    @NotNull
    private String orderAmount;
    @NotNull
    private String orderReference;
    private String ppAccountID;
    @NotNull
    private String transactionResult;
    private String transactionAuthCode;
    private String transactionID;
    private String transactionResponseMsg;
    @ToString.Exclude
    private String cardExpiry;
    private String cardLast4;
    private String ppCallID;
    private String customData1;
    private String customData2;
    private String customData3;
    private String customData4;

}
