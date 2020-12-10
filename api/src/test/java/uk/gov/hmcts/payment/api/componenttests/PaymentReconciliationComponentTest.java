package uk.gov.hmcts.payment.api.componenttests;

import org.joda.time.MutableDateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.hmcts.payment.api.componenttests.util.PaymentsDataUtil;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.v1.componenttests.TestUtil;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.gov.hmcts.payment.api.model.PaymentFeeLink.paymentFeeLinkWith;

public class PaymentReconciliationComponentTest extends TestUtil {
    private PaymentsDataUtil paymentsDataUtil;

    @Before
    public void setUp() {
        paymentsDataUtil = new PaymentsDataUtil();
    }

    @Test
    public void testFindPaymetsBetweenGivenValidDates() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String paymentRef1 = UUID.randomUUID().toString();
        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference(paymentRef1)
            .payments(paymentsDataUtil.getCardPaymentsData())
            .fees(paymentsDataUtil.getFeesData())
            .build());

        String paymentRef2 = UUID.randomUUID().toString();
        paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference(paymentRef2)
            .payments(Arrays.asList(paymentsDataUtil.getCardPaymentsData().get(1)))
            .fees(Arrays.asList(paymentsDataUtil.getFeesData().get(0)))
            .build());


        Date fromDate = new Date();
        MutableDateTime mFromDate = new MutableDateTime(fromDate);
        mFromDate.addDays(-1);
        Date toDate = new Date();
        MutableDateTime mToDate = new MutableDateTime(toDate);

        List<PaymentFeeLink> paymentFeeLinks = paymentFeeLinkRepository.findAll(findByDatesBetween(mFromDate.toDate(), mToDate.toDate()));

        assertNotNull(paymentFeeLink);
        assertEquals(paymentFeeLinks.size(), 2);
    }

    @Test
    public void testFindPaymetsBetweenGivenInValidDates() throws Exception {

        String paymentRef3 = UUID.randomUUID().toString();
        PaymentFeeLink paymentFeeLink = paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference(paymentRef3)
            .payments(Arrays.asList(paymentsDataUtil.getCardPaymentsData().get(1)))
            .fees(paymentsDataUtil.getFeesData())
            .build());

        String paymentRef4 = UUID.randomUUID().toString();
        paymentFeeLinkRepository.save(paymentFeeLinkWith().paymentReference(paymentRef4)
            .payments(Arrays.asList(paymentsDataUtil.getCardPaymentsData().get(1)))
            .fees(Arrays.asList(paymentsDataUtil.getFeesData().get(0)))
            .build());


        Date fromDate = new Date();
        MutableDateTime mFromDate = new MutableDateTime(fromDate);
        mFromDate.addDays(-3);
        Date toDate = new Date();
        MutableDateTime mToDate = new MutableDateTime(toDate);
        mToDate.addDays(-2);

        List<PaymentFeeLink> paymentFeeLinks = paymentFeeLinkRepository.findAll(findByDatesBetween(mFromDate.toDate(), mToDate.toDate()));

        assertNotNull(paymentFeeLink);
        assertEquals(paymentFeeLinks.size(), 0);
    }


    private static Specification findByDatesBetween(Date fromDate, Date toDate) {
        return Specification
            .where(isBetween(fromDate, toDate));
    }

    private static Specification isBetween(Date startDate, Date endDate) {

        return ((root, query, cb) -> cb.between(root.get("dateCreated"), startDate, endDate));
    }

}
