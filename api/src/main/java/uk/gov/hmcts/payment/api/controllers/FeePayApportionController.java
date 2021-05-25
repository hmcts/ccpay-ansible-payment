package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.domain.service.PaymentDomainService;
import uk.gov.hmcts.payment.api.dto.OrderPaymentGroupResponse;
import uk.gov.hmcts.payment.api.dto.PaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.RetrieveOrderPaymentGroupDto;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentGroupDtoMapper;
import uk.gov.hmcts.payment.api.model.FeePayApportion;
import uk.gov.hmcts.payment.api.model.Payment;
import uk.gov.hmcts.payment.api.model.PaymentFee;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.model.PaymentFeeRepository;
import uk.gov.hmcts.payment.api.service.PaymentService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@Api(tags = {"PaymentApportion"})
@SwaggerDefinition(tags = {@Tag(name = "FeePayApportionController", description = "FeePayApportion REST API")})
public class FeePayApportionController {

    private final PaymentService<PaymentFeeLink, String> paymentService;

    private final PaymentFeeRepository paymentFeeRepository;

    private final PaymentGroupDtoMapper paymentGroupDtoMapper;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    private static final Logger LOG = LoggerFactory.getLogger(FeePayApportionController.class);

    @Autowired
    public FeePayApportionController(PaymentService<PaymentFeeLink, String> paymentService,PaymentFeeRepository paymentFeeRepository,PaymentGroupDtoMapper paymentGroupDtoMapper,LaunchDarklyFeatureToggler featureToggler) {
        this.paymentService = paymentService;
        this.paymentFeeRepository = paymentFeeRepository;
        this.paymentGroupDtoMapper = paymentGroupDtoMapper;
        this.featureToggler = featureToggler;
    }

    @ApiOperation(value = "Get apportion details by payment reference", notes = "Get apportion details for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Apportionment Details retrieved"),
        @ApiResponse(code = 403, message = "Payment info forbidden"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @GetMapping(value = "/payment-groups/fee-pay-apportion/{paymentreference}")
    public ResponseEntity<PaymentGroupDto> retrieveApportionDetails(@PathVariable("paymentreference") String paymentReference) {
        LOG.info("Invoking new API in FeePayApportionController");
        PaymentFeeLink paymentFeeLink = paymentService.retrieve(paymentReference);
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature",false);
        LOG.info("apportionFeature value in FeePayApportionController: {}", apportionFeature);
        Optional<Payment> payment = paymentFeeLink.getPayments().stream()
            .filter(p -> p.getReference().equals(paymentReference)).findAny();

        if (payment.isPresent() && apportionFeature)
        {
            LOG.info("Apportion feature is true and payment is available in FeePayApportionController");
            List<FeePayApportion> feePayApportionList = paymentService.findByPaymentId(payment.get().getId());
            if(feePayApportionList != null && !feePayApportionList.isEmpty()) {
                LOG.info("Apportion details available in FeePayApportionController");
                List<PaymentFee> feeList = new ArrayList<>();
                for (FeePayApportion feePayApportion : feePayApportionList)
                {
                    LOG.info("Inside FeePayApportion section in FeePayApportionController");
                    Optional<PaymentFee> apportionedFee = paymentFeeRepository.findById(feePayApportion.getFeeId());
                    if(apportionedFee.isPresent())
                    {
                        LOG.info("Apportioned fee is present");
                        PaymentFee fee = apportionedFee.get();
                        LOG.info("apportion amount value in FeePayApportionController: {}", feePayApportion.getApportionAmount());
                        fee.setApportionAmount(feePayApportion.getApportionAmount());
                        feeList.add(fee);
                                }
                            }
                paymentFeeLink.setFees(feeList);
            }

    }
        return new ResponseEntity<>(paymentGroupDtoMapper.toPaymentGroupDto(paymentFeeLink), HttpStatus.OK);
    }


    @ApiOperation(value = "Get apportion details by payment reference", notes = "Get apportion details for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Apportionment Details retrieved"),
        @ApiResponse(code = 403, message = "Payment info forbidden"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @GetMapping(value = "/orderpoc/payment-groups/fee-pay-apportion/{paymentreference}")
    public ResponseEntity<OrderPaymentGroupResponse> retrieveApportionDetailsForOrders(@PathVariable("paymentreference") String paymentReference) {
        LOG.info("Invoking new API in FeePayApportionController");
        final RetrieveOrderPaymentGroupDto retrieveOrderPaymentGroupDto = RetrieveOrderPaymentGroupDto.paymentGroupDtoWith().build();
        Payment payment = paymentDomainService.getPaymentByReference(paymentReference);
        return new ResponseEntity(paymentGroupDtoMapper.toPaymentGroupDtoForFeePayApportionment(retrieveOrderPaymentGroupDto,payment), HttpStatus.OK);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PaymentNotFoundException.class)
    public String notFound(PaymentNotFoundException ex) {
        return ex.getMessage();
    }
}
