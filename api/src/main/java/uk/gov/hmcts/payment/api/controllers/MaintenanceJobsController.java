package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.dto.Reference;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.service.PaymentService;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.springframework.web.bind.annotation.RequestMethod.PATCH;

@RestController
@Api(tags = {"MaintenanceJobsController"})
@SwaggerDefinition(tags = {@Tag(name = "MaintenanceJobsController", description = "Maintenance Jobs API")})
public class MaintenanceJobsController {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceJobsController.class);

    private final PaymentService<PaymentFeeLink, String> paymentService;

    private final DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;

    @Autowired
    public MaintenanceJobsController(PaymentService<PaymentFeeLink, String> paymentService,
                                     DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService) {
        this.paymentService = paymentService;
        this.delegatingPaymentService = delegatingPaymentService;
    }

    @ApiOperation(value = "Update payment status", notes = "Updates the payment status on all gov pay pending card payments")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Reports sent")
    })
    @RequestMapping(value = "/jobs/card-payments-status-update", method = PATCH)
    @Transactional
    public void updatePaymentsStatus() throws ExecutionException, InterruptedException {

        List<Reference> referenceList = paymentService.listInitiatedStatusPaymentsReferences();

        LOG.warn("Found " + referenceList.size() + " references that require an status update");

        long count = referenceList
            .stream()
            .map(Reference::getReference)
            .map(delegatingPaymentService::retrieve)
            .filter(p -> p != null && p.getPayments() != null && p.getPayments().get(0) != null && p.getPayments().get(0).getStatus() != null)
            .count();

        LOG.warn(count + " payment references were successfully updated");

    }

    @ApiOperation(value = "Notify Callbacks", notes = "Notifies services that registered to be called back when a status update on a payment happens")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Reports sent")
    })
    @RequestMapping(value = "/jobs/notify-callbacks", method = PATCH)
    @Transactional
    public void notifyCallbacks() throws ExecutionException, InterruptedException {

        List<Reference> referenceList = paymentService.listInitiatedStatusPaymentsReferences();

        LOG.warn("Found " + referenceList.size() + " references that require an status update");

        long count = referenceList
            .stream()
            .map(Reference::getReference)
            .map(delegatingPaymentService::retrieve)
            .filter(p -> p != null && p.getPayments() != null && p.getPayments().get(0) != null && p.getPayments().get(0).getStatus() != null)
            .count();

        LOG.warn(count + " payment references were successfully updated");

    }

}
