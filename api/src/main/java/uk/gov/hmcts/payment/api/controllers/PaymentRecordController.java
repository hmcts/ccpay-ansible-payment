package uk.gov.hmcts.payment.api.controllers;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.dto.PaymentRecordRequest;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentProviderRepository;
import uk.gov.hmcts.payment.api.service.PaymentRecordService;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.service.ReferenceDataService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.referencedata.dto.SiteDTO;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@Api(tags = {"Payment Record"})
@SwaggerDefinition(tags = {@Tag(name = "PaymentRecordController", description = "Payment record REST API")})
public class PaymentRecordController {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentRecordController.class);

    private static final String DEFAULT_CURRENCY = "GBP";

    private final PaymentRecordService<PaymentFeeLink, String> paymentRecordService;
    private final PaymentDtoMapper paymentDtoMapper;
    private final PaymentProviderRepository paymentProviderRespository;
    private final ReferenceDataService<SiteDTO> referenceDataService;

    @Autowired
    private PaymentService<PaymentFeeLink, String> paymentService;

    @Autowired
    public PaymentRecordController(PaymentRecordService<PaymentFeeLink, String> paymentRecordService,
                                   PaymentDtoMapper paymentDtoMapper,
                                   PaymentProviderRepository paymentProviderRespository,
                                   ReferenceDataService<SiteDTO> referenceDataService) {
        this.paymentRecordService = paymentRecordService;
        this.paymentDtoMapper = paymentDtoMapper;
        this.paymentProviderRespository = paymentProviderRespository;
        this.referenceDataService = referenceDataService;
    }


    @ApiOperation(value = "Record a payment", notes = "Record a payment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Payment created"),
        @ApiResponse(code = 400, message = "Payment creation failed"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @RequestMapping(value = "/payment-records", method = POST)
    @ResponseBody
    public ResponseEntity<PaymentDto> recordPayment(@Valid @RequestBody PaymentRecordRequest paymentRecordRequest) throws CheckDigitException {
        String paymentGroupReference = PaymentReference.getInstance().getNext();

        List<SiteDTO> sites = referenceDataService.getSiteIDs();

        if (sites.stream().noneMatch(o -> o.getSiteID().equals(paymentRecordRequest.getSiteId()))) {
            throw new PaymentException("Invalid siteID: " + paymentRecordRequest.getSiteId());
        }

        PaymentProvider paymentProvider = paymentRecordRequest.getExternalProvider() != null ?
            paymentProviderRespository.findByNameOrThrow(paymentRecordRequest.getExternalProvider())
            : null;

        Payment payment = Payment.paymentWith()
            .amount(paymentRecordRequest.getAmount())
            .caseReference(paymentRecordRequest.getReference())
            .currency(paymentRecordRequest.getCurrency().getCode())
            .paymentProvider(paymentProvider)
            .externalReference(paymentRecordRequest.getExternalReference())
            .serviceType(paymentService.getServiceNameByCode(paymentRecordRequest.getService()))
            .paymentMethod(PaymentMethod.paymentMethodWith().name(paymentRecordRequest.getPaymentMethod().getType()).build())
            .siteId(paymentRecordRequest.getSiteId())
            .giroSlipNo(paymentRecordRequest.getGiroSlipNo())
            .reportedDateOffline(DateTime.parse(paymentRecordRequest.getReportedDateOffline()).withZone(DateTimeZone.UTC).toDate())
            .paymentChannel(paymentRecordRequest.getPaymentChannel())
            .paymentStatus(paymentRecordRequest.getPaymentStatus())
            .build();

        List<PaymentFee> fees = paymentRecordRequest.getFees().stream()
            .map(f -> paymentDtoMapper.toFee(f))
            .collect(Collectors.toList());

        LOG.debug("Record payment for PaymentGroupRef:" + paymentGroupReference + " ,with Payment and " + fees.size() + " - Fees");

        PaymentFeeLink paymentFeeLink = paymentRecordService.recordPayment(payment, fees, paymentGroupReference);

        return new ResponseEntity<>(paymentDtoMapper.toCreateRecordPaymentResponse(paymentFeeLink), HttpStatus.CREATED);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(PaymentException.class)
    public String return400(PaymentException ex) {
        return ex.getMessage();
    }
}

