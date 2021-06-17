package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.contract.CasePaymentOrdersDto;
import uk.gov.hmcts.payment.api.dto.mapper.CasePaymentOrdersMapper;
import uk.gov.hmcts.payment.api.service.CasePaymentOrdersService;
import uk.gov.hmcts.payment.casepaymentorders.client.dto.CpoGetResponse;
import uk.gov.hmcts.payment.casepaymentorders.client.exceptions.CpoBadRequestException;
import uk.gov.hmcts.payment.casepaymentorders.client.exceptions.CpoClientException;
import uk.gov.hmcts.payment.casepaymentorders.client.exceptions.CpoInternalServerErrorException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Case payment orders controller
 */

@RestController
@Api(tags = {"Case Payment Orders"})
@SwaggerDefinition(tags = {@Tag(name = "CasePaymentOrderController", description = "Case Payment Order REST API")})
public class CasePaymentOrdersController {
    private static final Logger LOG = LoggerFactory.getLogger(CasePaymentOrdersController.class);

    private final CasePaymentOrdersService casePaymentOrdersService;
    private final CasePaymentOrdersMapper casePaymentOrdersMapper;

    @Autowired
    public CasePaymentOrdersController(CasePaymentOrdersService casePaymentOrdersService,
                                       CasePaymentOrdersMapper casePaymentOrdersMapper) {
        this.casePaymentOrdersService = casePaymentOrdersService;
        this.casePaymentOrdersMapper = casePaymentOrdersMapper;
    }

    @ApiOperation(value = "Get payment orders for a case", notes = "Get payment orders for a case")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment orders retrieved"),
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Credentials are required to access this resource"),
        @ApiResponse(code = 403, message = "Case Payment Orders info forbidden"),
        @ApiResponse(code = 404, message = "Case Payment Order does not exist"),
        @ApiResponse(code = 500, message = "Downstream system error")
    })
    @RequestMapping(value = "/case-payment-orders", method = GET)
    public CasePaymentOrdersDto retrieveCasePaymentOrders(
        @ApiParam(value = "Coma separated list of case ids.", required = true)
        @RequestParam(name = "case_ids") String caseIds,
        @ApiParam(value = "Page number to be served. 1 based index")
        @RequestParam(name = "page_number", required = false) String pageNumber,
        @ApiParam(value = "Page size - number of elements on the page.")
        @RequestParam(name = "page_size", required = false) String pageSize,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {

        CpoGetResponse casePaymentOrders = casePaymentOrdersService
            .getCasePaymentOrders(caseIds, pageNumber, pageSize, authorization);

        return casePaymentOrdersMapper.toCasePaymentOrdersDto(casePaymentOrders);
    }

    @ExceptionHandler(value = {CpoBadRequestException.class})
    public ResponseEntity<String> cpoBadRequestException(CpoBadRequestException e) {
        LOG.error("BadRequest - Error while calling case payment orders", e);
        return new ResponseEntity<>(e.getMessage(), BAD_REQUEST);
    }

    @ExceptionHandler(value = {CpoInternalServerErrorException.class})
    public ResponseEntity<String> cpoInternalServerErrorException(CpoInternalServerErrorException e) {
        LOG.error("InternalServerError - Error while calling case payment orders", e);
        return new ResponseEntity<>(e.getMessage(), INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = {CpoClientException.class})
    public ResponseEntity<String> cpoClientException(CpoClientException e) {
        LOG.error("ClientException - Error while calling case payment orders", e);
        return new ResponseEntity<>(e.getMessage(), HttpStatus.valueOf(e.getStatus()));
    }
}
