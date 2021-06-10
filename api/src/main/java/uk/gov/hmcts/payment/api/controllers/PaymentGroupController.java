package uk.gov.hmcts.payment.api.controllers;

import com.google.common.collect.Lists;
import io.swagger.annotations.*;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.http.MethodNotSupportedException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.PaymentAllocationDto;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.TelephonyPaymentRequest;
import uk.gov.hmcts.payment.api.dto.*;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentGroupDtoMapper;
import uk.gov.hmcts.payment.api.exceptions.OrderReferenceNotFoundException;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.*;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;
import uk.gov.hmcts.payment.api.v1.model.exceptions.*;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.payment.api.contract.TelephonyCardPaymentsRequest;
import uk.gov.hmcts.payment.api.contract.TelephonyCardPaymentsResponse;
import uk.gov.hmcts.payment.api.dto.mapper.TelephonyDtoMapper;
import uk.gov.hmcts.payment.api.external.client.dto.TelephonyProviderAuthorisationResponse;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@Api(tags = {"Payment group"})
@SwaggerDefinition(tags = {@Tag(name = "PaymentGroupController", description = "Payment group REST API")})
public class PaymentGroupController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentGroupController.class);
    private static final String APPORTION_FEATURE = "apportion-feature";
    private final PaymentGroupService<PaymentFeeLink, String> paymentGroupService;
    private final PaymentGroupDtoMapper paymentGroupDtoMapper;
    private final DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;
    private final PaymentDtoMapper paymentDtoMapper;
    private final PciPalPaymentService pciPalPaymentService;
    private final ReferenceUtil referenceUtil;
    private final ReferenceDataService<SiteDTO> referenceDataService;
    private final PaymentProviderRepository paymentProviderRepository;
    private final FeePayApportionService feePayApportionService;
    private final LaunchDarklyFeatureToggler featureToggler;
    private final Payment2Repository payment2Repository;

    private final TelephonyDtoMapper telephonyDtoMapper;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    @Autowired()
    @Qualifier("restTemplatePaymentGroup")
    private RestTemplate restTemplatePaymentGroup;

    @Value("${bulk.scanning.payments.processed.url}")
    private String bulkScanPaymentsProcessedUrl;

    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;

    @Autowired
    public PaymentGroupController(PaymentGroupService paymentGroupService, PaymentGroupDtoMapper paymentGroupDtoMapper,
                                  DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService,
                                  PaymentDtoMapper paymentDtoMapper, PciPalPaymentService pciPalPaymentService,
                                  ReferenceUtil referenceUtil,
                                  ReferenceDataService<SiteDTO> referenceDataService,
                                  PaymentProviderRepository paymentProviderRespository,
                                  FeePayApportionService feePayApportionService,
                                  LaunchDarklyFeatureToggler featureToggler,
                                  FeePayApportionRepository feePayApportionRepository,
                                  Payment2Repository payment2Repository,
                                  TelephonyDtoMapper telephonyDtoMapper) {


        this.paymentGroupService = paymentGroupService;
        this.paymentGroupDtoMapper = paymentGroupDtoMapper;
        this.delegatingPaymentService = delegatingPaymentService;
        this.paymentDtoMapper = paymentDtoMapper;
        this.pciPalPaymentService = pciPalPaymentService;
        this.referenceUtil = referenceUtil;
        this.referenceDataService = referenceDataService;
        this.paymentProviderRepository = paymentProviderRespository;
        this.feePayApportionService = feePayApportionService;
        this.featureToggler = featureToggler;
        this.payment2Repository = payment2Repository;
        this.telephonyDtoMapper = telephonyDtoMapper;
    }

    @ApiOperation(value = "Get payments/remissions/fees details by payment group reference", notes = "Get payments/remissions/fees details for supplied payment group reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment retrieved"),
        @ApiResponse(code = 403, message = "Payment info forbidden"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @GetMapping(value = "/payment-groups/{payment-group-reference}")
    public ResponseEntity<PaymentGroupDto> retrievePayment(@PathVariable("payment-group-reference") String paymentGroupReference) {
        PaymentFeeLink paymentFeeLink = paymentGroupService.findByPaymentGroupReference(paymentGroupReference);

        return new ResponseEntity<>(paymentGroupDtoMapper.toPaymentGroupDto(paymentFeeLink), HttpStatus.OK);
    }

    @ApiOperation(value = "Add Payment Group with Fees", notes = "Add Payment Group with Fees")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Payment group with fee(s) created"),
        @ApiResponse(code = 400, message = "Payment group creation failed")
    })
    @PostMapping(value = "/payment-groups")
    public ResponseEntity<PaymentGroupDto> addNewFee(@Valid @RequestBody PaymentGroupDto paymentGroupDto) {

        String paymentGroupReference = PaymentReference.getInstance().getNext();

        paymentGroupDto.getFees().stream().forEach(f -> {
            if (f.getCcdCaseNumber() == null && f.getReference() == null) {
                throw new InvalidFeeRequestException("Either ccdCaseNumber or caseReference is required.");
            }
        });

        List<PaymentFee> feeList = paymentGroupDto.getFees().stream()
            .map(paymentGroupDtoMapper::toPaymentFee).collect(Collectors.toList());

        PaymentFeeLink feeLink = PaymentFeeLink.paymentFeeLinkWith()
            .paymentReference(paymentGroupReference)
            .fees(Lists.newArrayList(feeList))
            .build();
        feeList.stream().forEach(fee -> fee.setPaymentLink(feeLink));

        PaymentFeeLink paymentFeeLink = paymentGroupService.addNewFeeWithPaymentGroup(feeLink);

        return new ResponseEntity<>(paymentGroupDtoMapper.toPaymentGroupDto(paymentFeeLink), HttpStatus.CREATED);
    }


    @ApiOperation(value = "Add new Fee(s) to existing Payment Group", notes = "Add new Fee(s) to existing Payment Group")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Fee(s) added to Payment Group"),
        @ApiResponse(code = 400, message = "Payment group creation failed"),
        @ApiResponse(code = 404, message = "Payment Group not found")
    })
    @PutMapping(value = "/payment-groups/{payment-group-reference}")
    public ResponseEntity<PaymentGroupDto> addNewFeetoPaymentGroup(@PathVariable("payment-group-reference") String paymentGroupReference,
                                                                   @Valid @RequestBody PaymentGroupDto paymentGroupDto) {

        paymentGroupDto.getFees().stream().forEach(f -> {
            if (f.getCcdCaseNumber() == null && f.getReference() == null) {
                throw new InvalidFeeRequestException("Either ccdCaseNumber or caseReference is required.");
            }
        });

        PaymentFeeLink paymentFeeLink = paymentGroupService.
            addNewFeetoExistingPaymentGroup(paymentGroupDto.getFees().stream()
                .map(paymentGroupDtoMapper::toPaymentFee).collect(Collectors.toList()), paymentGroupReference);

        return new ResponseEntity<>(paymentGroupDtoMapper.toPaymentGroupDto(paymentFeeLink), HttpStatus.OK);
    }

    @ApiOperation(value = "Create card payment in Payment Group", notes = "Create card payment in Payment Group")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Payment created"),
        @ApiResponse(code = 400, message = "Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/payment-groups/{payment-group-reference}/card-payments")
    @ResponseBody
    @Transactional
    public ResponseEntity<PaymentDto> createCardPayment(
        @RequestHeader(value = "return-url") String returnURL,
        @RequestHeader(value = "service-callback-url", required = false) String serviceCallbackUrl,
        @PathVariable("payment-group-reference") String paymentGroupReference,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @Valid @RequestBody TelephonyPaymentRequest request) throws CheckDigitException, MethodNotSupportedException {

        OrganisationalServiceDto organisationalServiceDto = referenceDataService.getOrganisationalDetail(request.getCaseType(), headers);

        PaymentServiceRequest paymentServiceRequest = PaymentServiceRequest.paymentServiceRequestWith()
            .description(request.getDescription())
            .paymentGroupReference(paymentGroupReference)
            .paymentReference(referenceUtil.getNext("RC"))
            .returnUrl(returnURL)
            .ccdCaseNumber(request.getCcdCaseNumber())
            .caseReference(request.getCaseReference())
            .currency(request.getCurrency().getCode())
            .siteId(organisationalServiceDto.getServiceCode())
            .serviceType(organisationalServiceDto.getServiceDescription())
            .amount(request.getAmount())
            .serviceCallbackUrl(serviceCallbackUrl)
            .channel(request.getChannel())
            .provider(request.getProvider())
            .build();
        LOG.info("Inside createCardPayment");

        PaymentFeeLink paymentLink = delegatingPaymentService.update(paymentServiceRequest);
        Payment payment = getPayment(paymentLink, paymentServiceRequest.getPaymentReference());
        PaymentDto paymentDto = paymentDtoMapper.toCardPaymentDto(payment, paymentGroupReference);

        if (request.getChannel().equals("telephony") && request.getProvider().equals("pci pal")) {
            LOG.info("Inside if loop");
            PciPalPaymentRequest pciPalPaymentRequest = PciPalPaymentRequest.pciPalPaymentRequestWith().orderAmount(request.getAmount().toString()).orderCurrency(request.getCurrency().getCode())
                .orderReference(paymentDto.getReference()).build();
            pciPalPaymentRequest.setCustomData2(payment.getCcdCaseNumber());
            String link = pciPalPaymentService.getPciPalLink(pciPalPaymentRequest, paymentServiceRequest.getServiceType());
            paymentDto = paymentDtoMapper.toPciPalCardPaymentDto(paymentLink, payment, link);
        }

        // trigger Apportion based on the launch darkly feature flag
        boolean apportionFeature = featureToggler.getBooleanValue(APPORTION_FEATURE, false);
        LOG.info("ApportionFeature Flag Value in CardPaymentController : {}", apportionFeature);
        if (apportionFeature) {
            feePayApportionService.processApportion(payment);
        }

        return new ResponseEntity<>(paymentDto, HttpStatus.CREATED);
    }

    @ApiOperation(value = "Record a Bulk Scan Payment", notes = "Record a Bulk Scan Payment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Bulk Scan Payment created"),
        @ApiResponse(code = 400, message = "Bulk Scan Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/payment-groups/{payment-group-reference}/bulk-scan-payments")
    @ResponseBody
    @Transactional
    public ResponseEntity<PaymentDto> recordBulkScanPayment(@PathVariable("payment-group-reference") String paymentGroupReference,
                                                            @Valid @RequestBody BulkScanPaymentRequest bulkScanPaymentRequest) throws CheckDigitException {

        List<SiteDTO> sites = referenceDataService.getSiteIDs();

        if (!sites.stream().anyMatch(site -> site.getSiteID().equalsIgnoreCase(bulkScanPaymentRequest.getSiteId()))) {
            throw new PaymentException("Invalid siteID: " + bulkScanPaymentRequest.getSiteId());
        }

        PaymentProvider paymentProvider = bulkScanPaymentRequest.getExternalProvider() != null ?
            paymentProviderRepository.findByNameOrThrow(bulkScanPaymentRequest.getExternalProvider())
            : null;

        Payment payment = Payment.paymentWith()
            .reference(referenceUtil.getNext("RC"))
            .amount(bulkScanPaymentRequest.getAmount())
            .caseReference(bulkScanPaymentRequest.getExceptionRecord())
            .ccdCaseNumber(bulkScanPaymentRequest.getCcdCaseNumber())
            .currency(bulkScanPaymentRequest.getCurrency().getCode())
            .paymentProvider(paymentProvider)
            .serviceType(bulkScanPaymentRequest.getService())
            .paymentMethod(PaymentMethod.paymentMethodWith().name(bulkScanPaymentRequest.getPaymentMethod().getType()).build())
            .paymentStatus(bulkScanPaymentRequest.getPaymentStatus())
            .siteId(bulkScanPaymentRequest.getSiteId())
            .giroSlipNo(bulkScanPaymentRequest.getGiroSlipNo())
            .reportedDateOffline(DateTime.parse(bulkScanPaymentRequest.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
            .paymentChannel(bulkScanPaymentRequest.getPaymentChannel())
            .documentControlNumber(bulkScanPaymentRequest.getDocumentControlNumber())
            .payerName(bulkScanPaymentRequest.getPayerName())
            .bankedDate(DateTime.parse(bulkScanPaymentRequest.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
            .build();

        PaymentFeeLink paymentFeeLink = paymentGroupService.addNewPaymenttoExistingPaymentGroup(payment, paymentGroupReference);

        Payment newPayment = getPayment(paymentFeeLink, payment.getReference());

        // trigger Apportion based on the launch darkly feature flag
        boolean apportionFeature = featureToggler.getBooleanValue(APPORTION_FEATURE, false);
        LOG.info("ApportionFeature Flag Value in CardPaymentController : {}", apportionFeature);
        if (apportionFeature) {
            feePayApportionService.processApportion(newPayment);

            // Update Fee Amount Due as Payment Status received from Bulk Scan Payment as SUCCESS
            if (newPayment.getPaymentStatus().getName().equalsIgnoreCase("success")) {
                LOG.info("Update Fee Amount Due as Payment Status received from Bulk Scan Payment as SUCCESS!!!");
                feePayApportionService.updateFeeAmountDue(newPayment);
            }
        }

        return new ResponseEntity<>(paymentDtoMapper.toBulkScanPaymentDto(newPayment, paymentGroupReference), HttpStatus.CREATED);
    }

    @ApiOperation(value = "Record a Bulk Scan Payment with Payment Group", notes = "Record a Bulk Scan Payment with Payment Group")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Bulk Scan Payment with payment group created"),
        @ApiResponse(code = 400, message = "Bulk Scan Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/payment-groups/bulk-scan-payments")
    @ResponseBody
    public ResponseEntity<PaymentDto> recordUnsolicitedBulkScanPayment(@Valid @RequestBody BulkScanPaymentRequest bulkScanPaymentRequest) throws CheckDigitException {

        List<SiteDTO> sites = referenceDataService.getSiteIDs();

        String paymentGroupReference = PaymentReference.getInstance().getNext();

        if (!sites.stream().anyMatch(site -> site.getSiteID().equalsIgnoreCase(bulkScanPaymentRequest.getSiteId()))) {
            throw new PaymentException("Invalid siteID: " + bulkScanPaymentRequest.getSiteId());
        }

        PaymentProvider paymentProvider = bulkScanPaymentRequest.getExternalProvider() != null ?
            paymentProviderRepository.findByNameOrThrow(bulkScanPaymentRequest.getExternalProvider())
            : null;

        Payment payment = Payment.paymentWith()
            .reference(referenceUtil.getNext("RC"))
            .amount(bulkScanPaymentRequest.getAmount())
            .caseReference(bulkScanPaymentRequest.getExceptionRecord())
            .ccdCaseNumber(bulkScanPaymentRequest.getCcdCaseNumber())
            .currency(bulkScanPaymentRequest.getCurrency().getCode())
            .paymentProvider(paymentProvider)
            .serviceType(bulkScanPaymentRequest.getService())
            .paymentMethod(PaymentMethod.paymentMethodWith().name(bulkScanPaymentRequest.getPaymentMethod().getType()).build())
            .siteId(bulkScanPaymentRequest.getSiteId())
            .giroSlipNo(bulkScanPaymentRequest.getGiroSlipNo())
            .reportedDateOffline(DateTime.parse(bulkScanPaymentRequest.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
            .paymentChannel(bulkScanPaymentRequest.getPaymentChannel())
            .documentControlNumber(bulkScanPaymentRequest.getDocumentControlNumber())
            .payerName(bulkScanPaymentRequest.getPayerName())
            .paymentStatus(bulkScanPaymentRequest.getPaymentStatus())
            .bankedDate(DateTime.parse(bulkScanPaymentRequest.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
            .build();

        PaymentFeeLink paymentFeeLink = paymentGroupService.addNewBulkScanPayment(payment, paymentGroupReference);

        Payment newPayment = getPayment(paymentFeeLink, payment.getReference());

        return new ResponseEntity<>(paymentDtoMapper.toBulkScanPaymentDto(newPayment, paymentGroupReference), HttpStatus.CREATED);
    }

    private Payment getPayment(PaymentFeeLink paymentFeeLink, String paymentReference) {
        return paymentFeeLink.getPayments().stream().filter(p -> p.getReference().equals(paymentReference)).findAny()
            .orElseThrow(() -> new PaymentNotFoundException("Payment with reference " + paymentReference + " does not exists."));
    }

    @ApiOperation(value = "Record a Bulk Scan Payment", notes = "Record a Bulk Scan Payment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Bulk Scan Payment created"),
        @ApiResponse(code = 400, message = "Bulk Scan Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/payment-groups/{payment-group-reference}/bulk-scan-payments-strategic")
    @ResponseBody
    @Transactional
    public ResponseEntity<PaymentDto> recordBulkScanPaymentStrategic(@PathVariable("payment-group-reference") String paymentGroupReference,
                                                                     @Valid @RequestBody BulkScanPaymentRequestStrategic bulkScanPaymentRequestStrategic,
                                                                     @RequestHeader(required = false) MultiValueMap<String, String> headers) throws CheckDigitException {

        boolean prodStrategicFixFeatureFlag = featureToggler.getBooleanValue("prod-strategic-fix", false);
        if (prodStrategicFixFeatureFlag) {
            // Check Any Duplicate payments for current DCN
            if (bulkScanPaymentRequestStrategic.getDocumentControlNumber() != null) {
                List<Payment> existingPaymentForDCNList = payment2Repository.findByDocumentControlNumber(bulkScanPaymentRequestStrategic.getDocumentControlNumber()).orElse(null);
                if (existingPaymentForDCNList != null && !existingPaymentForDCNList.isEmpty()) {
                    throw new DuplicatePaymentException("Bulk scan payment already exists for DCN = " + bulkScanPaymentRequestStrategic.getDocumentControlNumber());
                }
            }

            OrganisationalServiceDto organisationalServiceDto = referenceDataService.getOrganisationalDetail(bulkScanPaymentRequestStrategic.getCaseType(), headers);

            PaymentProvider paymentProvider = bulkScanPaymentRequestStrategic.getExternalProvider() != null ?
                paymentProviderRepository.findByNameOrThrow(bulkScanPaymentRequestStrategic.getExternalProvider())
                : null;

            Payment payment = Payment.paymentWith()
                .reference(referenceUtil.getNext("RC"))
                .amount(bulkScanPaymentRequestStrategic.getAmount())
                .caseReference(bulkScanPaymentRequestStrategic.getExceptionRecord())
                .ccdCaseNumber(bulkScanPaymentRequestStrategic.getCcdCaseNumber())
                .currency(bulkScanPaymentRequestStrategic.getCurrency().getCode())
                .paymentProvider(paymentProvider)
                .serviceType(organisationalServiceDto.getServiceDescription())
                .paymentMethod(PaymentMethod.paymentMethodWith().name(bulkScanPaymentRequestStrategic.getPaymentMethod().getType()).build())
                .paymentStatus(bulkScanPaymentRequestStrategic.getPaymentStatus())
                .siteId(organisationalServiceDto.getServiceCode())
                .giroSlipNo(bulkScanPaymentRequestStrategic.getGiroSlipNo())
                .reportedDateOffline(DateTime.parse(bulkScanPaymentRequestStrategic.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
                .paymentChannel(bulkScanPaymentRequestStrategic.getPaymentChannel())
                .documentControlNumber(bulkScanPaymentRequestStrategic.getDocumentControlNumber())
                .payerName(bulkScanPaymentRequestStrategic.getPayerName())
                .bankedDate(DateTime.parse(bulkScanPaymentRequestStrategic.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
                .build();

            PaymentFeeLink paymentFeeLink = paymentGroupService.addNewPaymenttoExistingPaymentGroup(payment, paymentGroupReference);

            Payment newPayment = getPayment(paymentFeeLink, payment.getReference());

            // trigger Apportion based on the launch darkly feature flag
            boolean apportionFeature = featureToggler.getBooleanValue(APPORTION_FEATURE, false);
            LOG.info("ApportionFeature Flag Value in PaymentGroupController  RecordBulkScanPaymentStrategic: {}", apportionFeature);
            if (apportionFeature) {
                feePayApportionService.processApportion(newPayment);

                // Update Fee Amount Due as Payment Status received from Bulk Scan Payment as SUCCESS
                if (newPayment.getPaymentStatus().getName().equalsIgnoreCase("success")) {
                    LOG.info("Update Fee Amount Due as Payment Status received from Bulk Scan Payment as SUCCESS!!!");
                    feePayApportionService.updateFeeAmountDue(newPayment);
                }
            }

            allocateThePaymentAndMarkBulkScanPaymentAsProcessed(bulkScanPaymentRequestStrategic, paymentGroupReference, newPayment, headers);
            return new ResponseEntity<>(paymentDtoMapper.toBulkScanPaymentStrategicDto(newPayment, paymentGroupReference), HttpStatus.CREATED);
        } else {
            throw new PaymentException("This feature is not available to use !!!");
        }
    }

    @ApiOperation(value = "Record a Bulk Scan Payment with Payment Group", notes = "Record a Bulk Scan Payment with Payment Group")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Bulk Scan Payment with payment group created"),
        @ApiResponse(code = 400, message = "Bulk Scan Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/payment-groups/bulk-scan-payments-strategic")
    @ResponseBody
    @Transactional
    public ResponseEntity<PaymentDto> recordUnsolicitedBulkScanPaymentStrategic(@Valid @RequestBody BulkScanPaymentRequestStrategic bulkScanPaymentRequestStrategic
        , @RequestHeader(required = false) MultiValueMap<String, String> headers) throws CheckDigitException {

        boolean prodStrategicFixFeatureFlag = featureToggler.getBooleanValue("prod-strategic-fix", false);
        if (prodStrategicFixFeatureFlag) {
            // Check Any Duplicate payments for current DCN
            if (bulkScanPaymentRequestStrategic.getDocumentControlNumber() != null) {
                List<Payment> existingPaymentForDCNList = payment2Repository.findByDocumentControlNumber(bulkScanPaymentRequestStrategic.getDocumentControlNumber()).orElse(null);
                if (existingPaymentForDCNList != null && !existingPaymentForDCNList.isEmpty()) {
                    throw new DuplicatePaymentException("Bulk scan payment already exists for DCN = " + bulkScanPaymentRequestStrategic.getDocumentControlNumber());
                }
            }

            String paymentGroupReference = PaymentReference.getInstance().getNext();

            OrganisationalServiceDto organisationalServiceDto = referenceDataService.getOrganisationalDetail(bulkScanPaymentRequestStrategic.getCaseType(), headers);

            PaymentProvider paymentProvider = bulkScanPaymentRequestStrategic.getExternalProvider() != null ?
                paymentProviderRepository.findByNameOrThrow(bulkScanPaymentRequestStrategic.getExternalProvider())
                : null;

            Payment payment = Payment.paymentWith()
                .reference(referenceUtil.getNext("RC"))
                .amount(bulkScanPaymentRequestStrategic.getAmount())
                .caseReference(bulkScanPaymentRequestStrategic.getExceptionRecord())
                .ccdCaseNumber(bulkScanPaymentRequestStrategic.getCcdCaseNumber())
                .currency(bulkScanPaymentRequestStrategic.getCurrency().getCode())
                .paymentProvider(paymentProvider)
                .serviceType(organisationalServiceDto.getServiceDescription())
                .paymentMethod(PaymentMethod.paymentMethodWith().name(bulkScanPaymentRequestStrategic.getPaymentMethod().getType()).build())
                .siteId(organisationalServiceDto.getServiceCode())
                .giroSlipNo(bulkScanPaymentRequestStrategic.getGiroSlipNo())
                .reportedDateOffline(DateTime.parse(bulkScanPaymentRequestStrategic.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
                .paymentChannel(bulkScanPaymentRequestStrategic.getPaymentChannel())
                .documentControlNumber(bulkScanPaymentRequestStrategic.getDocumentControlNumber())
                .payerName(bulkScanPaymentRequestStrategic.getPayerName())
                .paymentStatus(bulkScanPaymentRequestStrategic.getPaymentStatus())
                .bankedDate(DateTime.parse(bulkScanPaymentRequestStrategic.getBankedDate()).withZone(DateTimeZone.UTC).toDate())
                .build();

            PaymentFeeLink paymentFeeLink = paymentGroupService.addNewBulkScanPayment(payment, paymentGroupReference);

            Payment newPayment = getPayment(paymentFeeLink, payment.getReference());

            allocateThePaymentAndMarkBulkScanPaymentAsProcessed(bulkScanPaymentRequestStrategic, paymentGroupReference, newPayment, headers);
            return new ResponseEntity<>(paymentDtoMapper.toBulkScanPaymentStrategicDto(newPayment, paymentGroupReference), HttpStatus.CREATED);
        } else {
            throw new PaymentException("This feature is not available to use !!!");
        }
    }

    public void allocateThePaymentAndMarkBulkScanPaymentAsProcessed(BulkScanPaymentRequestStrategic bulkScanPaymentRequestStrategic, String paymentGroupReference, Payment newPayment,
                                                                    MultiValueMap<String, String> headers) {
        //Payment Allocation endpoint call
        PaymentAllocationDto paymentAllocationDto = bulkScanPaymentRequestStrategic.getPaymentAllocationDTO();
        PaymentAllocation paymentAllocation = PaymentAllocation.paymentAllocationWith()
            .paymentReference(newPayment.getReference())
            .paymentGroupReference(paymentGroupReference)
            .paymentAllocationStatus(paymentAllocationDto.getPaymentAllocationStatus())
            .reason(paymentAllocationDto.getReason())
            .explanation(paymentAllocationDto.getExplanation())
            .userName(paymentAllocationDto.getUserName())
            .receivingOffice(paymentAllocationDto.getReceivingOffice())
            .unidentifiedReason(paymentAllocationDto.getUnidentifiedReason())
            .build();
        List<PaymentAllocation> paymentAllocationList = new ArrayList<>();
        paymentAllocationList.add(paymentAllocation);
        newPayment.setPaymentAllocation(paymentAllocationList);

        //Mark bulk scan payment as processed
        try {
            markBulkScanPaymentProcessed(headers, newPayment.getDocumentControlNumber(), "PROCESSED"); // default status PROCESSED
        } catch (HttpClientErrorException httpClientErrorException) {
            throw new PaymentException("Bulk scan payment can't be marked as processed for DCN " + newPayment.getDocumentControlNumber() +
                " Due to response status code as  = " + httpClientErrorException.getMessage());
        } catch (Exception exception) {
            throw new PaymentException("Error occurred while processing bulk scan payments with DCN " + newPayment.getDocumentControlNumber() +
                " Exception message  = " + exception.getMessage());
        }
    }

    public ResponseEntity<String> markBulkScanPaymentProcessed(MultiValueMap<String, String> headersMap, String dcn, String status) throws RestClientException {
        //Generate token for payment api and replace
        List<String> serviceAuthTokenPaymentList = new ArrayList<>();
        serviceAuthTokenPaymentList.add(authTokenGenerator.generate());

        MultiValueMap<String, String> headerMultiValueMapForBulkScan = new LinkedMultiValueMap<String, String>();
        headerMultiValueMapForBulkScan.put("content-type", headersMap.get("content-type"));
        //User token
        headerMultiValueMapForBulkScan.put("Authorization", headersMap.get("authorization"));
        //Service token
        headerMultiValueMapForBulkScan.put("ServiceAuthorization", serviceAuthTokenPaymentList);

        HttpHeaders headers = new HttpHeaders(headerMultiValueMapForBulkScan);
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        Map<String, String> params = new HashMap<>();
        params.put("dcn", dcn);
        params.put("status", status);
//        return new ResponseEntity<String>("new ArrayList<>()", HttpStatus.OK);
        LOG.info("Calling Bulk scan api to mark payment as processed from Payment Api");

        return restTemplatePaymentGroup.exchange(bulkScanPaymentsProcessedUrl + "/bulk-scan-payments/{dcn}/status/{status}", HttpMethod.PATCH, entity, String.class, params);
    }

    @ApiOperation(value = "Create telephony card payment in Payment Group", notes = "Create telephony card payment in Payment Group")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Payment created"),
        @ApiResponse(code = 400, message = "Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/payment-groups/{payment-group-reference}/telephony-card-payments")
    @ResponseBody
    @Transactional
    public ResponseEntity<TelephonyCardPaymentsResponse> createTelephonyCardPayment(
        @PathVariable("payment-group-reference") String paymentGroupReference,
        @Valid @RequestBody TelephonyCardPaymentsRequest telephonyCardPaymentsRequest,
        @RequestHeader(required = false) MultiValueMap<String, String> headers) throws CheckDigitException, MethodNotSupportedException {

        boolean antennaFeature = featureToggler.getBooleanValue("pci-pal-antenna-feature", false);
        LOG.info("Feature Flag Value in CardPaymentController : {}", antennaFeature);
        if (antennaFeature) {
            LOG.info("Inside Telephony check!!!");
            OrganisationalServiceDto organisationalServiceDto = referenceDataService.getOrganisationalDetail(telephonyCardPaymentsRequest.getCaseType(), headers);
            TelephonyProviderAuthorisationResponse telephonyProviderAuthorisationResponse = pciPalPaymentService.getPaymentProviderAutorisationTokens();
            PaymentServiceRequest paymentServiceRequest = PaymentServiceRequest.paymentServiceRequestWith()
                .paymentGroupReference(paymentGroupReference)
                .paymentReference(referenceUtil.getNext("RC"))
                .ccdCaseNumber(telephonyCardPaymentsRequest.getCcdCaseNumber())
                .currency(telephonyCardPaymentsRequest.getCurrency().getCode())
                .siteId(organisationalServiceDto.getServiceCode())
                .serviceType(organisationalServiceDto.getServiceDescription())
                .amount(telephonyCardPaymentsRequest.getAmount())
                .channel(PaymentChannel.TELEPHONY.getName())
                .provider(PaymentProvider.PCI_PAL.getName())
                .build();
            PaymentFeeLink paymentLink = delegatingPaymentService.update(paymentServiceRequest);
            Payment payment = getPayment(paymentLink, paymentServiceRequest.getPaymentReference());

            PciPalPaymentRequest pciPalPaymentRequest = PciPalPaymentRequest.pciPalPaymentRequestWith().orderAmount(telephonyCardPaymentsRequest.getAmount().toString()).orderCurrency(telephonyCardPaymentsRequest.getCurrency().getCode())
                .orderReference(payment.getReference()).build();
            telephonyProviderAuthorisationResponse = pciPalPaymentService.getTelephonyProviderLink(pciPalPaymentRequest, telephonyProviderAuthorisationResponse, organisationalServiceDto.getServiceDescription(), telephonyCardPaymentsRequest.getReturnURL());
            LOG.info("Next URL Value in CardPaymentController : {}", telephonyProviderAuthorisationResponse.getNextUrl());
            TelephonyCardPaymentsResponse telephonyCardPaymentsResponse = telephonyDtoMapper.toTelephonyCardPaymentsResponse(paymentLink, payment, telephonyProviderAuthorisationResponse);

            // trigger Apportion based on the launch darkly feature flag
            boolean apportionFeature = featureToggler.getBooleanValue(APPORTION_FEATURE, false);
            LOG.info("ApportionFeature Flag Value in CardPaymentController : {}", apportionFeature);
            if (apportionFeature) {
                feePayApportionService.processApportion(payment);
            }
            return new ResponseEntity<>(telephonyCardPaymentsResponse, HttpStatus.CREATED);
        } else {
            throw new MethodNotSupportedException("This feature is not available to use or invalid request!!!");
        }
    }


    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {NoServiceFoundException.class})
    public String return404(NoServiceFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    @ExceptionHandler(GatewayTimeoutException.class)
    public String return504(GatewayTimeoutException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {OrderReferenceNotFoundException.class})
    public String return404(OrderReferenceNotFoundException ex) {
        return ex.getMessage();
    }


    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(InvalidPaymentGroupReferenceException.class)
    public String return403(InvalidPaymentGroupReferenceException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(PaymentNotFoundException.class)
    public String return400(PaymentNotFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidFeeRequestException.class)
    public String return400(InvalidFeeRequestException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodNotSupportedException.class)
    public String return400(MethodNotSupportedException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(PaymentException.class)
    public String return400(PaymentException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DuplicatePaymentException.class)
    public String return400DuplicatePaymentException(DuplicatePaymentException ex) {
        return ex.getMessage();
    }
}
