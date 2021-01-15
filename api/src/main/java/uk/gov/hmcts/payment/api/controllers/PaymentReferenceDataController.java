package uk.gov.hmcts.payment.api.controllers;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.model.LegacySite;
import uk.gov.hmcts.payment.api.model.LegacySiteRepository;
import uk.gov.hmcts.payment.api.model.PaymentChannel;
import uk.gov.hmcts.payment.api.model.PaymentChannelRepository;
import uk.gov.hmcts.payment.api.model.PaymentMethod;
import uk.gov.hmcts.payment.api.model.PaymentMethodRepository;
import uk.gov.hmcts.payment.api.model.PaymentProvider;
import uk.gov.hmcts.payment.api.model.PaymentProviderRepository;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.model.PaymentStatusRepository;

import java.util.List;

@RestController
@Api(tags = {"Payment Reference Data"})
@RequestMapping("/refdata")
public class PaymentReferenceDataController {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentReferenceDataController.class);

    private final PaymentStatusRepository paymentStatusRepository;

    private final PaymentProviderRepository paymentProviderRespository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final PaymentChannelRepository paymentChannelRepository;

    private final LegacySiteRepository legacySiteRepository;


    @Autowired
    public PaymentReferenceDataController(PaymentStatusRepository paymentStatusRepository, PaymentProviderRepository paymentProviderRespository,
                                          PaymentMethodRepository paymentMethodRepository, PaymentChannelRepository paymentChannelRepository, LegacySiteRepository legacySiteRepository) {
        this.paymentStatusRepository = paymentStatusRepository;
        this.paymentProviderRespository = paymentProviderRespository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.legacySiteRepository = legacySiteRepository;
    }

    @ApiOperation(value = "Payment channels", notes = "Get all payment channels")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment channels found"),
        @ApiResponse(code = 404, message = "Payment channels not found")
    })
    @GetMapping("/channels")
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentChannel> findAllPaymentChannels() {
        List<PaymentChannel> paymentChannels = paymentChannelRepository.findAll();

        return paymentChannels;
    }

    @ApiOperation(value = "Payment methods", notes = "Get all payment methods")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment methods found"),
        @ApiResponse(code = 404, message = "Payment methods not found")
    })
    @GetMapping("/methods")
    public List<PaymentMethod> findAllPaymentMethods() {
        List<PaymentMethod> paymentMethods = paymentMethodRepository.findAll();

        return paymentMethods;
    }

    @ApiOperation(value = "Payment providers", notes = "Get all payment providers")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment providers found"),
        @ApiResponse(code = 404, message = "Payment providers not found")
    })
    @GetMapping("/providers")
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentProvider> findAllPaymentProviders() {
        List<PaymentProvider> paymentProviders = paymentProviderRespository.findAll();

        return paymentProviders;
    }

    @ApiOperation(value = "Payment status", notes = "Get all payment statuses")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment status found"),
        @ApiResponse(code = 404, message = "Payment status not found")
    })
    @GetMapping("/status")
    public List<PaymentStatus> findAllPaymentStatuses() {
        List<PaymentStatus> paymentStatus = paymentStatusRepository.findAll();

        return paymentStatus;
    }

    @ApiOperation(value = "Get allowed legacy sites")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Legacy sites retrieved successfully"),
        @ApiResponse(code = 404, message = "Legacy sites not found"),
        @ApiResponse(code = 401, message = "Credentials are required to access this resource")
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping(value = "/legacy-sites")
    public List<LegacySite> findAllLegacySites() {
        return legacySiteRepository.findAll();
    }
}
