package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.domain.service.OrderDomainService;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.PaymentGroupResponse;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentGroupDtoMapper;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.PaymentGroupService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentGroupNotFoundException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Api(tags = {"Case"})
@SwaggerDefinition(tags = {@Tag(name = "CaseController", description = "Case REST API")})
@Validated
public class CaseController {

    private final PaymentService<PaymentFeeLink, String> paymentService;
    private final PaymentGroupService<PaymentFeeLink, String> paymentGroupService;
    private final PaymentDtoMapper paymentDtoMapper;
    private final PaymentGroupDtoMapper paymentGroupDtoMapper;

    @Autowired
    private OrderDomainService orderDomainService;

    @Autowired
    public CaseController(PaymentService<PaymentFeeLink, String> paymentService, PaymentGroupService paymentGroupService,
                          PaymentDtoMapper paymentDtoMapper, PaymentGroupDtoMapper paymentGroupDtoMapper) {
        this.paymentService = paymentService;
        this.paymentGroupService = paymentGroupService;
        this.paymentDtoMapper = paymentDtoMapper;
        this.paymentGroupDtoMapper = paymentGroupDtoMapper;
    }

    @ApiOperation(value = "Get payments for a case", notes = "Get payments for a case")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payments retrieved"),
        @ApiResponse(code = 400, message = "Bad request")
    })
    @RequestMapping(value = "/cases/{case}/payments", method = GET)
    @PaymentExternalAPI
    public PaymentsResponse retrieveCasePayments(@PathVariable(name = "case") String ccdCaseNumber) {

        List<PaymentDto> payments = paymentService
            .search(PaymentSearchCriteria.searchCriteriaWith().ccdCaseNumber(ccdCaseNumber).build())
            .stream()
            .map(paymentDtoMapper::toReconciliationResponseDto)
            .collect(Collectors.toList());

        if (payments == null || payments.isEmpty()) {
            throw new PaymentNotFoundException();
        }

        return new PaymentsResponse(payments);
    }


    @ApiOperation(value = "Get payment groups for a case", notes = "Get payment groups for a case")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment Groups retrieved"),
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 403, message = "Payment Info Forbidden"),
        @ApiResponse(code = 404, message = "Payment Groups not found")
    })
    @RequestMapping(value = "/cases/{ccdcasenumber}/paymentgroups", method = GET)
    public PaymentGroupResponse retrieveCasePaymentGroups(@PathVariable(name = "ccdcasenumber") String ccdCaseNumber) {

        List<PaymentGroupDto> paymentGroups = paymentGroupService
            .search(ccdCaseNumber)
            .stream()
            .map(paymentGroupDtoMapper::toPaymentGroupDto)
            .collect(Collectors.toList());

        if (paymentGroups == null || paymentGroups.isEmpty()) {
            throw new PaymentGroupNotFoundException();
        }

        return new PaymentGroupResponse(paymentGroups);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PaymentException.class)
    public String notFound(PaymentException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PaymentGroupNotFoundException.class)
    public String notFound(PaymentGroupNotFoundException ex) {
        return ex.getMessage();
    }

}
