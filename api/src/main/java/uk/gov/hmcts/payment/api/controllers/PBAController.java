package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.contract.PaymentsResponse;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api(tags = {"Pay By Account"})
@SwaggerDefinition(tags = {@Tag(name = "PBAController", description = "Pay by account REST API")})
public class PBAController {

    private final PaymentService<PaymentFeeLink, String> paymentService;

    private final PaymentDtoMapper paymentDtoMapper;
    

    @Autowired
    public PBAController(PaymentService<PaymentFeeLink, String> paymentService, PaymentDtoMapper paymentDtoMapper) {
        this.paymentService = paymentService;
        this.paymentDtoMapper = paymentDtoMapper;
    }

    @ApiOperation(value = "Get payments for a PBA account", notes = "Get list of payments")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payments retrieved"),
        @ApiResponse(code = 400, message = "Bad request")
    })
    @GetMapping(value = "/pba-accounts/{account}/payments")
    @PaymentExternalAPI
    public PaymentsResponse retrievePaymentsByAccount(@PathVariable(name = "account") String account) {

        List<PaymentFeeLink> paymentFeeLinks = paymentService.search(PaymentSearchCriteria.searchCriteriaWith().pbaNumber(account).build());

        List<PaymentDto> paymentDto = paymentFeeLinks.stream()
            .map(paymentDtoMapper::toReconciliationResponseDto).collect(Collectors.toList());

        return new PaymentsResponse(paymentDto);
    }
}
