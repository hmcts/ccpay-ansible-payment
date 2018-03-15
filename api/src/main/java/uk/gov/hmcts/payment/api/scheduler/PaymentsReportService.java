package uk.gov.hmcts.payment.api.scheduler;

import org.joda.time.MutableDateTime;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.fees2.register.api.contract.Fee2Dto;
import uk.gov.hmcts.fees2.register.api.contract.FeeVersionDto;
import uk.gov.hmcts.payment.api.contract.FeeCsvDto;
import uk.gov.hmcts.payment.api.contract.PaymentCsvDto;
import uk.gov.hmcts.payment.api.dto.mapper.CardPaymentDtoMapper;
import uk.gov.hmcts.payment.api.dto.mapper.CreditAccountDtoMapper;
import uk.gov.hmcts.payment.api.email.CardPaymentReconciliationReportEmail;
import uk.gov.hmcts.payment.api.email.CreditAccountReconciliationReportEmail;
import uk.gov.hmcts.payment.api.email.Email;
import uk.gov.hmcts.payment.api.email.EmailService;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.service.CardPaymentService;
import uk.gov.hmcts.payment.api.service.CreditAccountPaymentService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.hmcts.payment.api.email.EmailAttachment.csv;

@Service
@Transactional
public class PaymentsReportService {

    private static final Logger LOG = getLogger(PaymentsReportService.class);

    private static final String BYTE_ARRAY_OUTPUT_STREAM_NEWLINE = "\r\n";

    private static final String CARD_PAYMENTS_CSV_FILE_PREFIX = "hmcts_card_payments_";

    private static final String CREDIT_ACCOUNT_PAYMENTS_CSV_FILE_PREFIX = "hmcts_credit_account_payments_";

    private static final String PAYMENTS_CSV_FILE_EXTENSION = ".csv";

    private static final Charset utf8 = Charset.forName("UTF-8");

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private static final String CARD_PAYMENTS_HEADER = "Service,Payment Group reference,Payment reference,"
        + "Payment created date,Payment status updated date,Payment status,Payment channel,Payment method,Payment amount,"
        + "Site id,Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code";

    private static final String CREDIT_ACCOUNT_PAYMENTS_HEADER = "Service,Payment Group reference,Payment reference,CCD reference,Case reference,"
        + "Organisation name,Customer internal reference,PBA Number,Payment created date,Payment status updated date,"
        + "Payment status,Payment channel,Payment method,Payment amount,"
        + "Site id,Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code,"
        + "Fee code,Version,Calculated amount,Memoline,Natural account code";

    private CardPaymentService<PaymentFeeLink, String> cardPaymentService;

    private CreditAccountPaymentService<PaymentFeeLink, String> creditAccountPaymentService;

    private CardPaymentDtoMapper cardPaymentDtoMapper;
    private CreditAccountDtoMapper creditAccountDtoMapper;

    private EmailService emailService;

    private CardPaymentReconciliationReportEmail cardPaymentReconciliationReportEmail;
    private CreditAccountReconciliationReportEmail creditAccountReconciliationReportEmail;

    @Autowired
    public PaymentsReportService(@Qualifier("loggingCardPaymentService") CardPaymentService<PaymentFeeLink, String> cardPaymentService, CardPaymentDtoMapper cardPaymentDtoMapper,
                                 @Qualifier("loggingCreditAccountPaymentService") CreditAccountPaymentService<PaymentFeeLink, String> creditAccountPaymentService,
                                 CreditAccountDtoMapper creditAccountDtoMapper, EmailService emailService,
                                 CardPaymentReconciliationReportEmail cardPaymentReconciliationReportEmail,
                                 CreditAccountReconciliationReportEmail creditAccountReconciliationReportEmail1) {
        this.cardPaymentService = cardPaymentService;
        this.cardPaymentDtoMapper = cardPaymentDtoMapper;
        this.emailService = emailService;
        this.cardPaymentReconciliationReportEmail = cardPaymentReconciliationReportEmail;
        this.creditAccountReconciliationReportEmail = creditAccountReconciliationReportEmail1;
        this.creditAccountPaymentService = creditAccountPaymentService;
        this.creditAccountDtoMapper = creditAccountDtoMapper;

    }

    public void generateCardPaymentsCsvAndSendEmail(String startDate, String endDate, Map<String, Fee2Dto> feesDataMap) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        sdf.setLenient(false);
        try {
            Date fromDate = startDate == null ? sdf.parse(getYesterdaysDate()) : sdf.parse(startDate);
            Date toDate = endDate == null ? sdf.parse(getTodaysDate()) : sdf.parse(endDate);

            if (fromDate.after(toDate) || fromDate.compareTo(toDate) == 0) {
                LOG.error("PaymentsReportService - Error while card  payments csv file. Incorrect start and end dates ");
                return;

            }
            List<PaymentCsvDto> cardPayments = cardPaymentService.search(fromDate, toDate).stream()
                .map(cardPaymentDtoMapper::toReconciliationResponseDto).collect(Collectors.toList());

            List populatedCardPayments;
            if (!feesDataMap.isEmpty()) {
                populatedCardPayments = populateFeesData(cardPayments, feesDataMap);
            } else {
                populatedCardPayments = cardPayments;
            }

            String cardPaymentCsvFileNameSuffix = LocalDateTime.now().format(formatter);
            String paymentsCsvFileName = CARD_PAYMENTS_CSV_FILE_PREFIX + cardPaymentCsvFileNameSuffix + PAYMENTS_CSV_FILE_EXTENSION;
            generateCsvAndSendEmail(populatedCardPayments, paymentsCsvFileName, CARD_PAYMENTS_HEADER, cardPaymentReconciliationReportEmail);
        } catch (ParseException paex) {

            LOG.error("PaymentsReportService - Error while creating card payments csv file." +
                " Error message is " + paex.getMessage() + ". Expected format is dd-mm-yyyy.");

        }
    }

    public void generateCreditAccountPaymentsCsvAndSendEmail(String startDate, String endDate, Map<String, Fee2Dto> feesDataMap) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        sdf.setLenient(false);
        try {
            Date fromDate = startDate == null ? sdf.parse(getYesterdaysDate()) : sdf.parse(startDate);
            Date toDate = endDate == null ? sdf.parse(getTodaysDate()) : sdf.parse(endDate);

            if (fromDate.after(toDate) || fromDate.compareTo(toDate) == 0) {
                LOG.error("PaymentsReportService - Error while creating credit account payments csv file. Incorrect start and end dates ");
                return;

            }

            List<PaymentCsvDto> creditAccountPayments = creditAccountPaymentService.search(fromDate, toDate).stream()
                .map(creditAccountDtoMapper::toReconciliationResponseDto).collect(Collectors.toList());

            List populatedCreditAccountPayments;
            if (!feesDataMap.isEmpty()) {
                populatedCreditAccountPayments = populateFeesData(creditAccountPayments, feesDataMap);
            } else {
                populatedCreditAccountPayments = creditAccountPayments;
            }

            String fileNameSuffix = LocalDateTime.now().format(formatter);
            String paymentsCsvFileName = CREDIT_ACCOUNT_PAYMENTS_CSV_FILE_PREFIX + fileNameSuffix + PAYMENTS_CSV_FILE_EXTENSION;
            generateCsvAndSendEmail(populatedCreditAccountPayments, paymentsCsvFileName, CREDIT_ACCOUNT_PAYMENTS_HEADER, creditAccountReconciliationReportEmail);
        } catch (ParseException paex) {

            LOG.error("PaymentsReportService - Error while creating credit account payments csv file."
                + " Error message is " + paex.getMessage() + ". Expected format is dd-mm-yyyy.");

        }
    }

    private void generateCsvAndSendEmail(List<PaymentCsvDto> payments, String paymentsCsvFileName, String header, Email mail) {

        byte[] paymentsByteArray = createPaymentsCsvByteArray(payments, paymentsCsvFileName, header);
        sendEmail(mail, paymentsByteArray, paymentsCsvFileName);

    }

    private byte[] createPaymentsCsvByteArray(List<PaymentCsvDto> payments, String paymentsCsvFileName, String header) {
        byte[] paymentsCsvByteArray = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            bos.write(header.getBytes(utf8));
            bos.write(BYTE_ARRAY_OUTPUT_STREAM_NEWLINE.getBytes(utf8));
            for (PaymentCsvDto payment : payments) {
                if (paymentsCsvFileName.startsWith(CARD_PAYMENTS_CSV_FILE_PREFIX)) {
                    bos.write(payment.toCardPaymentCsv().getBytes(utf8));
                } else if (paymentsCsvFileName.startsWith(CREDIT_ACCOUNT_PAYMENTS_CSV_FILE_PREFIX)) {
                    bos.write(payment.toCreditAccountPaymentCsv().getBytes(utf8));
                }
                bos.write(BYTE_ARRAY_OUTPUT_STREAM_NEWLINE.getBytes(utf8));
            }

            LOG.info("PaymentsReportService - Total " + payments.size() + " payments records written in payments csv file " + paymentsCsvFileName);

            paymentsCsvByteArray = bos.toByteArray();

        } catch (IOException ex) {

            LOG.error("PaymentsReportService - Error while creating card payments csv file " + paymentsCsvFileName + ". Error message is " + ex.getMessage());

        }
        return paymentsCsvByteArray;

    }

    private List<PaymentCsvDto> populateFeesData(List<PaymentCsvDto> payments, Map<String, Fee2Dto> feesDataMap) {
        for (PaymentCsvDto payment : payments) {
            List<FeeCsvDto> fees = payment.getFees();
            for (FeeCsvDto unPopulatedFee : fees) {
                Fee2Dto feeFromFeesRegister = feesDataMap.get(unPopulatedFee.getCode());
                if(null!=feeFromFeesRegister) {
                    if(unPopulatedFee.getVersion().equals(feeFromFeesRegister.getCurrentVersion().getVersion())) {
                        unPopulatedFee.setMemoLine(feeFromFeesRegister.getCurrentVersion().getMemoLine());
                        unPopulatedFee.setNaturalAccountCode(feeFromFeesRegister.getCurrentVersion().getNaturalAccountCode());
                    }
                    else{
                        Optional<FeeVersionDto> optionalMatchingFeeVersionDto= feeFromFeesRegister.getFeeVersionDtos()
                            .stream().filter(versionDto -> versionDto.getVersion().equals(unPopulatedFee.getVersion())).findFirst();
                        if(optionalMatchingFeeVersionDto.isPresent()) {
                            unPopulatedFee.setMemoLine(optionalMatchingFeeVersionDto.get().getMemoLine());
                            unPopulatedFee.setNaturalAccountCode(optionalMatchingFeeVersionDto.get().getNaturalAccountCode());
                        }
                    }
                }
            }
            payment.setFees(fees);

        }
        return payments;
    }


    private void sendEmail(Email email, byte[] paymentsCsvByteArray, String csvFileName) {
        email.setAttachments(newArrayList(csv(paymentsCsvByteArray, csvFileName)));
        emailService.sendEmail(email);

        LOG.info("PaymentsReportService - Payments report email sent to " + Arrays.toString(email.getTo()));

    }

    private String getYesterdaysDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        Date now = new Date();
        MutableDateTime mtDtNow = new MutableDateTime(now);
        mtDtNow.addDays(-1);
        return sdf.format(mtDtNow.toDate());
    }

    private String getTodaysDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        return sdf.format(new Date());
    }

}

