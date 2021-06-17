package uk.gov.hmcts.payment.api.dto.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.Remission;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RemissionDtoMapper {

    public RemissionDto toCreateRemissionResponse(PaymentFeeLink paymentFeeLink) {
        Remission remission = paymentFeeLink.getRemissions().get(0);
        FeeDto feeDto = toFeeDto(paymentFeeLink.getFees().get(0));

        return RemissionDto.remissionDtoWith()
            .remissionReference(remission.getRemissionReference())
            .paymentReference(paymentFeeLink.getPayments() == null || paymentFeeLink.getPayments().isEmpty() ? null : paymentFeeLink.getPayments().get(0).getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .fee(feeDto)
            .build();
    }

    public List<PaymentFee> toFees(List<FeeDto> feeDtos) {
        return feeDtos.stream().map(this::toFee).collect(Collectors.toList());
    }

    public FeeDto toFeeDto(PaymentFee fee) {
        return FeeDto.feeDtoWith()
            .id(fee.getId())
            .calculatedAmount(fee.getCalculatedAmount())
            .code(fee.getCode())
            .version(fee.getVersion())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .volume(fee.getVolume())
            .build();
    }

    public PaymentFee toFee(FeeDto feeDto) {
        return PaymentFee.feeWith()
            .calculatedAmount(feeDto.getCalculatedAmount())
            .code(feeDto.getCode())
            .ccdCaseNumber(feeDto.getCcdCaseNumber())
            .version(feeDto.getVersion())
            .volume(feeDto.getVolume() == null ? 1 : feeDto.getVolume().intValue())
            .netAmount(feeDto.getNetAmount())
            .reference(feeDto.getReference())
            .build();
    }

}
