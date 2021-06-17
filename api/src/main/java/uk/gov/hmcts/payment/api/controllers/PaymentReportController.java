package uk.gov.hmcts.payment.api.controllers;

import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.payment.api.reports.PaymentsReportFacade;
import uk.gov.hmcts.payment.api.scheduler.Clock;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

@RestController
@Api(tags = {"Payment Report"})
@SwaggerDefinition(tags = {@Tag(name = "PaymentReportController", description = "Payment report REST API")})
public class PaymentReportController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE;

    private final PaymentsReportFacade paymentsReportFacade;
    private final PaymentValidator validator;
    private final Clock clock;

    @Autowired
    public PaymentReportController(PaymentsReportFacade paymentsReportFacade, PaymentValidator validator, Clock clock) {
        this.paymentsReportFacade = paymentsReportFacade;
        this.validator = validator;
        this.clock = clock;
    }

    @ApiOperation(value = "Email payment csv reports", notes = "fetch payments for between dates, enter the date in format YYYY-MM-DD")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Reports sent")
    })
    @PostMapping(value = "/jobs/email-pay-reports")
    public void generateAndEmailReport(@RequestParam(name = "payment_method", required = false) Optional<String> paymentMethodType,
                                       @RequestParam(name = "service_name", required = false) Optional<String> serviceType,
                                       @RequestParam(name = "start_date", required = false) Optional<String> startDateString,
                                       @RequestParam(name = "end_date", required = false) Optional<String> endDateString) {

        validator.validate(paymentMethodType, startDateString, endDateString);

        Date fromDate = startDateString.map(s -> clock.atStartOfDay(s, FORMATTER)).orElseGet(clock::getYesterdayDate);
        Date toDate = endDateString.map(s -> clock.atEndOfDay(s, FORMATTER)).orElseGet(clock::getTodayDate);
        String service = serviceType.isPresent() ? serviceType.get() : null;
        PaymentMethodType paymentMethodTypeName = paymentMethodType.map(value -> PaymentMethodType.valueOf(value.toUpperCase())).orElse(null);

        paymentsReportFacade.generateCsvAndSendEmail(fromDate, toDate, paymentMethodTypeName, service);
    }
}
