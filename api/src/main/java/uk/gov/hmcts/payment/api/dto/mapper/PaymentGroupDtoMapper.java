package uk.gov.hmcts.payment.api.dto.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RemissionDto;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.util.PayStatusToPayHubStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PaymentGroupDtoMapper {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentGroupDtoMapper.class);

    @Autowired
    private FeesService feesService;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;


    public PaymentGroupDto toPaymentGroupDto(PaymentFeeLink paymentFeeLink) {
        return PaymentGroupDto.paymentGroupDtoWith()
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .dateCreated(paymentFeeLink.getDateCreated())
            .dateUpdated(paymentFeeLink.getDateUpdated())
            .fees(toFeeDtos(paymentFeeLink.getFees()))
            .payments((!(paymentFeeLink.getPayments() == null) && !paymentFeeLink.getPayments().isEmpty()) ? toPaymentDtos(paymentFeeLink.getPayments()) : null)
            .remissions(!(paymentFeeLink.getRemissions() == null) ? toRemissionDtos(paymentFeeLink.getRemissions()) : null)
            .build();
    }

    private List<PaymentDto> toPaymentDtos(List<Payment> payments) {
        return payments.stream().map(p -> toPaymentDto(p)).collect(Collectors.toList());
    }
    //added missing pba account details
    private PaymentDto toPaymentDto(Payment payment) {
        return PaymentDto.payment2DtoWith()
            .reference(payment.getReference())
            .amount(payment.getAmount())
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .accountNumber(payment.getPbaNumber())
            .organisationName(payment.getOrganisationName())
            .customerReference(payment.getCustomerReference())
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .description(payment.getDescription())
            .channel(payment.getPaymentChannel() != null ? payment.getPaymentChannel().getName() : null)
            .method(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getName() : null)
            .externalReference(payment.getExternalReference())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .dateCreated(payment.getDateCreated() != null ? payment.getDateCreated() : null)
            .dateUpdated(payment.getDateUpdated() != null ? payment.getDateUpdated() : null)
            .documentControlNumber(payment.getDocumentControlNumber())
            .bankedDate(payment.getBankedDate())
            .payerName(payment.getPayerName())
            .paymentAllocation(payment.getPaymentAllocation() !=null ? toPaymentAllocationDtos(payment.getPaymentAllocation()) : null)
            .build();
    }

    private List<RemissionDto> toRemissionDtos(List<Remission> remissions) {
        return remissions.stream().map(r -> toRemissionDto(r)).collect(Collectors.toList());
    }

    private List<PaymentAllocationDto> toPaymentAllocationDtos(List<PaymentAllocation> paymentAllocation) {
        return paymentAllocation.stream().map(pa -> toPaymentAllocationDto(pa)).collect(Collectors.toList());
    }

    public PaymentAllocationDto toPaymentAllocationDto(PaymentAllocation paymentAllocation){
        return PaymentAllocationDto.paymentAllocationDtoWith()
            .allocationStatus(paymentAllocation.getPaymentAllocationStatus().getName())
            .build();
    }
    private BigDecimal getTotalHwfRemission(List<Remission> remissions) {
        return remissions != null ? remissions.stream().map(Remission::getHwfAmount).reduce(BigDecimal.ZERO, BigDecimal::add) : new BigDecimal("0.00");
    }

    private RemissionDto toRemissionDto(Remission remission) {
        return RemissionDto.remissionDtoWith()
            .remissionReference(remission.getRemissionReference())
            .beneficiaryName(remission.getBeneficiaryName())
            .ccdCaseNumber(remission.getCcdCaseNumber())
            .caseReference(remission.getCaseReference())
            .hwfReference(remission.getHwfReference())
            .hwfAmount(remission.getHwfAmount())
            .feeCode(remission.getFee().getCode())
            .dateCreated(remission.getDateCreated())
            .build();
    }

    private List<FeeDto> toFeeDtos(List<PaymentFee> paymentFees) {
        return paymentFees.stream().map(f -> toFeeDto(f)).collect(Collectors.toList());
    }

    private FeeDto toFeeDto(PaymentFee fee) {

        Optional<FeeVersionDto> optionalFeeVersionDto = feesService.getFeeVersion(fee.getCode(), fee.getVersion());
        LOG.info("Inside toFeeDto and amount due is: {}", fee.getAmountDue());
        return FeeDto.feeDtoWith()
            .calculatedAmount(fee.getCalculatedAmount())
            .code(fee.getCode())
            .netAmount(fee.getNetAmount())
            .version(fee.getVersion())
            .volume(fee.getVolume())
            .feeAmount(fee.getFeeAmount())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .reference(fee.getReference())
            .id(fee.getId())
            .memoLine(optionalFeeVersionDto.isPresent() ? optionalFeeVersionDto.get().getMemoLine() : null)
            .naturalAccountCode(optionalFeeVersionDto.isPresent() ? optionalFeeVersionDto.get().getNaturalAccountCode() : null)
            .description( optionalFeeVersionDto.isPresent() ? optionalFeeVersionDto.get().getDescription() : null)
            .allocatedAmount(fee.getAllocatedAmount())
            .apportionAmount(fee.getApportionAmount())
            .dateCreated(fee.getDateCreated())
            .dateUpdated(fee.getDateUpdated())
            .dateApportioned(fee.getDateApportioned())
            .amountDue(fee.getAmountDue())
            .build();
    }

    public PaymentFee toPaymentFee(FeeDto feeDto){
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
        return PaymentFee.feeWith()
            .code(feeDto.getCode())
            .version(feeDto.getVersion())
            .calculatedAmount(feeDto.getCalculatedAmount())
            .ccdCaseNumber(feeDto.getCcdCaseNumber())
            .volume(feeDto.getVolume())
            .feeAmount(feeDto.getFeeAmount())
            .netAmount(feeDto.getCalculatedAmount())
            .reference(feeDto.getReference())
            .dateCreated(apportionFeature ? timestamp: null)
            .build();
    }
}
