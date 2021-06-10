package uk.gov.hmcts.payment.api.util;



import org.joda.time.format.DateTimeFormatter;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateUtilTest {

    GregorianCalendar calendar = new GregorianCalendar(2019, Calendar.FEBRUARY, 20, 18, 9, 22);
    Date SampleDate = calendar.getTime();
    LocalDateTime SampleDateTime = LocalDateTime.of(2019, Month.JULY, 20, 18, 9, 22);

    @Test
    public void getIsoDateTimeFormatterTest(){
        DateUtil dateUtil = new DateUtil();
        String ActualDate = "2011-10-24";
        DateTimeFormatter date = dateUtil.getIsoDateTimeFormatter();
        assertEquals(ActualDate, date.parseLocalDate(ActualDate).toString());

    }

    @Test
    public void getDateForReportNameTest() {
        String Result = DateUtil.getDateForReportName(SampleDate);
        assertEquals("200219", Result);
    }

    @Test
    public void getDateTimeForReportNameTest() {
        String Result = DateUtil.getDateTimeForReportName(SampleDate);
        assertEquals("200219_180922", Result);
    }

    @Test
    public void dateToLocalDateTimeTest() {
        LocalDateTime Result = DateUtil.dateToLocalDateTime(SampleDate);
        assertEquals("2019-02-20T18:09:22", Result.toString());
    }

    @Test
    public void atStartOfDayTest() {
        Date Result = DateUtil.atStartOfDay(SampleDate);
        assertEquals("Wed Feb 20 00:00:00 UTC 2019", Result.toString());
    }

    @Test
    public void atEndOfDayTest() {
        Date Result = DateUtil.atEndOfDay(SampleDate);
        assertEquals("Wed Feb 20 23:59:59 UTC 2019", Result.toString());
    }

    @Test
    public void localDateTimeToDateTest() {
        Date Result = DateUtil.localDateTimeToDate(SampleDateTime);
        assertEquals("Sat Jul 20 18:09:22 UTC 2019", Result.toString());
    }

    @Test
    public void convertToDateViaInstantTest() {
        Date Result = DateUtil.convertToDateViaInstant(SampleDateTime);
        assertEquals("Sat Jul 20 18:09:22 UTC 2019", Result.toString());
    }
}
