package uk.gov.hmcts.payment.api.controllers;


import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.contract.*;
import uk.gov.hmcts.payment.api.contract.util.CurrencyCode;
import uk.gov.hmcts.payment.api.model.PaymentStatus;
import uk.gov.hmcts.payment.api.service.ReplayCreditAccountPaymentService;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentException;
import uk.gov.hmcts.payment.api.v1.model.exceptions.PaymentNotFoundException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api(tags = {"Replay Credit Account Payment"})
public class ReplayCreditAccountPaymentController {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayCreditAccountPaymentController.class);

    private static final String FAILED = "failed";


    private final LaunchDarklyFeatureToggler featureToggler;

    private final ReplayCreditAccountPaymentService replayCreditAccountPaymentService;
    private final CreditAccountPaymentController creditAccountPaymentController;

    private final static String PAYMENT_STATUS_HISTORY_MESSAGE = "System Failure. Not charged";


    @Autowired
    public ReplayCreditAccountPaymentController(LaunchDarklyFeatureToggler featureToggler,
                                                ReplayCreditAccountPaymentService replayCreditAccountPaymentService,
                                                CreditAccountPaymentController creditAccountPaymentController) {
        this.featureToggler = featureToggler;
        this.replayCreditAccountPaymentService = replayCreditAccountPaymentService;
        this.creditAccountPaymentController = creditAccountPaymentController;
    }

    @ApiOperation(value = "Replay credit account payment", notes = "Replay credit account payment")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Replay Payment Completed Successfully"),
        @ApiResponse(code = 400, message = "BAD Request"),
        @ApiResponse(code = 500, message = "Replay Payment failed")
    })
    @PostMapping(value = "/replay-credit-account-payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<String> replayCreditAccountPayment(@RequestParam("csvFile") MultipartFile replayPBAPaymentsFile,
                                                             @RequestParam("isReplayPBAPayments") Boolean isReplayPBAPayments,
                                                             @RequestHeader(required = false) MultiValueMap<String, String> headers) {

        LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT: isReplayPBAPayments = " + isReplayPBAPayments);

        //Validate csv file
        if (replayPBAPaymentsFile.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Parse CSV file to get list of PBA payments for replay
        try (Reader reader = new BufferedReader(new InputStreamReader(replayPBAPaymentsFile.getInputStream()))) {

            // Create csv reader to get list of credit account payments
            CsvToBean<ReplayCreditAccountPaymentRequest> csvToBean = new CsvToBeanBuilder(reader)
                .withType(ReplayCreditAccountPaymentRequest.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build();

            // List of replay credit account payment requests
            List<ReplayCreditAccountPaymentRequest> replayCreditAccountPaymentRequestList = csvToBean.parse();
            LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT: CSV TO OBJECT PARSED COUNT = " + replayCreditAccountPaymentRequestList.size());

            List<ReplayCreditAccountPaymentDTO> replayCreditAccountPaymentDTOList = replayCreditAccountPaymentRequestList.stream()
                .map(replayCreditAccountPaymentRequest -> populateRequestToDTO(isReplayPBAPayments, replayCreditAccountPaymentRequest))
                .collect(Collectors.toList());

            if (null != replayCreditAccountPaymentDTOList && !replayCreditAccountPaymentDTOList.isEmpty()) {
                LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT: REQUEST TO DTO COUNT = " + replayCreditAccountPaymentDTOList.size());

                // Note : Repeat the below 3 Steps for All Payments in the requested CSV

                // 1. Update the Payment Status to be 'failed'
                // 2. Update Payment History to reflect Error status and comments 'System Failure. Not charged'
                replayCreditAccountPaymentDTOList.stream().forEach(replayCreditAccountPaymentDTO -> {
                    try {
                        replayCreditAccountPaymentService.updatePaymentStatusByReference(
                            replayCreditAccountPaymentDTO.getExistingPaymentReference(),
                            PaymentStatus.FAILED, PAYMENT_STATUS_HISTORY_MESSAGE);

                        // 3. Replay New PBA Payment as Data Provided in CSV
                        if (isReplayPBAPayments) {
                            createPBAPayments(replayCreditAccountPaymentDTO,headers);
                        }
                    } catch (Exception exception) {
                        LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT ERROR: PBA Payment not found for reference =" + replayCreditAccountPaymentDTO.getExistingPaymentReference());
                    }
                });

                LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT:  PROCESS COMPLETED For " + replayCreditAccountPaymentDTOList.size() + " Payments");
            }

        } catch (Exception ex) {
            LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT: Replay PBA Payment failed for All");
            throw new PaymentException(ex);
        }

        return new ResponseEntity<String>("Replay Payment Completed Successfully", HttpStatus.OK);
    }

    private void createPBAPayments(ReplayCreditAccountPaymentDTO replayCreditAccountPaymentDTO,MultiValueMap<String, String> headers) {

        try {
            // Call the Payment PBA API v1
            ResponseEntity<PaymentDto> paymentDtoResponseEntity = creditAccountPaymentController.createCreditAccountPayment(replayCreditAccountPaymentDTO.getCreditAccountPaymentRequest(),headers);
            if (paymentDtoResponseEntity != null) {
                PaymentDto paymentDto = paymentDtoResponseEntity.getBody();
                if (paymentDto != null && paymentDto.getReference() != null) {
                    String newPaymentReference = paymentDto.getReference();
                    LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT:  Existing Payment Reference : " + replayCreditAccountPaymentDTO.getExistingPaymentReference()
                        + " New Payment Reference : " + newPaymentReference
                        + " CCD_CASE_NUMBER : " + replayCreditAccountPaymentDTO.getCreditAccountPaymentRequest().getCcdCaseNumber());
                }
            }

        } catch (Exception exception) {
            LOG.info("REPLAY_CREDIT_ACCOUNT_PAYMENT ERROR: Replay PBA Payment ERROR for reference =" + replayCreditAccountPaymentDTO.getExistingPaymentReference());
        }
    }


    private ReplayCreditAccountPaymentDTO populateRequestToDTO(Boolean isReplayPBAPayments,
                                                               ReplayCreditAccountPaymentRequest replayCreditAccountPaymentRequest) {
        if (isReplayPBAPayments) {
            return ReplayCreditAccountPaymentDTO.replayCreditAccountPaymentDTOWith()
                .existingPaymentReference(replayCreditAccountPaymentRequest.getExistingPaymentReference())
                .creditAccountPaymentRequest(CreditAccountPaymentRequest.createCreditAccountPaymentRequestDtoWith()
                    .amount(replayCreditAccountPaymentRequest.getAmount())
                    .ccdCaseNumber(replayCreditAccountPaymentRequest.getCcdCaseNumber())
                    .accountNumber(replayCreditAccountPaymentRequest.getPbaNumber().replace("\"", ""))
                    .description(replayCreditAccountPaymentRequest.getDescription())
                    .caseReference(replayCreditAccountPaymentRequest.getCaseReference().replace("\"", ""))
                    .service(replayCreditAccountPaymentRequest.getService())
                    .currency(CurrencyCode.valueOf(replayCreditAccountPaymentRequest.getCurrency()))
                    .customerReference(replayCreditAccountPaymentRequest.getCustomerReference())
                    .organisationName(replayCreditAccountPaymentRequest.getOrganisationName().replace("\"", ""))
                    .siteId(replayCreditAccountPaymentRequest.getSiteId())
                    .fees(Collections.singletonList(FeeDto.feeDtoWith()
                        .code(replayCreditAccountPaymentRequest.getCode())
                        .calculatedAmount(replayCreditAccountPaymentRequest.getCalculatedAmount())
                        .version(replayCreditAccountPaymentRequest.getVersion())
                        .build()))
                    .build())
                .build();
        } else {
            return ReplayCreditAccountPaymentDTO.replayCreditAccountPaymentDTOWith()
                .existingPaymentReference(replayCreditAccountPaymentRequest.getExistingPaymentReference())
                .build();
        }
    }

    @ExceptionHandler(value = {PaymentNotFoundException.class})
    public ResponseEntity httpClientErrorException() {
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = {PaymentException.class})
    public ResponseEntity returnInternalError(PaymentException ex) {
        return new ResponseEntity(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

