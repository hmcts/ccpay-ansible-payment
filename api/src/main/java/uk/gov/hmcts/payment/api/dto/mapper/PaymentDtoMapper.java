package uk.gov.hmcts.payment.api.dto.mapper;

import lombok.SneakyThrows;
import org.ff4j.FF4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.StatusHistoryDto;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.controllers.CardPaymentController;
import uk.gov.hmcts.payment.api.domain.service.PaymentDomainService;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.util.PayStatusToPayHubStatus;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PaymentDtoMapper {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentDtoMapper.class);

    @Autowired
    private FeesService feesService;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;



    public PaymentDto toCardPaymentDto(PaymentFeeLink paymentFeeLink) {
        Payment payment = paymentFeeLink.getPayments().get(0);
        return PaymentDto.payment2DtoWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .reference(payment.getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .dateCreated(payment.getDateCreated())
            .externalReference(payment.getExternalReference())
            .links(new PaymentDto.LinksDto(
                payment.getNextUrl() == null ? null : new PaymentDto.LinkDto(payment.getNextUrl(), "GET"),
                null, null
            ))
            .build();
    }

    public PaymentDto toCardPaymentDto(Payment payment, String paymentGroupReference) {

        return PaymentDto.payment2DtoWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .reference(payment.getReference())
            .paymentGroupReference(paymentGroupReference)
            .dateCreated(payment.getDateCreated())
            .externalReference(payment.getExternalReference())
            .links(new PaymentDto.LinksDto(
                payment.getNextUrl() == null ? null : new PaymentDto.LinkDto(payment.getNextUrl(), "GET"),
                null, null
            ))
            .build();
    }

    public PaymentDto toBulkScanPaymentDto(Payment payment, String paymentGroupReference) {

        return PaymentDto.payment2DtoWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .reference(payment.getReference())
            .paymentGroupReference(paymentGroupReference)
            .dateCreated(payment.getDateCreated())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .caseReference(payment.getCaseReference())
            .build();
    }

    public PaymentDto toBulkScanPaymentStrategicDto(Payment payment, String paymentGroupReference) {
        PaymentAllocation paymentAllocation = (payment.getPaymentAllocation() != null && payment.getPaymentAllocation().size() > 0) ?
            payment.getPaymentAllocation().get(0) : null;
        List<PaymentAllocationDto> paymentAllocationDtoList =  new ArrayList<>();
        paymentAllocationDtoList.add(toPaymentAllocationDto(paymentAllocation));

        return PaymentDto.payment2DtoWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .reference(payment.getReference())
            .paymentGroupReference(paymentGroupReference)
            .paymentAllocation(paymentAllocationDtoList)
            .dateCreated(payment.getDateCreated())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .caseReference(payment.getCaseReference())
            .build();
    }





    public PaymentDto toPciPalCardPaymentDto(PaymentFeeLink paymentFeeLink, String link) {
        Payment payment = paymentFeeLink.getPayments().get(0);
        return PaymentDto.payment2DtoWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .reference(payment.getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .fees(paymentFeeLink.getFees() != null ? toFeeDtos(paymentFeeLink.getFees()) : null)
            .dateCreated(payment.getDateCreated())
            .links(new PaymentDto.LinksDto(new PaymentDto.LinkDto(link, "GET"), null, null))
            .build();
    }

    public PaymentDto toPciPalCardPaymentDto(PaymentFeeLink paymentFeeLink, Payment payment, String link) {
        return PaymentDto.payment2DtoWith()
            .status(PayStatusToPayHubStatus.valueOf(payment.getStatus().toLowerCase()).getMappedStatus())
            .reference(payment.getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .fees(toFeeDtos(paymentFeeLink.getFees()))
            .dateCreated(payment.getDateCreated())
            .links(new PaymentDto.LinksDto(new PaymentDto.LinkDto(link, "GET"), null, null))
            .build();
    }

    public PaymentDto toResponseDto(PaymentFeeLink paymentFeeLink, Payment payment) {
        List<PaymentFee> fees = paymentFeeLink.getFees();
        return PaymentDto.payment2DtoWith()
            .reference(payment.getReference())
            .amount(payment.getAmount())
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .description(payment.getDescription())
            .channel(payment.getPaymentChannel() != null ? payment.getPaymentChannel().getName() : null)
            .method(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getName() : null)
            .externalReference(payment.getExternalReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .fees(toFeeDtos(fees))
            .links(payment.getReference() != null ? new PaymentDto.LinksDto(null,
                retrieveCardPaymentLink(payment.getReference()),
                null
            ) : null)
            .build();
    }

    public PaymentDto toRetrieveCardPaymentResponseDto(PaymentFeeLink paymentFeeLink) {
        Payment payment = paymentFeeLink.getPayments().get(0);
        List<PaymentFee> fees = paymentFeeLink.getFees();
        return PaymentDto.payment2DtoWith()
            .reference(payment.getReference())
            .amount(payment.getAmount())
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .description(payment.getDescription())
            .channel(payment.getPaymentChannel() != null ? payment.getPaymentChannel().getName() : null)
            .method(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getName() : null)
            .externalReference(payment.getExternalReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .fees(toFeeDtos(fees))
            .links(payment.getReference() != null ? new PaymentDto.LinksDto(null,
                retrieveCardPaymentLink(payment.getReference()),
                null
            ) : null)
            .build();
    }

    public PaymentDto toRetrievePaymentStatusesDto(PaymentFeeLink paymentFeeLink) {
        Payment payment = paymentFeeLink.getPayments().get(0);
        return PaymentDto.payment2DtoWith()
            .reference(payment.getReference())
            .amount(payment.getAmount())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .statusHistories(toStatusHistoryDtos(payment.getStatusHistories()))
            .build();
    }

    public PaymentDto toPaymentStatusesDto(Payment payment) {
        return PaymentDto.payment2DtoWith()
            .reference(payment.getReference())
            .amount(payment.getAmount())
            .paymentGroupReference(payment.getPaymentLink() != null ? payment.getPaymentLink().getPaymentReference() : null)
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .statusHistories(toStatusHistoryDtos(payment.getStatusHistories()))
            .build();
    }

    public PaymentDto toGetPaymentResponseDtos(Payment payment) {
        PaymentDto paymentDto = PaymentDto.payment2DtoWith()
            .paymentReference(payment.getReference())
            .paymentGroupReference(payment.getPaymentLink() != null ? payment.getPaymentLink().getPaymentReference() : null)
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .amount(payment.getAmount())
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .accountNumber(payment.getPbaNumber())
            .organisationName(payment.getOrganisationName())
            .customerReference(payment.getCustomerReference())
            .channel(payment.getPaymentChannel().getName())
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .statusHistories(payment.getStatusHistories() != null ? toStatusHistoryDtos(payment.getStatusHistories()) : null)
            .paymentAllocation(payment.getPaymentAllocation() != null ? toPaymentAllocationDtos(payment.getPaymentAllocation()) : null)
            .dateCreated(payment.getDateCreated())
            .dateUpdated(payment.getDateUpdated())
            .method(payment.getPaymentMethod().getName())
            .giroSlipNo(payment.getGiroSlipNo())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .bankedDate(payment.getBankedDate())
            .payerName(payment.getPayerName())
            .documentControlNumber(payment.getDocumentControlNumber())
            .externalReference(payment.getExternalReference())
            .reportedDateOffline(payment.getReportedDateOffline())
            .fees(toGetPaymentFeeDtos(payment.getPaymentLink() != null ? payment.getPaymentLink().getFees() : new ArrayList<>()))
            .build();
        return enrichWithFeeData(paymentDto);
    }

    public PaymentDto toReconciliationResponseDto(PaymentFeeLink paymentFeeLink) {
        Payment payment = paymentFeeLink.getPayments().get(0);
        PaymentDto paymentDto = PaymentDto.payment2DtoWith()
            .paymentReference(payment.getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .amount(payment.getAmount())
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .accountNumber(payment.getPbaNumber())
            .organisationName(payment.getOrganisationName())
            .customerReference(payment.getCustomerReference())
            .channel(payment.getPaymentChannel().getName())
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .statusHistories(payment.getStatusHistories() != null ? toStatusHistoryDtos(payment.getStatusHistories()) : null)
            .dateCreated(payment.getDateCreated())
            .dateUpdated(payment.getDateUpdated())
            .method(payment.getPaymentMethod().getName())
            .giroSlipNo(payment.getGiroSlipNo())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .externalReference(payment.getExternalReference())
            .reportedDateOffline(payment.getReportedDateOffline())
            .fees(toFeeDtos(paymentFeeLink.getFees()))
            .build();
        return enrichWithFeeData(paymentDto);
    }



    public PaymentDto toPaymentDto(Payment payment, PaymentFeeLink paymentFeeLink){
        PaymentDto paymentDto = PaymentDto.payment2DtoWith()
            .paymentReference(payment.getReference())
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .amount(payment.getAmount())
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .accountNumber(payment.getPbaNumber())
            .organisationName(payment.getOrganisationName())
            .customerReference(payment.getCustomerReference())
            .channel(payment.getPaymentChannel().getName())
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .statusHistories(payment.getStatusHistories() != null ? toStatusHistoryDtos(payment.getStatusHistories()) : null)
            .dateCreated(payment.getDateCreated())
            .dateUpdated(payment.getDateUpdated())
            .method(payment.getPaymentMethod().getName())
            .giroSlipNo(payment.getGiroSlipNo())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .externalReference(payment.getExternalReference())
            .reportedDateOffline(payment.getReportedDateOffline())
            .fees(toFeeDtos(paymentFeeLink.getFees()))
            .build();
        return enrichWithFeeData(paymentDto);
    }


    public PaymentDto toReconciliationResponseDtoForLibereta(final Payment payment, final String paymentReference, final List<PaymentFee> fees, final FF4j ff4j,boolean isPaymentAfterApportionment) {
        boolean isBulkScanPayment = payment.getPaymentChannel() !=null && payment.getPaymentChannel().getName().equals("bulk scan");
        boolean bulkScanCheck = ff4j.check("bulk-scan-check");
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
        boolean apportionCheck = apportionFeature && isPaymentAfterApportionment;
        LOG.info("bulkScanCheck value in PaymentDtoMapper: {}",bulkScanCheck);
        LOG.info("isBulkScanPayment value in PaymentDtoMapper: {}",isBulkScanPayment);
        LOG.info("apportionFeature value in PaymentDtoMapper: {}",apportionFeature);
        LOG.info("apportionCheck value in PaymentDtoMapper: {}",apportionCheck);
        PaymentDto paymentDto = PaymentDto.payment2DtoWith()
            .paymentReference(payment.getReference())
            .paymentGroupReference(apportionCheck ? null : paymentReference)
            .serviceName(payment.getServiceType())
            .siteId(payment.getSiteId())
            .amount(payment.getAmount())
            .caseReference(payment.getCaseReference())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .accountNumber(payment.getPbaNumber())
            .organisationName(payment.getOrganisationName())
            .customerReference(payment.getCustomerReference())
            .channel(payment.getPaymentChannel()!= null ? payment.getPaymentChannel().getName(): null)
            .currency(CurrencyCode.valueOf(payment.getCurrency()))
            .status(bulkScanCheck ? PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus().toLowerCase() : PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .dateCreated(payment.getDateCreated())
            .dateUpdated(payment.getDateUpdated())
            .method(payment.getPaymentMethod().getName())
            .bankedDate(payment.getBankedDate())
            .giroSlipNo(payment.getGiroSlipNo())
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .externalReference(isBulkScanPayment ? payment.getDocumentControlNumber() : payment.getExternalReference())
            .reportedDateOffline(payment.getPaymentChannel() != null && payment.getPaymentChannel().getName().equals("digital bar") ? payment.getReportedDateOffline() : null)
            .fees(isBulkScanPayment ? toFeeLiberataDtosWithCaseReference(fees,payment.getCaseReference(),apportionCheck) : toFeeLiberataDtos(fees,apportionCheck))
            .build();

        if (bulkScanCheck && isBulkScanPayment) {
            paymentDto.setPaymentAllocation(payment.getPaymentAllocation() != null ? toPaymentAllocationDtoForLibereta(payment.getPaymentAllocation()) : null);
        }
        return enrichWithFeeData(paymentDto);
    }


    private PaymentDto enrichWithFeeData(PaymentDto paymentDto) {
        LOG.info("Start of enrichWithFeeData!!!");
        paymentDto.getFees().forEach(fee -> {
            Optional<Map<String, Fee2Dto>> optFrFeeMap = Optional.ofNullable(feesService.getFeesDtoMap());
            if (optFrFeeMap.isPresent()) {
                LOG.info("Fee details retrieved from fees-register!!!");
                Map<String, Fee2Dto> frFeeMap = optFrFeeMap.get();

                if (frFeeMap.containsKey(fee.getCode())) {
                    Fee2Dto frFee = frFeeMap.get(fee.getCode());
                    fee.setJurisdiction1(frFee.getJurisdiction1Dto().getName());
                    fee.setJurisdiction2(frFee.getJurisdiction2Dto().getName());

                    Optional<FeeVersionDto> optionalFeeVersionDto = feesService.getFeeVersion(fee.getCode(), fee.getVersion());
                    if (optionalFeeVersionDto.isPresent()) {
                        fee.setMemoLine(optionalFeeVersionDto.get().getMemoLine());
                        fee.setNaturalAccountCode(optionalFeeVersionDto.get().getNaturalAccountCode());
                    }
                    LOG.info("End of enrichWithFeeData!!!");
                } else {
                    LOG.info("No fee found with the code: {}", fee.getCode());
                }
            }
        });
        return paymentDto;
    }

    private List<FeeDto> toFeeDtos(List<PaymentFee> fees) {
        return fees.stream().map(this::toFeeDto).collect(Collectors.toList());
    }

    private List<FeeDto> toFeeLiberataDtos(List<PaymentFee> fees,boolean apportionCheck) {
        return fees.stream().map(fee -> toFeeLiberataDto(fee,apportionCheck)).collect(Collectors.toList());
    }

    private List<FeeDto> toGetPaymentFeeDtos(List<PaymentFee> fees) {
        return fees.stream().map(this::toGetPaymentsFeeDto).collect(Collectors.toList());
    }


    private List<FeeDto> toFeeLiberataDtosWithCaseReference(List<PaymentFee> fees, String caseReference,boolean apportionCheck) {
        return fees.stream().map(fee -> toFeeLiberataDtoWithCaseReference(fee,caseReference,apportionCheck)).collect(Collectors.toList());
    }

    public List<PaymentFee> toFees(List<FeeDto> feeDtos) {
        return feeDtos.stream().map(this::toFee).collect(Collectors.toList());
    }

    public PaymentFee toFee(FeeDto feeDto) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
        return PaymentFee.feeWith()
            .calculatedAmount(feeDto.getCalculatedAmount())
            .code(feeDto.getCode())
            .netAmount(feeDto.getNetAmount())
            .version(feeDto.getVersion())
            .volume(feeDto.getVolume() == null ? 1 : feeDto.getVolume().intValue())
            .ccdCaseNumber(feeDto.getCcdCaseNumber())
            .reference(feeDto.getReference())
            .dateCreated(apportionFeature ? timestamp: null)
            .build();
    }


    private FeeDto toFeeDto(PaymentFee fee) {
        BigDecimal netAmount = fee.getNetAmount() != null ? fee.getNetAmount() : fee.getCalculatedAmount();
        BigDecimal calculatedAmount =  netAmount.equals(fee.getCalculatedAmount()) ? fee.getCalculatedAmount() : netAmount;

        return FeeDto.feeDtoWith()
            .id(fee.getId())
            .calculatedAmount(calculatedAmount)
            .code(fee.getCode())
            .netAmount(netAmount.equals(fee.getCalculatedAmount()) ? null : netAmount)
            .version(fee.getVersion())
            .volume(fee.getVolume())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .reference(fee.getReference())
            .build();
    }

    private FeeDto toFeeLiberataDto(PaymentFee fee,boolean apportionCheck) {
        LOG.info("Inside toFeeLiberataDto");
        BigDecimal netAmount = fee.getNetAmount() != null ? fee.getNetAmount() : fee.getCalculatedAmount();
        BigDecimal calculatedAmount =  netAmount.equals(fee.getCalculatedAmount()) ? fee.getCalculatedAmount() : netAmount;
        return FeeDto.feeDtoWith()
            .id(fee.getId())
            .calculatedAmount(calculatedAmount)
            .code(fee.getCode())
            .netAmount(netAmount.equals(fee.getCalculatedAmount()) ? null : netAmount)
            .version(fee.getVersion())
            .volume(fee.getVolume())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .reference(fee.getReference())
            .apportionedPayment(apportionCheck ? fee.getAllocatedAmount(): null)
            .dateReceiptProcessed(apportionCheck ? fee.getDateApportioned(): null)
            .paymentGroupReference(apportionCheck && fee.getPaymentLink() != null ? fee.getPaymentLink().getPaymentReference(): null)
            .build();
    }

    private FeeDto toGetPaymentsFeeDto(PaymentFee fee) {
        BigDecimal netAmount = fee.getNetAmount() != null ? fee.getNetAmount() : fee.getCalculatedAmount();
        BigDecimal calculatedAmount =  netAmount.equals(fee.getCalculatedAmount()) ? fee.getCalculatedAmount() : netAmount;

        return FeeDto.feeDtoWith()
            .id(fee.getId())
            .calculatedAmount(calculatedAmount)
            .code(fee.getCode())
            .netAmount(netAmount.equals(fee.getCalculatedAmount()) ? null : netAmount)
            .version(fee.getVersion())
            .volume(fee.getVolume())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .reference(fee.getReference())
            .allocatedAmount(fee.getAllocatedAmount())
            .apportionAmount(fee.getApportionAmount())
            .dateCreated(fee.getDateCreated())
            .dateUpdated(fee.getDateUpdated())
            .dateApportioned(fee.getDateApportioned())
            .build();
    }

    private FeeDto toFeeLiberataDtoWithCaseReference(PaymentFee fee, String caseReference,boolean apportionCheck) {

        BigDecimal netAmount = fee.getNetAmount() != null ? fee.getNetAmount() : fee.getCalculatedAmount();
        BigDecimal calculatedAmount =  netAmount.equals(fee.getCalculatedAmount()) ? fee.getCalculatedAmount() : netAmount;
        LOG.info("Inside toFeeLiberataDtoWithCaseReference");
        return FeeDto.feeDtoWith()
            .id(fee.getId())
            .calculatedAmount(calculatedAmount)
            .code(fee.getCode())
            .netAmount(netAmount.equals(fee.getCalculatedAmount()) ? null : netAmount)
            .version(fee.getVersion())
            .volume(fee.getVolume())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .caseReference(caseReference)
            .reference(fee.getReference())
            .apportionedPayment(apportionCheck ? fee.getAllocatedAmount(): null)
            .dateReceiptProcessed(apportionCheck ? fee.getDateApportioned(): null)
            .paymentGroupReference(apportionCheck && fee.getPaymentLink() != null ? fee.getPaymentLink().getPaymentReference(): null)
            .build();
    }

    private List<StatusHistoryDto> toStatusHistoryDtos(List<StatusHistory> statusHistories) {
        return statusHistories.stream().map(this::toStatusHistoryDto).collect(Collectors.toList());
    }

    private List<PaymentAllocationDto> toPaymentAllocationDtos(List<PaymentAllocation> paymentAllocation) {
        return paymentAllocation.stream().map(this::toPaymentAllocationDtos).collect(Collectors.toList());
    }

    private List<PaymentAllocationDto> toPaymentAllocationDtoForLibereta(List<PaymentAllocation> paymentAllocation) {
        return paymentAllocation.stream().map(this::toPaymentAllocationDtoForLibereta).collect(Collectors.toList());
    }

    private StatusHistoryDto toStatusHistoryDto(StatusHistory statusHistory) {
        return StatusHistoryDto.statusHistoryDtoWith()
            .status(statusHistory.getStatus())
            .externalStatus(statusHistory.getExternalStatus())
            .errorCode(statusHistory.getErrorCode())
            .errorMessage(statusHistory.getMessage())
            .dateCreated(statusHistory.getDateCreated())
            .build();
    }


    @SneakyThrows(NoSuchMethodException.class)
    private PaymentDto.LinkDto retrieveCardPaymentLink(String reference) {
        Method method = CardPaymentController.class.getMethod("retrieve", String.class);
        return new PaymentDto.LinkDto(WebMvcLinkBuilder.linkTo(method, reference).toString(), "GET");
    }

    public PaymentDto toCreateRecordPaymentResponse(PaymentFeeLink paymentFeeLink) {
        Payment payment = paymentFeeLink.getPayments().get(0);
        return PaymentDto.payment2DtoWith()
            .paymentGroupReference(paymentFeeLink.getPaymentReference())
            .status(PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .reference(payment.getReference())
            .dateCreated(payment.getDateCreated())
            .build();
    }

    public PaymentAllocationDto toPaymentAllocationDto(PaymentAllocation paymentAllocation) {
        return PaymentAllocationDto.paymentAllocationDtoWith()
            .paymentAllocationStatus(paymentAllocation.getPaymentAllocationStatus())
            .paymentGroupReference(paymentAllocation.getPaymentGroupReference())
            .paymentReference(paymentAllocation.getPaymentReference())
            .id(paymentAllocation.getId()!=null ? paymentAllocation.getId().toString():null)
            .dateCreated(paymentAllocation.getDateCreated())
            .reason(paymentAllocation.getReason())
            .explanation(paymentAllocation.getExplanation())
            .userName(paymentAllocation.getUserName())
            .receivingOffice(paymentAllocation.getReceivingOffice())
            .unidentifiedReason(paymentAllocation.getUnidentifiedReason())
            .build();
    }

    public PaymentAllocationDto toPaymentAllocationDtoForLibereta(PaymentAllocation paymentAllocation) {
        return PaymentAllocationDto.paymentAllocationDtoWith()
            .allocationStatus(paymentAllocation.getPaymentAllocationStatus() !=null ? paymentAllocation.getPaymentAllocationStatus().getName().toLowerCase():null)
            .allocationReason(paymentAllocation.getUnidentifiedReason())
            .dateCreated(paymentAllocation.getDateCreated())
            .receivingOffice(paymentAllocation.getReceivingOffice())
            .build();
    }

    public PaymentAllocationDto toPaymentAllocationDtos(PaymentAllocation paymentAllocation) {
        return PaymentAllocationDto.paymentAllocationDtoWith()
            .allocationStatus(paymentAllocation.getPaymentAllocationStatus().getName())
            .build();
    }

}
