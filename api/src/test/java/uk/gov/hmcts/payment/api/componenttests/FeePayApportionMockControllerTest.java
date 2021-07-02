package uk.gov.hmcts.payment.api.componenttests;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.payment.api.configuration.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.payment.api.dto.FeePayApportionCCDCase;
import uk.gov.hmcts.payment.api.model.*;
import uk.gov.hmcts.payment.api.service.FeePayApportionService;
import uk.gov.hmcts.payment.api.util.ReferenceUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "componenttest"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class FeePayApportionMockControllerTest {

    @Autowired
    private FeePayApportionService feePayApportionService;

    @Autowired
    private ReferenceUtil referenceUtil;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @Test
    public void SingleFeeMultiplePay_ExactPayment() throws CheckDigitException {

        String ccdCase = "1111222233334444";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(100));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.09.2020"));
        feeCreatedDates.add(parseDate("01.10.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(50));
        paymentAmounts.add(new BigDecimal(20));
        paymentAmounts.add(new BigDecimal(100));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.09.2020"));
        //paymentCreatedDates.add(parseDate("01.10.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 1))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 3))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);

        assertEquals(new BigDecimal(100), feePayApportionCCDCase.getFees().get(0).getAmountDue());
    }

    @Test
    public void SingleFeeMultiplePay_SurplusPayment() throws CheckDigitException {

        String ccdCase = "1111222233335555";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(100));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.09.2020"));
        feeCreatedDates.add(parseDate("01.10.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(80));
        paymentAmounts.add(new BigDecimal(20));
        paymentAmounts.add(new BigDecimal(130));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.09.2020"));
        //paymentCreatedDates.add(parseDate("01.10.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 1))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 3))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);

        assertEquals(new BigDecimal(100), feePayApportionCCDCase.getFees().get(0).getAmountDue());
    }

    @Test
    public void SingleFeeMultiplePay_ShortfallPayment() throws CheckDigitException {

        String ccdCase = "1111222233336666";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(100));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.09.2020"));
        feeCreatedDates.add(parseDate("01.10.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(80));
        paymentAmounts.add(new BigDecimal(10));
        paymentAmounts.add(new BigDecimal(95));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.09.2020"));
        //paymentCreatedDates.add(parseDate("01.10.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 1))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 3))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);

        assertEquals(new BigDecimal(100), feePayApportionCCDCase.getFees().get(0).getAmountDue());
    }

    @Test
    public void MultipleFeeMultiplePay_ExactPayment() throws CheckDigitException {

        String ccdCase = "1111222233337777";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(100));
        feeAmounts.add(new BigDecimal(40));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.08.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(50));
        paymentAmounts.add(new BigDecimal(50));
        paymentAmounts.add(new BigDecimal(140));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.09.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 2))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 3))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);

        assertEquals(new BigDecimal(100), feePayApportionCCDCase.getFees().get(0).getAmountDue());
        assertEquals(new BigDecimal(40), feePayApportionCCDCase.getFees().get(1).getAmountDue());
    }

    @Test
    public void MultipleFeeMultiplePay_SurplusPayment() throws CheckDigitException {

        String ccdCase = "1111222233338888";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(100));
        feeAmounts.add(new BigDecimal(25));
        feeAmounts.add(new BigDecimal(25));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.09.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));
        remissionAmounts.add(new BigDecimal(0));
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(100));
        paymentAmounts.add(new BigDecimal(200));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 3))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 2))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);

        assertEquals(new BigDecimal(100), feePayApportionCCDCase.getFees().get(0).getAmountDue());
        assertEquals(new BigDecimal(25), feePayApportionCCDCase.getFees().get(1).getAmountDue());
        assertEquals(new BigDecimal(25), feePayApportionCCDCase.getFees().get(2).getAmountDue());
    }

    @Test
    public void MultipleFeeMultiplePay_ShortfallPayment() throws CheckDigitException {

        String ccdCase = "1111222233339999";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(100));
        feeAmounts.add(new BigDecimal(50));
        feeAmounts.add(new BigDecimal(10));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.08.2020"));
        feeCreatedDates.add(parseDate("01.09.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));
        remissionAmounts.add(new BigDecimal(0));
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(80));
        paymentAmounts.add(new BigDecimal(50));
        paymentAmounts.add(new BigDecimal(10));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.09.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 3))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 3))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);

        assertEquals(BigDecimal.valueOf(100), feePayApportionCCDCase.getFees().get(0).getAmountDue());
        assertEquals(BigDecimal.valueOf(50), feePayApportionCCDCase.getFees().get(1).getAmountDue());
        assertEquals(BigDecimal.valueOf(10), feePayApportionCCDCase.getFees().get(2).getAmountDue());
    }

    @Test
    public void MultipleFeeMultiplePay_UseCase5() throws CheckDigitException {

        String ccdCase = "1111222244441111";

        when(featureToggler.getBooleanValue("apportion-feature", false)).thenReturn(true);

        List<BigDecimal> feeAmounts = new ArrayList<>();
        feeAmounts.add(new BigDecimal(500));

        List<Timestamp> feeCreatedDates = new ArrayList<>();
        feeCreatedDates.add(parseDate("01.08.2020"));

        List<BigDecimal> remissionAmounts = new ArrayList<>();
        remissionAmounts.add(new BigDecimal(0));

        List<BigDecimal> paymentAmounts = new ArrayList<>();
        paymentAmounts.add(new BigDecimal(200));
        paymentAmounts.add(new BigDecimal(500));

        List<Date> paymentCreatedDates = new ArrayList<>();
        paymentCreatedDates.add(parseDate("01.08.2020"));
        paymentCreatedDates.add(parseDate("01.08.2020"));

        FeePayApportionCCDCase feePayApportionCCDCase = FeePayApportionCCDCase.feePayApportionCCDCaseWith()
            .ccdCaseNo(ccdCase)
            .feePayGroups(getPaymentFeeLinks(1))
            .fees(getFees(ccdCase, feeAmounts, remissionAmounts, feeCreatedDates, 1))
            .payments(getPayments(ccdCase, paymentAmounts, paymentCreatedDates, 2))
            .build();

        feePayApportionService.processFeePayApportion(feePayApportionCCDCase);
        //Based on Amount Due
        assertEquals(new BigDecimal(500), feePayApportionCCDCase.getFees().get(0).getAmountDue());
    }

    private List<PaymentFeeLink> getPaymentFeeLinks(int count) throws CheckDigitException {
        List<PaymentFeeLink> paymentFeeLinks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            paymentFeeLinks.add(PaymentFeeLink.paymentFeeLinkWith()
                .id(RandomUtils.nextInt())
                .paymentReference(referenceUtil.getNext("GR"))
                .build());
        }
        return paymentFeeLinks;
    }

    private List<Payment> getPayments(String ccdCase, List<BigDecimal> amounts, List<Date> paymentCreatedDates, int count) throws CheckDigitException {
        List<Payment> payments = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            payments.add(Payment.paymentWith()
                .id(RandomUtils.nextInt(100, 999))
                .amount(amounts.get(i))
                .reference(referenceUtil.getNext("RC"))
                .paymentStatus(PaymentStatus.paymentStatusWith().name("success").build())
                .dateCreated(paymentCreatedDates.get(i))
                .paymentChannel(PaymentChannel.paymentChannelWith().name("online").build())
                .paymentMethod(PaymentMethod.paymentMethodWith().name("card").build())
                .paymentProvider(PaymentProvider.paymentProviderWith().name("gov pay").build())
                .serviceType("PROBATE")
                .ccdCaseNumber(ccdCase)
                .paymentLink(PaymentFeeLink.paymentFeeLinkWith().ccdCaseNumber(ccdCase).build())
                .build());
        }
        return payments;
    }

    private List<PaymentFee> getFees(String ccdCase, List<BigDecimal> amounts, List<BigDecimal> remissionAmounts, List<Timestamp> feeCreatedDates, int count) throws CheckDigitException {
        List<PaymentFee> fees = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            PaymentFee fee = PaymentFee.feeWith()
                .id(RandomUtils.nextInt(100, 999))
                .code("FEE00" + i)
                .feeAmount(amounts.get(i))
                .volume(1)
                .calculatedAmount(amounts.get(i).multiply(new BigDecimal(1)))
                .netAmount(amounts.get(i).multiply(new BigDecimal(1)).subtract(remissionAmounts.get(i)))
                .amountDue(amounts.get(i).multiply(new BigDecimal(1)).subtract(remissionAmounts.get(i)))
                .dateCreated(feeCreatedDates.get(i))
                .build();

            fees.add(fee);
        }
        return fees;
    }

    private Timestamp parseDate(String date) {
        try {
            return new Timestamp(new SimpleDateFormat("dd.MM.yyyy").parse(date).getTime());
        } catch (ParseException e) {
            return null;
        }
    }
}
