package uk.gov.hmcts.payment.api.util;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.DateTimeParser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class DateUtil {

    private static final DateTimeParser[] ISO_DATE_TIME_PARSERS = {
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("dd-MM-yyyy'T'HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("yyyy-MM-dd").getParser(),
        DateTimeFormat.forPattern("dd-MM-yyyy").getParser(),
    };

    public DateTimeFormatter getIsoDateTimeFormatter() {
        return new DateTimeFormatterBuilder().append(null, ISO_DATE_TIME_PARSERS).toFormatter();
    }

    public static String getDateForReportName(Date date){
        java.time.format.DateTimeFormatter reportNameDateFormat = java.time.format.DateTimeFormatter.ofPattern("ddMMyy");
        return date == null ? null : dateToLocalDateTime(date).format(reportNameDateFormat);
    }

    public static String getDateTimeForReportName(Date date){
        java.time.format.DateTimeFormatter reportNameDateFormat = java.time.format.DateTimeFormatter.ofPattern("ddMMyy_HHmmss");
        return date == null ? null : dateToLocalDateTime(date).format(reportNameDateFormat);
    }

    public static LocalDateTime dateToLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static Date atStartOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime startOfDay = localDateTime == null ? null : localDateTime.with(LocalTime.MIN);
        return localDateTimeToDate(startOfDay);
    }

    public static Date atEndOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime endOfDay = localDateTime == null ? null : localDateTime.with(LocalTime.MAX);
        return localDateTimeToDate(endOfDay);
    }

    public static Date localDateTimeToDate(LocalDateTime ldt) {
        return ldt == null ? null : Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date convertToDateViaInstant(LocalDateTime dateToConvert) {
        return java.util.Date
            .from(dateToConvert.atZone(ZoneId.systemDefault())
                .toInstant());
    }
}
