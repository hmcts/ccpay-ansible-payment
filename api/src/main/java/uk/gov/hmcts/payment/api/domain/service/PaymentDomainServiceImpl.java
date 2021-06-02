package uk.gov.hmcts.payment.api.domain.service;

import org.ff4j.FF4j;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.FeeDto;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.ReconcilePaymentDto;
import uk.gov.hmcts.payment.api.dto.ReconcilePaymentResponse;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.Payment2Repository;
import uk.gov.hmcts.payment.api.model.PaymentAllocation;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.util.DateUtil;
import uk.gov.hmcts.payment.api.util.PayStatusToPayHubStatus;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PaymentDomainServiceImpl implements PaymentDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentDomainServiceImpl.class);

    @Autowired
    Payment2Repository payment2Repository;

    private DateTimeFormatter formatter = new DateUtil().getIsoDateTimeFormatter();

    @Autowired
    private PaymentValidator validator;
    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;
    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;
    @Autowired
    private PaymentDtoMapper paymentDtoMapper;
    @Autowired
    private FF4j ff4j;
    @Autowired
    private PaymentFeeRepository paymentFeeRepository;
    @Autowired
    private FeesService feesService;

    @Override
    public Payment getPaymentByApportionment(FeePayApportion feePayApportion) {
        return paymentService.getPaymentById(feePayApportion.getPaymentId());
    }

    @Override
    public Payment getPaymentByReference(String reference) {
        return paymentService.findSavedPayment(reference);
    }

    public List<FeePayApportion> getFeePayApportionByPaymentId(Integer paymentId) {
        return paymentService.findByPaymentId(paymentId);

    }

    public ReconcilePaymentResponse retrievePayments(Optional<String> startDateTimeString, Optional<String> endDateTimeString, Optional<String> paymentMethodType, Optional<String> serviceType, String pbaNumber, String ccdCaseNumber) {


        if (!ff4j.check("payment-search")) {
            throw new PaymentException("Payment search feature is not available for usage.");
        }

        validator.validate(paymentMethodType, startDateTimeString, endDateTimeString);

        Date fromDateTime = getFromDateTime(startDateTimeString);

        Date toDateTime = getToDateTime(endDateTimeString, fromDateTime);

        List<Payment> payments = paymentService
            .searchByCriteria(
                getSearchCriteria(paymentMethodType, pbaNumber, fromDateTime, toDateTime)
            );

        List<ReconcilePaymentDto> reconcilePaymentDtos = new ArrayList<>();

        List<Payment> filteredPayments = (serviceType.isPresent() && ccdCaseNumber != null) ?
            payments.stream()
                .filter(payment -> payment.getPaymentLink().getCcdCaseNumber().equals(ccdCaseNumber) && payment.getPaymentLink().getEnterpriseServiceName().equalsIgnoreCase(serviceType.get()))
                .collect(Collectors.toList()) : ccdCaseNumber != null ?
            payments.stream()
                .filter(payment -> payment.getPaymentLink().getCcdCaseNumber().equals(ccdCaseNumber))
                .collect(Collectors.toList()) : serviceType.isPresent() ?
            payments.stream()
                .filter(payment -> payment.getPaymentLink().getEnterpriseServiceName().equalsIgnoreCase(serviceType.get()))
                .collect(Collectors.toList()) : payments;

        populatePaymentDtos(reconcilePaymentDtos, filteredPayments);

        return new ReconcilePaymentResponse(reconcilePaymentDtos);
    }

    private Date getFromDateTime(@RequestParam(name = "start_date", required = false) Optional<String> startDateTimeString) {
        return Optional.ofNullable(startDateTimeString.map(formatter::parseLocalDateTime).orElse(null))
            .map(LocalDateTime::toDate)
            .orElse(null);
    }

    private Date getToDateTime(@RequestParam(name = "end_date", required = false) Optional<String> endDateTimeString, Date fromDateTime) {
        return Optional.ofNullable(endDateTimeString.map(formatter::parseLocalDateTime).orElse(null))
            .map(s -> fromDateTime != null && s.getHourOfDay() == 0 ? s.plusDays(1).minusSeconds(1).toDate() : s.toDate())
            .orElse(null);
    }

    private PaymentSearchCriteria getSearchCriteria(Optional<String> paymentMethodType, String pbaNumber, Date fromDateTime, Date toDateTime) {
        return PaymentSearchCriteria
            .searchCriteriaWith()
            .startDate(fromDateTime)
            .endDate(toDateTime)
            .pbaNumber(pbaNumber)
            .paymentMethod(paymentMethodType.map(value -> PaymentMethodType.valueOf(value.toUpperCase()).getType()).orElse(null))
            .build();
    }

    private void populatePaymentDtos(final List<ReconcilePaymentDto> paymentDtos, final List<Payment> payments) {
        //Adding this filter to exclude Exela payments if the bulk scan toggle feature is disabled.
        List<Payment> filteredPayments = getFilteredListBasedOnBulkScanToggleFeature(payments);
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature", false);

        LOG.info("BSP Feature ON : No of Payments retrieved for Liberata Pull : {}", payments.size());
        LOG.info("Apportion feature flag in liberata API: {}", apportionFeature);
        for (final Payment payment : filteredPayments) {
            final String paymentReference = payment.getPaymentLink() != null ? payment.getPaymentLink().getPaymentReference() : null;
            //Apportion logic added for pulling allocation amount
            populateApportionedFees(paymentDtos, payment.getPaymentLink(), apportionFeature, payment, paymentReference);
        }
    }

    private void populateApportionedFees(List<ReconcilePaymentDto> paymentDtos, PaymentFeeLink paymentFeeLink, boolean apportionFeature, Payment payment, String paymentReference) {
        boolean apportionCheck = payment.getPaymentChannel() != null
            && !payment.getPaymentChannel().getName().equalsIgnoreCase(paymentService.getServiceNameByCode("DIGITAL_BAR"));
        LOG.info("Apportion check value in liberata API: {}", apportionCheck);
        List<PaymentFee> fees = paymentFeeLink.getFees();
        boolean isPaymentAfterApportionment = false;
        if (apportionCheck && apportionFeature) {
            LOG.info("Apportion check and feature passed");
            final List<FeePayApportion> feePayApportionList = paymentService.findByPaymentId(payment.getId());
            if (feePayApportionList != null && !feePayApportionList.isEmpty()) {
                LOG.info("Apportion details available in PaymentController");
                fees = new ArrayList<>();
                getApportionedDetails(fees, feePayApportionList);
                isPaymentAfterApportionment = true;
            }
        }
        //End of Apportion logic
        final ReconcilePaymentDto paymentDto = toReconciliationResponseDtoForLiberetaAfterOrders(payment, paymentReference, fees, ff4j, isPaymentAfterApportionment);
        paymentDtos.add(paymentDto);
    }


    private void getApportionedDetails(List<PaymentFee> fees, List<FeePayApportion> feePayApportionList) {
        LOG.info("Getting Apportionment Details!!!");
        for (FeePayApportion feePayApportion : feePayApportionList) {
            Optional<PaymentFee> apportionedFee = paymentFeeRepository.findById(feePayApportion.getFeeId());
            if (apportionedFee.isPresent()) {
                LOG.info("Apportioned fee is present");
                PaymentFee fee = apportionedFee.get();
                if (feePayApportion.getApportionAmount() != null) {
                    LOG.info("Apportioned Amount is available!!!");
                    BigDecimal allocatedAmount = feePayApportion.getApportionAmount()
                        .add(feePayApportion.getCallSurplusAmount() != null
                            ? feePayApportion.getCallSurplusAmount()
                            : BigDecimal.valueOf(0));
                    LOG.info("Allocated amount in PaymentController: {}", allocatedAmount);
                    fee.setAllocatedAmount(allocatedAmount);
                    fee.setDateApportioned(feePayApportion.getDateCreated());
                }
                fees.add(fee);
            }
        }
    }

    private List<Payment> getFilteredListBasedOnBulkScanToggleFeature(List<Payment> payments) {
        payments = getPayments(payments);
        return payments;
    }

    private List<Payment> getPayments(List<Payment> payments) {
        boolean bulkScanCheck = ff4j.check("bulk-scan-check");
        LOG.info("bulkScanCheck value: {}", bulkScanCheck);
        if (!bulkScanCheck) {
            LOG.info("BSP Feature OFF : No of Payments retrieved for Liberata Pull : {}", payments.size());
            payments = Optional.ofNullable(payments)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(payment -> Objects.nonNull(payment.getPaymentChannel()))
                .filter(payment -> Objects.nonNull(payment.getPaymentChannel().getName()))
                .filter(payment -> !payment.getPaymentChannel().getName().equalsIgnoreCase("bulk scan"))
                .collect(Collectors.toList());
        }
        return payments;
    }

    private ReconcilePaymentDto toReconciliationResponseDtoForLiberetaAfterOrders(Payment payment, String paymentReference, List<PaymentFee> fees, FF4j ff4j, Boolean isPaymentAfterApportionment) {
        boolean isBulkScanPayment = payment.getPaymentChannel() != null && payment.getPaymentChannel().getName().equals("bulk scan");
        boolean bulkScanCheck = ff4j.check("bulk-scan-check");
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature", false);
        boolean apportionCheck = apportionFeature && isPaymentAfterApportionment;
        LOG.info("bulkScanCheck value in PaymentDtoMapper: {}", bulkScanCheck);
        LOG.info("isBulkScanPayment value in PaymentDtoMapper: {}", isBulkScanPayment);
        LOG.info("apportionFeature value in PaymentDtoMapper: {}", apportionFeature);
        LOG.info("apportionCheck value in PaymentDtoMapper: {}", apportionCheck);
        ReconcilePaymentDto paymentDto = ReconcilePaymentDto.reconcilePaymentDtoWith()
            .paymentReference(payment.getReference())
            .paymentGroupReference(apportionCheck ? null : paymentReference)
            .serviceName(payment.getPaymentLink().getEnterpriseServiceName())
            .siteId(payment.getPaymentLink().getOrgId())
            .amount(payment.getAmount())
            .caseReference(payment.getPaymentLink().getCaseReference())
            .ccdCaseNumber(payment.getPaymentLink().getCcdCaseNumber())
            .accountNumber(payment.getPbaNumber())
            .organisationName(payment.getOrganisationName())
            .customerReference(payment.getCustomerReference())
            .channel(payment.getPaymentChannel() != null ? payment.getPaymentChannel().getName() : null)
            .currency(payment.getCurrency())
            .status(bulkScanCheck ? PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus().toLowerCase() : PayStatusToPayHubStatus.valueOf(payment.getPaymentStatus().getName()).getMappedStatus())
            .dateCreated(payment.getDateCreated())
            .dateUpdated(payment.getDateUpdated())
            .method(payment.getPaymentMethod().getName())
            .bankedDate(payment.getBankedDate())
            .giroSlipNo(payment.getGiroSlipNo())
            .reportedDateOffline(payment.getPaymentChannel() != null && payment.getPaymentChannel().getName().equals("digital bar") ? payment.getReportedDateOffline() : null)
            .externalProvider(payment.getPaymentProvider() != null ? payment.getPaymentProvider().getName() : null)
            .externalReference(isBulkScanPayment ? payment.getDocumentControlNumber() : payment.getExternalReference())
            .fees(isBulkScanPayment ? toFeeLiberataDtosWithCaseReference(fees, payment.getCaseReference(), apportionCheck) : toFeeLiberataDtos(fees, apportionCheck))
            .build();

        if (bulkScanCheck && isBulkScanPayment) {
            paymentDto.setPaymentAllocation(payment.getPaymentAllocation() != null ? toPaymentAllocationDtoForLibereta(payment.getPaymentAllocation()) : null);
        }
        return enrichWithFeeData(paymentDto);
    }


    private ReconcilePaymentDto enrichWithFeeData(ReconcilePaymentDto paymentDto) {
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


    private List<FeeDto> toFeeLiberataDtosWithCaseReference(List<PaymentFee> fees, String caseReference, boolean apportionCheck) {
        return fees.stream().map(fee -> toFeeLiberataDtoWithCaseReference(fee, caseReference, apportionCheck)).collect(Collectors.toList());
    }

    private FeeDto toFeeLiberataDtoWithCaseReference(PaymentFee fee, String caseReference, boolean apportionCheck) {

        BigDecimal netAmount = fee.getNetAmount() != null ? fee.getNetAmount() : fee.getCalculatedAmount();
        BigDecimal calculatedAmount = netAmount.equals(fee.getCalculatedAmount()) ? fee.getCalculatedAmount() : netAmount;
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
            .apportionedPayment(apportionCheck ? fee.getAllocatedAmount() : null)
            .dateReceiptProcessed(apportionCheck ? fee.getDateApportioned() : null)
            .paymentGroupReference(apportionCheck && fee.getPaymentLink() != null ? fee.getPaymentLink().getPaymentReference() : null)
            .build();
    }

    private List<FeeDto> toFeeLiberataDtos(List<PaymentFee> fees, boolean apportionCheck) {
        return fees.stream().map(fee -> toFeeLiberataDto(fee, apportionCheck)).collect(Collectors.toList());
    }

    private FeeDto toFeeLiberataDto(PaymentFee fee, boolean apportionCheck) {
        LOG.info("Inside toFeeLiberataDto");
        BigDecimal netAmount = fee.getNetAmount() != null ? fee.getNetAmount() : fee.getCalculatedAmount();
        BigDecimal calculatedAmount = netAmount.equals(fee.getCalculatedAmount()) ? fee.getCalculatedAmount() : netAmount;
        return FeeDto.feeDtoWith()
            .id(fee.getId())
            .calculatedAmount(calculatedAmount)
            .code(fee.getCode())
            .netAmount(netAmount.equals(fee.getCalculatedAmount()) ? null : netAmount)
            .version(fee.getVersion())
            .volume(fee.getVolume())
            .ccdCaseNumber(fee.getCcdCaseNumber())
            .reference(fee.getReference())
            .apportionedPayment(apportionCheck ? fee.getAllocatedAmount() : null)
            .dateReceiptProcessed(apportionCheck ? fee.getDateApportioned() : null)
            .paymentGroupReference(apportionCheck && fee.getPaymentLink() != null ? fee.getPaymentLink().getPaymentReference() : null)
            .build();
    }

    private List<PaymentAllocationDto> toPaymentAllocationDtoForLibereta(List<PaymentAllocation> paymentAllocation) {
        return paymentAllocation.stream().map(this::toPaymentAllocationDtoForLibereta).collect(Collectors.toList());
    }

    private PaymentAllocationDto toPaymentAllocationDtoForLibereta(PaymentAllocation paymentAllocation) {
        return PaymentAllocationDto.paymentAllocationDtoWith()
            .allocationStatus(paymentAllocation.getPaymentAllocationStatus() != null ? paymentAllocation.getPaymentAllocationStatus().getName().toLowerCase() : null)
            .allocationReason(paymentAllocation.getUnidentifiedReason())
            .dateCreated(paymentAllocation.getDateCreated())
            .receivingOffice(paymentAllocation.getReceivingOffice())
            .build();

    }
}
