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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.RetrieveOrderPaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.contract.OrderPaymentsResponse;
import uk.gov.hmcts.payment.api.domain.service.FeeDomainService;
import uk.gov.hmcts.payment.api.domain.service.OrderDomainService;
import uk.gov.hmcts.payment.api.domain.service.PaymentDomainService;
import uk.gov.hmcts.payment.api.dto.*;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentGroupDtoMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.PaymentGroupService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentGroupNotFoundException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import javax.validation.ConstraintViolationException;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Set;
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
    private FeeDomainService feeDomainService;

    @Autowired
    private PaymentDomainService paymentDomainService;

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

    @ApiOperation(value = "Get payment groups for a case using Orders", notes = "Get payment groups for a case using Orders")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment Groups retrieved"),
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 403, message = "Payment Info Forbidden"),
        @ApiResponse(code = 404, message = "Payment Groups not found")
    })
    @GetMapping(value = "/orderpoc/cases/{ccdcasenumber}/paymentgroups")
    public OrderPaymentGroupResponse retrieveCasePaymentGroups_NewAPI(@PathVariable(name = "ccdcasenumber") @Size(max = 16, min = 16, message = "CcdCaseNumber should be 16 digits") String ccdCaseNumber) {
        List<PaymentFeeLink> paymentFeeLinks = orderDomainService.findByCcdCaseNumber(ccdCaseNumber);
        List<RetrieveOrderPaymentGroupDto> retrieveOrderPaymentGroupDtoList = paymentFeeLinks.stream().map(paymentGroupDtoMapper::toPaymentGroupDtoForOrders)
            .collect(Collectors.toList());

        if (retrieveOrderPaymentGroupDtoList == null || retrieveOrderPaymentGroupDtoList.isEmpty()) {
            throw new PaymentGroupNotFoundException();
        }

        return new OrderPaymentGroupResponse(retrieveOrderPaymentGroupDtoList);
    }

    @ApiOperation(value = "Get payments for a case by orders", notes = "Get payments for a case  by orders")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payments retrieved"),
        @ApiResponse(code = 400, message = "Bad request")
    })
    @GetMapping(value = "/orderpoc/cases/{case}/payments")
    @PaymentExternalAPI
    public OrderPaymentsResponse retrieveCasePaymentsByOrders(@PathVariable(name = "case") @Size(max = 16, min = 16, message = "CcdCaseNumber should be 16 digits") String ccdCaseNumber) {
        List<PaymentFeeLink> paymentFeeLinks = orderDomainService.findByCcdCaseNumber(ccdCaseNumber);
        List<RetrieveOrderPaymentDto> payments = paymentFeeLinks.stream().flatMap(link -> toReconciliationResponseDtoForOrders(link).stream())
            .collect(Collectors.toList());
        if (payments == null || payments.isEmpty()) {
            throw new PaymentNotFoundException();
        }

        return new OrderPaymentsResponse(payments);
    }

    private List<RetrieveOrderPaymentDto> toReconciliationResponseDtoForOrders(PaymentFeeLink paymentFeeLink) {
        List<FeePayApportion> feePayApportions = paymentFeeLink.getFees()
            .stream()
            .flatMap(fee ->
                feeDomainService.getFeePayApportionsByFee(fee).stream())
            .collect(Collectors.toList());
        Set<RetrieveOrderPaymentDto> paymentDtos = feePayApportions
            .stream()
            .map(feePayApportion ->
                paymentDtoMapper.toPaymentDto(paymentDomainService.getPaymentByApportionment(feePayApportion), paymentFeeLink))
            .collect(Collectors.toSet());
        return paymentDtos.stream().collect(Collectors.toList());
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

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public String handleConstraintViolationException(ConstraintViolationException exception) {
        return exception.getMessage();
    }

}
