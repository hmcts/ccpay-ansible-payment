package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.ff4j.FF4j;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.UpdatePaymentRequest;
import uk.gov.hmcts.payment.api.contract.util.Service;
import uk.gov.hmcts.payment.api.dto.*;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;
import uk.gov.hmcts.payment.api.service.CallbackService;
import uk.gov.hmcts.payment.api.service.IacService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.util.DateUtil;
import uk.gov.hmcts.payment.api.util.OrderCaseUtil;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.PATCH;

@RestController
@Api(tags = {"Payment"})
@SwaggerDefinition(tags = {@Tag(name = "PaymentController", description = "Payment REST API")})
public class PaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService<PaymentFeeLink, String> paymentService;
    private final CallbackService callbackService;
    private final PaymentStatusRepository paymentStatusRepository;
    private final PaymentDtoMapper paymentDtoMapper;
    private final PaymentValidator validator;
    private final FF4j ff4j;
    private final DateTimeFormatter formatter;
    private final PaymentFeeRepository paymentFeeRepository;
    private final LaunchDarklyFeatureToggler featureToggler;

    @Autowired
    private OrderCaseUtil orderCaseUtil;

    @Autowired
    private IacService iacService;

    @Autowired
    public PaymentController(PaymentService<PaymentFeeLink, String> paymentService,
                             PaymentStatusRepository paymentStatusRepository, CallbackService callbackService,
                             PaymentDtoMapper paymentDtoMapper, PaymentValidator paymentValidator, FF4j ff4j,
                             DateUtil dateUtil, PaymentFeeRepository paymentFeeRepository,
                             LaunchDarklyFeatureToggler featureToggler) {
        this.paymentService = paymentService;
        this.callbackService = callbackService;
        this.paymentStatusRepository = paymentStatusRepository;
        this.paymentDtoMapper = paymentDtoMapper;
        this.validator = paymentValidator;
        this.ff4j = ff4j;
        this.formatter = dateUtil.getIsoDateTimeFormatter();
        this.paymentFeeRepository = paymentFeeRepository;
        this.featureToggler = featureToggler;
    }

    @ApiOperation(value = "Update case reference by payment reference", notes = "Update case reference by payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "No content"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @RequestMapping(value = "/payments/{reference}", method = PATCH)
    @Transactional
    public ResponseEntity updateCaseReference(@PathVariable("reference") String reference,
                                              @RequestBody @Validated UpdatePaymentRequest request) {
        Optional<Payment> payment = getPaymentByReference(reference);

        if (payment.isPresent()) {
            if (request.getCaseReference() != null) {
                payment.get().setCaseReference(request.getCaseReference());
            }
            if (request.getCcdCaseNumber() != null) {
                payment.get().setCcdCaseNumber(request.getCcdCaseNumber());
            }

            orderCaseUtil.updateOrderCaseDetails(payment.get().getPaymentLink(), payment.get());

            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @ApiOperation(value = "Get payments for between dates", notes = "Get list of payments. You can optionally provide start date and end dates which can include times as well. Following are the supported date/time formats. These are yyyy-MM-dd, dd-MM-yyyy," +
        "yyyy-MM-dd HH:mm:ss, dd-MM-yyyy HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss, dd-MM-yyyy'T'HH:mm:ss")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payments retrieved"),
        @ApiResponse(code = 400, message = "Bad request")
    })
    @GetMapping(value = "/payments")
    @PaymentExternalAPI
    public PaymentsResponse retrievePayments(@RequestParam(name = "start_date", required = false) Optional<String> startDateTimeString,
                                             @RequestParam(name = "end_date", required = false) Optional<String> endDateTimeString,
                                             @RequestParam(name = "payment_method", required = false) Optional<String> paymentMethodType,
                                             @RequestParam(name = "service_name", required = false) Optional<String> serviceType,
                                             @RequestParam(name = "ccd_case_number", required = false) String ccdCaseNumber,
                                             @RequestParam(name = "pba_number", required = false) String pbaNumber
    ) {

        validatePullRequest(startDateTimeString, endDateTimeString, paymentMethodType, serviceType);

        Date fromDateTime = getFromDateTime(startDateTimeString);

        Date toDateTime = getToDateTime(endDateTimeString, fromDateTime);

        List<PaymentFeeLink> paymentFeeLinks = paymentService
            .search(
                getSearchCriteria(paymentMethodType, serviceType, ccdCaseNumber, pbaNumber, fromDateTime, toDateTime)
            );

        final List<PaymentDto> paymentDtos = new ArrayList<>();
        LOG.info("No of paymentFeeLinks retrieved for Liberata Pull : {}", paymentFeeLinks.size());
        for (final PaymentFeeLink paymentFeeLink : paymentFeeLinks) {
            populatePaymentDtos(paymentDtos, paymentFeeLink, fromDateTime, toDateTime);
        }
        return new PaymentsResponse(paymentDtos);
    }

    @ApiOperation(value = "Get payments for Reconciliation for between dates", notes = "Get list of payments. You can optionally provide start date and end dates which can include times as well. Following are the supported date/time formats. These are yyyy-MM-dd, dd-MM-yyyy," +
        "yyyy-MM-dd HH:mm:ss, dd-MM-yyyy HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss, dd-MM-yyyy'T'HH:mm:ss")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payments retrieved"),
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 206, message = "Supplementary details partially retrieved"),
    })
    @GetMapping(value = "/reconciliation-payments")
    @PaymentExternalAPI
    public ResponseEntity retrievePaymentsWithApportion(@RequestParam(name = "start_date", required = false) Optional<String> startDateTimeString,
                                                          @RequestParam(name = "end_date", required = false) Optional<String> endDateTimeString,
                                                          @RequestParam(name = "payment_method", required = false) Optional<String> paymentMethodType,
                                                          @RequestParam(name = "service_name", required = false) Optional<String> serviceType,
                                                          @RequestParam(name = "ccd_case_number", required = false) String ccdCaseNumber,
                                                          @RequestParam(name = "pba_number", required = false) String pbaNumber
    ) {

        validatePullRequest(startDateTimeString, endDateTimeString, paymentMethodType, serviceType);

        Date fromDateTime = getFromDateTime(startDateTimeString);

        Date toDateTime = getToDateTime(endDateTimeString, fromDateTime);

        List<Payment> payments = paymentService
            .searchByCriteria(
                getSearchCriteria(paymentMethodType, serviceType, ccdCaseNumber, pbaNumber, fromDateTime, toDateTime)
            );

        final List<PaymentDto> paymentDtos = new ArrayList<>();
        LOG.info("No of paymentFeeLinks retrieved for Liberata Pull : {}", payments.size());
        populatePaymentDtos(paymentDtos, payments);

        Optional<Payment> iacPaymentAny = payments.stream()
            .filter(p -> p.getServiceType().equalsIgnoreCase(Service.IAC.getName())).findAny();
        boolean iacSupplementaryDetailsFeature = featureToggler.getBooleanValue("iac-supplementary-details-feature",false);
        LOG.info("IAC Supplementary Details feature flag in liberata API: {}", iacSupplementaryDetailsFeature);
        LOG.info("Is any IAC payment present: {}", iacPaymentAny.isPresent());

        if(iacPaymentAny.isPresent() && iacSupplementaryDetailsFeature){
            return iacService.getIacSupplementaryInfo(paymentDtos,Service.IAC.getName());
        }

        return new ResponseEntity(new PaymentsResponse(paymentDtos),HttpStatus.OK);

    }

    @ApiOperation(value = "Update payment status by payment reference", notes = "Update payment status by payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "No content"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @PaymentExternalAPI
    @PatchMapping("/payments/{reference}/status/{status}")
    @Transactional
    public ResponseEntity updatePaymentStatus(@PathVariable(value = "reference", required = true) String reference,
                                              @PathVariable(value = "status", required = true) String status) {
        Optional<Payment> payment = getPaymentByReference(reference);

        if (payment.isPresent()) {
            payment.get().setPaymentStatus(paymentStatusRepository.findByNameOrThrow(status));
            if (payment.get().getServiceCallbackUrl() != null) {
                callbackService.callback(payment.get().getPaymentLink(), payment.get());
            }
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @ApiOperation(value = "Get payment details by payment reference", notes = "Get payment details for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment retrieved"),
        @ApiResponse(code = 403, message = "Payment info forbidden"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @GetMapping(value = "/payments/{reference}")
    public PaymentDto retrievePayment(@PathVariable("reference") String paymentReference) {

        PaymentFeeLink paymentFeeLink = paymentService.retrieve(paymentReference);
        Optional<Payment> payment = paymentFeeLink.getPayments().stream()
            .filter(p -> p.getReference().equals(paymentReference)).findAny();
        Payment payment1 = null;
        if (payment.isPresent()) {
            payment1 = payment.get();
        }
        return paymentDtoMapper.toGetPaymentResponseDtos(payment1);
    }

    private PaymentSearchCriteria getSearchCriteria(@RequestParam(name = "payment_method", required = false) Optional<String> paymentMethodType, @RequestParam(name = "service_name", required = false) Optional<String> serviceType, @RequestParam(name = "ccd_case_number", required = false) String ccdCaseNumber, @RequestParam(name = "pba_number", required = false) String pbaNumber, Date fromDateTime, Date toDateTime) {
        return PaymentSearchCriteria
            .searchCriteriaWith()
            .startDate(fromDateTime)
            .endDate(toDateTime)
            .ccdCaseNumber(ccdCaseNumber)
            .pbaNumber(pbaNumber)
            .paymentMethod(paymentMethodType.map(value -> PaymentMethodType.valueOf(value.toUpperCase()).getType()).orElse(null))
            .serviceType(serviceType.orElse(null))
            .build();
    }

    private Date getFromDateTime(@RequestParam(name = "start_date", required = false) Optional<String> startDateTimeString) {
        return Optional.ofNullable(startDateTimeString.map(formatter::parseLocalDateTime).orElse(null))
            .map(LocalDateTime::toDate)
            .orElse(null);
    }

    private void validatePullRequest(@RequestParam(name = "start_date", required = false) Optional<String> startDateTimeString, @RequestParam(name = "end_date", required = false) Optional<String> endDateTimeString, @RequestParam(name = "payment_method", required = false) Optional<String> paymentMethodType, @RequestParam(name = "service_name", required = false) Optional<String> serviceType) {
        if (!ff4j.check("payment-search")) {
            throw new PaymentException("Payment search feature is not available for usage.");
        }

        validator.validate(paymentMethodType, startDateTimeString, endDateTimeString);
    }

    private Date getToDateTime(@RequestParam(name = "end_date", required = false) Optional<String> endDateTimeString, Date fromDateTime) {
        return Optional.ofNullable(endDateTimeString.map(formatter::parseLocalDateTime).orElse(null))
            .map(s -> fromDateTime != null && s.getHourOfDay() == 0 ? s.plusDays(1).minusSeconds(1).toDate() : s.toDate())
            .orElse(null);
    }

    private Optional<Payment> getPaymentByReference(String reference) {
        PaymentFeeLink paymentFeeLink = paymentService.retrieve(reference);
        return paymentFeeLink.getPayments().stream()
            .filter(p -> p.getReference().equals(reference)).findAny();
    }

    private void populatePaymentDtos(final List<PaymentDto> paymentDtos, final PaymentFeeLink paymentFeeLink, Date fromDateTime, Date toDateTime) {
        //Adding this filter to exclude Exela payments if the bulk scan toggle feature is disabled.
        List<Payment> payments = getFilteredListBasedOnBulkScanToggleFeature(paymentFeeLink);

        //Additional filter to retrieve payments within specified Date Range for Liberata Pull
        if (null != fromDateTime && null != toDateTime) {
            payments = payments.stream().filter(payment -> (payment.getDateUpdated().compareTo(fromDateTime) >= 0
                && payment.getDateUpdated().compareTo(toDateTime) <= 0))
                .collect(Collectors.toList());
        } else if (null != fromDateTime) {
            payments = payments.stream().filter(payment -> (payment.getDateUpdated().compareTo(fromDateTime) >= 0))
                .collect(Collectors.toList());
        } else if (null != toDateTime) {
            payments = payments.stream().filter(payment -> (payment.getDateUpdated().compareTo(toDateTime) <= 0))
                .collect(Collectors.toList());
        }

        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature", false);

        LOG.info("BSP Feature ON : No of Payments retrieved for Liberata Pull : {}", payments.size());
        LOG.info("Apportion feature flag in liberata API: {}", apportionFeature);
        for (final Payment payment : payments) {
            final String paymentReference = paymentFeeLink.getPaymentReference();
            //Apportion logic added for pulling allocation amount
            populateApportionedFees(paymentDtos, paymentFeeLink, apportionFeature, payment, paymentReference);
        }
    }

    private void populatePaymentDtos(final List<PaymentDto> paymentDtos, final List<Payment> payments) {
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

    private void populateApportionedFees(List<PaymentDto> paymentDtos, PaymentFeeLink paymentFeeLink, boolean apportionFeature, Payment payment, String paymentReference) {
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
        final PaymentDto paymentDto = paymentDtoMapper.toReconciliationResponseDtoForLibereta(payment, paymentReference, fees, ff4j, isPaymentAfterApportionment);
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

    private List<Payment> getFilteredListBasedOnBulkScanToggleFeature(PaymentFeeLink paymentFeeLink) {
        List<Payment> payments = paymentFeeLink.getPayments();
        payments = getPayments(payments);
        return payments;
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

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PaymentNotFoundException.class)
    public String notFound(PaymentNotFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(PaymentException.class)
    public String return400(PaymentException ex) {
        return ex.getMessage();
    }
}
