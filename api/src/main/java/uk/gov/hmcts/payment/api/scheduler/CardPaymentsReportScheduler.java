package uk.gov.hmcts.payment.api.scheduler;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.reports.PaymentsReportService;

import java.util.Date;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class CardPaymentsReportScheduler {

    private static final Logger LOG = getLogger(CardPaymentsReportScheduler.class);
    private PaymentsReportService paymentsReportService;
    private FeesService feesService;

    @Value("${card.payments.report.startDate}")
    private String startDate;

    @Value("${card.payments.report.endDate}")
    private String endDate;

    @Autowired
    public CardPaymentsReportScheduler(PaymentsReportService paymentsReportService,FeesService feesService) {
        this.paymentsReportService= paymentsReportService;
        this.feesService = feesService;
    }

    public void generateCardPaymentsReportTask(Date fromDate, Date toDate) {
        LOG.info("CardPaymentsReportScheduler -  Start of scheduled job for HMCTS-Card Payments csv report file.");
        feesService.dailyRefreshOfFeesData();
        paymentsReportService.generateCardPaymentsCsvAndSendEmail(fromDate, toDate);
        LOG.info("CardPaymentsReportScheduler -  End of scheduled job for HMCTS-Card Payments csv report file.");
    }

}
