package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.ff4j.FF4j;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CardPaymentRequest;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.dto.OrganisationalServiceDto;
import uk.gov.hmcts.payment.api.dto.PaymentServiceRequest;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.external.client.dto.CardDetails;
import uk.gov.hmcts.payment.api.external.client.exceptions.GovPayCancellationFailedException;
import uk.gov.hmcts.payment.api.external.client.exceptions.GovPayException;
import uk.gov.hmcts.payment.api.external.client.exceptions.GovPayPaymentNotFoundException;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.*;
import uk.gov.hmcts.payment.api.v1.model.exceptions.GatewayTimeoutException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.NoServiceFoundException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import javax.validation.Valid;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.web.bind.annotation.RequestMethod.GET;


/**
 * Card payment controller
 */

@RestController
@Api(tags = {"Card Payment"})
@SwaggerDefinition(tags = {@Tag(name = "CardPaymentController", description = "Card payment REST API")})
public class CardPaymentController {
    private static final Logger LOG = LoggerFactory.getLogger(CardPaymentController.class);

    private final DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;
    private final PaymentDtoMapper paymentDtoMapper;
    private final CardDetailsService<CardDetails, String> cardDetailsService;
    private final FF4j ff4j;
    private final FeePayApportionService feePayApportionService;
    private final LaunchDarklyFeatureToggler featureToggler;
    private final ReferenceDataService referenceDataService;

    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;

    @Autowired
    public CardPaymentController(DelegatingPaymentService<PaymentFeeLink, String> cardDelegatingPaymentService,
                                 PaymentDtoMapper paymentDtoMapper,
                                 CardDetailsService<CardDetails, String> cardDetailsService,
                                 PciPalPaymentService pciPalPaymentService,
                                 FF4j ff4j,
                                 FeePayApportionService feePayApportionService, LaunchDarklyFeatureToggler featureToggler, ReferenceDataService referenceDataService) {
        this.delegatingPaymentService = cardDelegatingPaymentService;
        this.paymentDtoMapper = paymentDtoMapper;
        this.cardDetailsService = cardDetailsService;
        this.ff4j = ff4j;
        this.feePayApportionService = feePayApportionService;
        this.featureToggler = featureToggler;
        this.referenceDataService = referenceDataService;
    }

    @ApiOperation(value = "Create card payment", notes = "Create card payment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Payment created"),
        @ApiResponse(code = 400, message = "Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute"),
        @ApiResponse(code = 404, message = "No Service found for given CaseType"),
        @ApiResponse(code = 504, message = "Unable to retrieve service information. Please try again later")
    })
    @PostMapping(value = "/card-payments")
    @ResponseBody
    @Transactional
    public ResponseEntity<PaymentDto> createCardPayment(
        @RequestHeader(value = "return-url") String returnURL,
        @RequestHeader(value = "service-callback-url", required = false) String serviceCallbackUrl,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @Valid @RequestBody CardPaymentRequest request) throws CheckDigitException {
        String paymentGroupReference = PaymentReference.getInstance().getNext();

        if (StringUtils.isEmpty(request.getChannel()) || StringUtils.isEmpty(request.getProvider())) {
            request.setChannel("online");
            request.setProvider("gov pay");
        }

        if (request.getCcdCaseNumber() != null && request.getFees() != null) {
            request.setFees(request.getFees()
                .stream()
                .map(feeDto -> {
                    if (feeDto.getCcdCaseNumber() == null || feeDto.getCcdCaseNumber().isEmpty()) {
                        feeDto.setCcdCaseNumber(request.getCcdCaseNumber());
                    }
                    return feeDto;
                })
                .collect(Collectors.toList())
            );
        }

        LOG.info("Case Type: {} ", request.getCaseType());

        if (StringUtils.isNotBlank(request.getCaseType())) {
            OrganisationalServiceDto organisationalServiceDto = referenceDataService.getOrganisationalDetail(request.getCaseType(), headers);
            request.setSiteId(organisationalServiceDto.getServiceCode());
            request.setService(organisationalServiceDto.getServiceDescription());
        } else {
            /*
            Following piece of code to be removed once all Services are on-boarded to Enterprise ORG ID
            */
            request.setService(paymentService.getServiceNameByCode(request.getService()));
        }

        LOG.info("Service Name : {} ", request.getService());

        PaymentServiceRequest paymentServiceRequest = PaymentServiceRequest.paymentServiceRequestWith()
            .paymentGroupReference(paymentGroupReference)
            .description(Encode.forHtml(request.getDescription()))
            .returnUrl(returnURL)
            .ccdCaseNumber(request.getCcdCaseNumber())
            .caseReference(request.getCaseReference())
            .currency(request.getCurrency().getCode())
            .siteId(request.getSiteId())
            .serviceType(request.getService())
            .fees((request.getFees() != null) ? paymentDtoMapper.toFees(request.getFees()) : null)
            .amount(request.getAmount())
            .serviceCallbackUrl(serviceCallbackUrl)
            .channel(request.getChannel())
            .provider(request.getProvider())
            .language((StringUtils.isBlank(request.getLanguage()) || request.getLanguage() != null
                && request.getLanguage().equalsIgnoreCase("string")) ? null : StringUtils.lowerCase(request.getLanguage()))
            //change language to lower case before sending to gov pay
            .build();

        LOG.info("Language Value : {}", paymentServiceRequest.getLanguage());
        LOG.info("siteId Value : {}", paymentServiceRequest.getSiteId());
        PaymentFeeLink paymentLink = delegatingPaymentService.create(paymentServiceRequest);
        PaymentDto paymentDto = paymentDtoMapper.toCardPaymentDto(paymentLink);

        // trigger Apportion based on the launch darkly feature flag
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature", false);
        LOG.info("ApportionFeature Flag Value in CardPaymentController : {}", apportionFeature);
        if (apportionFeature) {
            feePayApportionService.processApportion(paymentLink.getPayments().get(0));
        }
        return new ResponseEntity<>(paymentDto, CREATED);
    }

    @ApiOperation(value = "Get card payment details by payment reference", notes = "Get payment details for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment retrieved"),
        @ApiResponse(code = 403, message = "Payment info forbidden"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @GetMapping(value = "/card-payments/{reference}")
    public PaymentDto retrieve(@PathVariable("reference") String paymentReference) {
        return paymentDtoMapper.toRetrieveCardPaymentResponseDto(delegatingPaymentService.retrieve(paymentReference));
    }

    @ApiOperation(value = "Get card payment details with card details by payment reference", notes = "Get payment details with card details for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment card details retrieved"),
        @ApiResponse(code = 404, message = "Payment card details not found")
    })
    @RequestMapping(value = "/card-payments/{reference}/details", method = GET)
    public CardDetails retrieveWithCardDetails(@PathVariable("reference") String paymentReference) {
        return cardDetailsService.retrieve(paymentReference);
    }

    @ApiOperation(value = "Get card payment statuses by payment reference", notes = "Get payment statuses for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment retrieved"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @GetMapping(value = "/card-payments/{reference}/statuses")
    public PaymentDto retrievePaymentStatus(@PathVariable("reference") String paymentReference) {
        PaymentFeeLink paymentFeeLink = delegatingPaymentService.retrieve(paymentReference);
        Optional<Payment> payment = paymentFeeLink.getPayments().stream()
            .filter(p -> p.getReference().equals(paymentReference)).findAny();
        Payment payment1 = null;
        if (payment.isPresent()) {
            payment1 = payment.get();
        }
        return paymentDtoMapper.toPaymentStatusesDto(payment1);
    }

    @ApiOperation(value = "Cancel payment for supplied payment reference", notes = "Cancel payment for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "Cancellation of payment successful"),
        @ApiResponse(code = 400, message = "Cancellation of payment failed"),
        @ApiResponse(code = 401, message = "Credentials are required to access this resource"),
        @ApiResponse(code = 403, message = "Forbidden-Access Denied"),
        @ApiResponse(code = 404, message = "Payment Not found"),
        @ApiResponse(code = 500, message = "Downstream system error")
    })
    @PostMapping(value = "/card-payments/{reference}/cancel")
    public ResponseEntity cancelPayment(@PathVariable("reference") String paymentReference) {
        if (!ff4j.check("payment-cancel")) {
            throw new PaymentException("Payment cancel feature is not available for usage.");
        }
        delegatingPaymentService.cancel(paymentReference);
        return new ResponseEntity(NO_CONTENT);
    }

    @ExceptionHandler(value = {GovPayCancellationFailedException.class})
    public ResponseEntity cancellationFailedException(GovPayCancellationFailedException ex) {
        return new ResponseEntity(BAD_REQUEST);
    }

    @ExceptionHandler(value = {GovPayPaymentNotFoundException.class, PaymentNotFoundException.class})
    public ResponseEntity httpClientErrorException() {
        return new ResponseEntity(NOT_FOUND);
    }

    @ExceptionHandler(value = {GovPayException.class})
    public ResponseEntity httpClientErrorException(GovPayException e) {
        LOG.error("Error while calling payments", e);
        return new ResponseEntity(INTERNAL_SERVER_ERROR);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = {NoServiceFoundException.class})
    public String return404(NoServiceFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(PaymentException.class)
    public String return400(PaymentException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    @ExceptionHandler(GatewayTimeoutException.class)
    public String return504(GatewayTimeoutException ex) {
        return ex.getMessage();
    }
}
