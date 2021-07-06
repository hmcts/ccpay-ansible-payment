package uk.gov.hmcts.payment.api.componenttests;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.payment.api.controllers.PaymentReportController;
import uk.gov.hmcts.payment.api.controllers.RestErrorHandler;
import uk.gov.hmcts.payment.api.exception.ValidationErrorException;
import uk.gov.hmcts.payment.api.reports.PaymentsReportFacade;
import uk.gov.hmcts.payment.api.scheduler.Clock;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;
import uk.gov.hmcts.payment.api.validators.PaymentValidator;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class PaymentReportControllerMockTest {

    private static final Date FROM_DATE = new Date();
    private static final Date TO_DATE = new Date();
    private static final Date WEEK_AGO_DATE = new DateTime().minusWeeks(1).toDate();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE;

    private MockMvc mockMvc;

    @Mock
    private PaymentsReportFacade paymentsReportFacade;

    @Mock
    private PaymentValidator validator;

    @Mock
    private Clock clock;

    @InjectMocks
    private PaymentReportController controller;


    @Before
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestErrorHandler()).build();
    }

    @Test
    public void paymentReportWithNoInputDates() throws Exception {
        // given
        given(clock.getYesterdayDate()).willReturn(FROM_DATE);
        given(clock.getTodayDate()).willReturn(TO_DATE);
        // when & then
        this.mockMvc.perform(post("/jobs/email-pay-reports")
            .param("payment_method", "CARD"))
            .andExpect(status().isOk());

        verify(paymentsReportFacade).generateCsvAndSendEmail(FROM_DATE, TO_DATE, PaymentMethodType.CARD, null);
    }

    @Test
    public void paymentReportWithNoInputDatesOnlyServiceName() throws Exception {
        // given
        given(clock.getYesterdayDate()).willReturn(FROM_DATE);
        given(clock.getTodayDate()).willReturn(TO_DATE);
        // when & then
        this.mockMvc.perform(post("/jobs/email-pay-reports")
            .param("service_name", "Digital Bar"))
            .andExpect(status().isOk());

        verify(paymentsReportFacade).generateCsvAndSendEmail(FROM_DATE, TO_DATE, null, "Digital Bar");
    }

    @Test
    public void paymentReportWithNoInputDatesOnlyServiceNameAndStartDate() throws Exception {
        // given
        given(clock.atStartOfDay(WEEK_AGO_DATE.toString(), FORMATTER)).willReturn(WEEK_AGO_DATE);
        given(clock.getTodayDate()).willReturn(TO_DATE);
        // when & then
        this.mockMvc.perform(post("/jobs/email-pay-reports")
            .param("service_name", "Digital Bar").param("start_date", WEEK_AGO_DATE.toString()))
            .andExpect(status().isOk());

        verify(paymentsReportFacade).generateCsvAndSendEmail(WEEK_AGO_DATE, TO_DATE, null, "Digital Bar");
    }

    @Test
    public void paymentReportWithInputDates() throws Exception {
        // given
        given(clock.atStartOfDay("2018-06-30", DateTimeFormatter.ISO_DATE)).willReturn(FROM_DATE);
        given(clock.atEndOfDay("2018-07-01", DateTimeFormatter.ISO_DATE)).willReturn(TO_DATE);

        // when & then
        this.mockMvc.perform(post("/jobs/email-pay-reports")
            .param("payment_method", "CARD")
            .param("start_date", "2018-06-30")
            .param("end_date", "2018-07-01")
            .param("service_name", "Divorce"))
            .andExpect(status().isOk());

        verify(paymentsReportFacade).generateCsvAndSendEmail(FROM_DATE, TO_DATE, PaymentMethodType.CARD, "Divorce");
    }

    @Test
    public void paymentReport_shouldThrowValidationError() throws Exception {
        // given

        doThrow(new ValidationErrorException("validation failed", null))
            .when(validator).validate(Optional.of("CARD"), Optional.of("2018-06-30"), Optional.of("2018-07-01"));
        // when & then
        this.mockMvc.perform(post("/jobs/email-pay-reports")
            .param("payment_method", "CARD")
            .param("start_date", "2018-06-30")
            .param("end_date", "2018-07-01")
            .param("service_name", "UNKNOWN"))
            .andExpect(status().isBadRequest());

        verifyZeroInteractions(paymentsReportFacade);
    }
}
