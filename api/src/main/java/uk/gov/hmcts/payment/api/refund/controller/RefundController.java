package uk.gov.hmcts.payment.api.refund.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.*;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.payment.api.controllers.PaymentExternalAPI;
import uk.gov.hmcts.payment.api.domain.model.ApproverAction;
import uk.gov.hmcts.payment.api.domain.service.RefundDomainService;
import uk.gov.hmcts.payment.api.dto.RefundDto;
import uk.gov.hmcts.payment.api.dto.order.OrderFeeDto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;


@RestController
@Api(tags = {"Refund"})
@SwaggerDefinition(tags = {@Tag(name = "RefundController", description = "Refund REST API")})
public class RefundController {

    private static final Logger LOG = LoggerFactory.getLogger(RefundController.class);
    private static final String FAILED = "failed";

    @Autowired
    private RefundDomainService refundDomainService;

    @ApiOperation(value = "Initiate Refund for a Payment", notes = "Initiate Refund for a Payment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Refund Created"),
        @ApiResponse(code = 400, message = "Refund Creation Failed"),
        @ApiResponse(code = 401, message = "Credentials are required to access this resource"),
        @ApiResponse(code = 403, message = "Forbidden-Access Denied"),
        @ApiResponse(code = 504, message = "Unable to retrieve service information. Please try again later"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    @PostMapping(value = "/refund/{payment-reference}/refund-payment")
    @Transactional
    public ResponseEntity<?> create(
        @PathVariable("payment-reference") String paymentReference,
        @Valid @RequestBody RefundDto refundDto, @RequestHeader(required = false) MultiValueMap<String, String> headers) {
        return new ResponseEntity<>(refundDomainService.create(refundDto, paymentReference, headers), HttpStatus.CREATED);
    }

    @ApiOperation(value = "Update Refund for a Payment", notes = "Update Refund for a Payment")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Refund Updated"),
        @ApiResponse(code = 400, message = "Refund Update Failed"),
        @ApiResponse(code = 401, message = "Credentials are required to access this resource"),
        @ApiResponse(code = 403, message = "Forbidden-Access Denied"),
        @ApiResponse(code = 504, message = "Unable to retrieve service information. Please try again later"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    @PutMapping(value = "/refund/{refund-reference}/refund-payment")
    @Transactional
    public ResponseEntity<?> update(
        @PathVariable("refund-reference") String refundReference,
        @Valid @RequestBody RefundDto refundDto, @RequestHeader(required = false) MultiValueMap<String, String> headers) {
        return new ResponseEntity<>(refundDomainService.update(refundDto, refundReference, headers), HttpStatus.OK);
    }

    @ApiOperation(value = "Approver action on Refund for a Payment", notes = "Approver action on Refund for a Payment")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Approver action completed"),
        @ApiResponse(code = 400, message = "Approver action Failed"),
        @ApiResponse(code = 401, message = "Credentials are required to access this resource"),
        @ApiResponse(code = 403, message = "Forbidden-Access Denied"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    @PatchMapping(value = "/refund/{refund-reference}/approver-action/{approver-action}")
    @Transactional
    public ResponseEntity<?> action(
        @PathVariable("refund-reference") String refundReference,
        @PathVariable("approver-action") ApproverAction approverAction,
        @RequestHeader(required = false) MultiValueMap<String, String> headers,
        @RequestBody(required = false) RefundDto refundDto) {
        return new ResponseEntity<>(refundDomainService.action(refundReference, approverAction, headers, refundDto), HttpStatus.OK);
    }

    @ApiOperation(value = "Update Refund Status for Payment", notes = "Update Refund Status for a Payment")
    @ApiResponses(value = {
        @ApiResponse(code = 401, message = "Credentials are required to access this resource"),
        @ApiResponse(code = 403, message = "Forbidden-Access Denied"),
        @ApiResponse(code = 204, message = "Refund Status[Issued/Rejected] Updated Successfully"),
        @ApiResponse(code = 404, message = "Refund Not Found"),
        @ApiResponse(code = 400, message = "Bad Request, Refund Status should be Issued or Rejected")
    })
    @PaymentExternalAPI
    @PatchMapping(value = "/refund/{refund-reference}")
    @Transactional
    public ResponseEntity<?> updateRefundStatus(
        @PathVariable("refund-reference") String refundReference,
        @RequestBody RefundStatusDto refundStatusDto) {
        return new ResponseEntity<>("", HttpStatus.OK);
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "refundStatusDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
class RefundStatusDto {

    @NotNull
    private RefundStatus status;
    private String reason;
}

enum RefundStatus {
    Issued,
    Rejected
}

class Status {
    private RefundStatus status;
}
