package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.UpdatePaymentRequest;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.util.Optional;

import static org.springframework.web.bind.annotation.RequestMethod.PATCH;

@RestController
@Api(tags = {"Payment"})
@SwaggerDefinition(tags = {@Tag(name = "PaymentController", description = "Payment REST API")})
public class PaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService<PaymentFeeLink, String> paymentService;
    private final PaymentDtoMapper paymentDtoMapper;
    @Autowired
    public PaymentController(PaymentService<PaymentFeeLink, String> paymentService,
                             PaymentDtoMapper paymentDtoMapper) {
        this.paymentService = paymentService;
        this.paymentDtoMapper = paymentDtoMapper;
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

    private Optional<Payment> getPaymentByReference(String reference) {
        PaymentFeeLink paymentFeeLink = paymentService.retrieve(reference);
        return paymentFeeLink.getPayments().stream()
            .filter(p -> p.getReference().equals(reference)).findAny();
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
