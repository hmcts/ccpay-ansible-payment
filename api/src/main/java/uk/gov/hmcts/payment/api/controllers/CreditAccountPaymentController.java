package uk.gov.hmcts.payment.api.controllers;


import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.CreditAccountPaymentRequest;
import uk.gov.hmcts.payment.api.contract.PaymentDto;
import uk.gov.hmcts.payment.api.dto.AccountDto;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.mapper.PBAStatusErrorMapper;
import uk.gov.hmcts.payment.api.exception.AccountNotFoundException;
import uk.gov.hmcts.payment.api.exception.AccountServiceUnavailableException;
import uk.gov.hmcts.payment.api.mapper.CreditAccountPaymentRequestMapper;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.AccountService;
import uk.gov.hmcts.payment.api.service.CreditAccountPaymentService;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.DuplicatePaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;
import uk.gov.hmcts.payment.api.validators.DuplicatePaymentValidator;

import javax.validation.Valid;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Api(tags = {"Credit Account Payment"})
public class CreditAccountPaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(CreditAccountPaymentController.class);

    private static final String FAILED = "failed";


    private final CreditAccountPaymentService<PaymentFeeLink, String> creditAccountPaymentService;
    private final CreditAccountDtoMapper creditAccountDtoMapper;
    private final AccountService<AccountDto, String> accountService;
    private final DuplicatePaymentValidator paymentValidator;
    private final FeePayApportionService feePayApportionService;
    private final LaunchDarklyFeatureToggler featureToggler;
    private final PBAStatusErrorMapper pbaStatusErrorMapper;
    private final CreditAccountPaymentRequestMapper requestMapper;


    @Autowired
    public CreditAccountPaymentController(@Qualifier("loggingCreditAccountPaymentService") CreditAccountPaymentService<PaymentFeeLink, String> creditAccountPaymentService,
                                          CreditAccountDtoMapper creditAccountDtoMapper,
                                          AccountService<AccountDto, String> accountService,
                                          DuplicatePaymentValidator paymentValidator,
                                          FeePayApportionService feePayApportionService, LaunchDarklyFeatureToggler featureToggler,
                                          PBAStatusErrorMapper pbaStatusErrorMapper,
                                          CreditAccountPaymentRequestMapper requestMapper) {
        this.creditAccountPaymentService = creditAccountPaymentService;
        this.creditAccountDtoMapper = creditAccountDtoMapper;
        this.accountService = accountService;
        this.paymentValidator = paymentValidator;
        this.feePayApportionService = feePayApportionService;
        this.featureToggler = featureToggler;
        this.pbaStatusErrorMapper = pbaStatusErrorMapper;
        this.requestMapper = requestMapper;
    }

    @ApiOperation(value = "Create credit account payment", notes = "Create credit account payment")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Payment created"),
        @ApiResponse(code = 400, message = "Payment creation failed"),
        @ApiResponse(code = 403, message = "Payment failed due to insufficient funds or the account being on hold"),
        @ApiResponse(code = 404, message = "Account information could not be found"),
        @ApiResponse(code = 504, message = "Unable to retrieve account information, please try again later"),
        @ApiResponse(code = 422, message = "Invalid or missing attribute")
    })
    @PostMapping(value = "/credit-account-payments")
    @ResponseBody
    @Transactional
    public ResponseEntity<PaymentDto> createCreditAccountPayment(@Valid @RequestBody CreditAccountPaymentRequest creditAccountPaymentRequest) throws CheckDigitException {
        String paymentGroupReference = PaymentReference.getInstance().getNext();

        final Payment payment = requestMapper.mapPBARequest(creditAccountPaymentRequest);

        List<PaymentFee> fees = requestMapper.mapPBAFeesFromRequest(creditAccountPaymentRequest);

        LOG.debug("Create credit account request for PaymentGroupRef:" + paymentGroupReference + " ,with Payment and " + fees.size() + " - Fees");

        LOG.info("CreditAccountPayment received for ccdCaseNumber : {} serviceType : {} pbaNumber : {} amount : {} NoOfFees : {}",
            payment.getCcdCaseNumber(), payment.getServiceType(), payment.getPbaNumber(), payment.getAmount(), fees.size());
        LOG.info("Checking with Liberata for Service : {}", creditAccountPaymentRequest.getService());
        AccountDto accountDetails;
        try {
            accountDetails = accountService.retrieve(creditAccountPaymentRequest.getAccountNumber());
            LOG.info("CreditAccountPayment received for ccdCaseNumber : {} Liberata AccountStatus : {}", payment.getCcdCaseNumber(), accountDetails.getStatus());
        } catch (HttpClientErrorException ex) {
            LOG.error("Account information could not be found, exception: {}", ex.getMessage());
            throw new AccountNotFoundException("Account information could not be found");
        } catch (Exception ex) {
            LOG.error("Unable to retrieve account information, exception: {}", ex.getMessage());
            throw new AccountServiceUnavailableException("Unable to retrieve account information, please try again later");
        }

        pbaStatusErrorMapper.setPaymentStatus(creditAccountPaymentRequest, payment, accountDetails);
        checkDuplication(payment, fees);
        PaymentFeeLink paymentFeeLink = creditAccountPaymentService.create(payment, fees, paymentGroupReference);

        if (payment.getPaymentStatus().getName().equals(FAILED)) {
            LOG.info("CreditAccountPayment Response 403(FORBIDDEN) for ccdCaseNumber : {} PaymentStatus : {}", payment.getCcdCaseNumber(), payment.getPaymentStatus().getName());
            return new ResponseEntity<>(creditAccountDtoMapper.toCreateCreditAccountPaymentResponse(paymentFeeLink), HttpStatus.FORBIDDEN);
        }

        // trigger Apportion based on the launch darkly feature flag
        boolean apportionFeature = featureToggler.getBooleanValue("apportion-feature", false);
        LOG.info("ApportionFeature Flag Value in CreditAccountPaymentController : {}", apportionFeature);
        if (apportionFeature) {
            Payment pbaPayment = paymentFeeLink.getPayments().get(0);
            pbaPayment.setPaymentLink(paymentFeeLink);
            feePayApportionService.processApportion(pbaPayment);

            // Update Fee Amount Due as Payment Status received from PBA Payment as SUCCESS
            if (Lists.newArrayList("success").contains(pbaPayment.getPaymentStatus().getName().toLowerCase())) {
                LOG.info("Update Fee Amount Due as Payment Status received from PBA Payment as {}" + pbaPayment.getPaymentStatus().getName());
                feePayApportionService.updateFeeAmountDue(pbaPayment);
            }
        }

        LOG.info("CreditAccountPayment Response 201(CREATED) for ccdCaseNumber : {} PaymentStatus : {}", payment.getCcdCaseNumber(), payment.getPaymentStatus().getName());
        return new ResponseEntity<>(creditAccountDtoMapper.toCreateCreditAccountPaymentResponse(paymentFeeLink), HttpStatus.CREATED);
    }

    @ApiOperation(value = "Get credit account payment details by payment reference", notes = "Get payment details for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment retrieved"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @RequestMapping(value = "/credit-account-payments/{paymentReference}", method = GET)
    public ResponseEntity<PaymentDto> retrieve(@PathVariable("paymentReference") String paymentReference) {
        PaymentFeeLink paymentFeeLink = creditAccountPaymentService.retrieveByPaymentReference(paymentReference);
        Payment payment = paymentFeeLink.getPayments().stream().filter(p -> p.getReference().equals(paymentReference))
            .findAny()
            .orElseThrow(PaymentNotFoundException::new);
        List<PaymentFee> fees = paymentFeeLink.getFees();
        return new ResponseEntity<>(creditAccountDtoMapper.toRetrievePaymentResponse(payment, fees), HttpStatus.OK);
    }

    @ApiOperation(value = "Get credit account payment statuses by payment reference", notes = "Get payment statuses for supplied payment reference")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Payment retrieved"),
        @ApiResponse(code = 404, message = "Payment not found")
    })
    @RequestMapping(value = "/credit-account-payments/{paymentReference}/statuses", method = GET)
    public ResponseEntity<PaymentDto> retrievePaymentStatus(@PathVariable("paymentReference") String paymentReference) {
        PaymentFeeLink paymentFeeLink = creditAccountPaymentService.retrieveByPaymentReference(paymentReference);
        Payment payment = paymentFeeLink.getPayments().stream().filter(p -> p.getReference().equals(paymentReference))
            .findAny()
            .orElseThrow(PaymentNotFoundException::new);

        return new ResponseEntity<>(creditAccountDtoMapper.toRetrievePaymentStatusResponse(payment), HttpStatus.OK);
    }

    @ExceptionHandler(value = {PaymentNotFoundException.class})
    public ResponseEntity httpClientErrorException() {
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(PaymentException.class)
    public String return400(PaymentException ex) {
        LOG.error("Error while processing payment request:", ex);
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DuplicatePaymentException.class)
    public String returnDuplicateError(DuplicatePaymentException ex) {
        LOG.error("Duplicate pba payments error:", ex);
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(AccountNotFoundException.class)
    public String return404(AccountNotFoundException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    @ExceptionHandler(AccountServiceUnavailableException.class)
    public String return504(AccountServiceUnavailableException ex) {
        return ex.getMessage();
    }

    private void checkDuplication(Payment payment, List<PaymentFee> fees) {
        paymentValidator.checkDuplication(payment, fees);

    }

}
