package uk.gov.hmcts.payment.api.scheduler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.payment.api.dto.PaymentSearchCriteria;
import uk.gov.hmcts.payment.api.dto.mapper.PaymentDtoMapper;
import uk.gov.hmcts.payment.api.email.Email;
import uk.gov.hmcts.payment.api.email.EmailService;
import uk.gov.hmcts.payment.api.model.PaymentFeeLink;
import uk.gov.hmcts.payment.api.reports.FeesService;
import uk.gov.hmcts.payment.api.reports.PaymentsReportService;
import uk.gov.hmcts.payment.api.reports.config.CardPaymentReportConfig;
import uk.gov.hmcts.payment.api.reports.config.PaymentReportConfig;
import uk.gov.hmcts.payment.api.service.DelegatingPaymentService;
import uk.gov.hmcts.payment.api.util.PaymentMethodType;

import java.util.Date;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.verify;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class PaymentsReportServiceTest {

    @InjectMocks
    private PaymentsReportService paymentsReportService;

    @Mock
    private DelegatingPaymentService<PaymentFeeLink, String> delegatingPaymentService;
    @Mock
    private EmailService emailService;
    @Mock
    private FeesService feesService;
    @Mock
    private PaymentDtoMapper paymentDtoMapper;

    private PaymentReportConfig paymentReportConfig;

    @Before
    public void setUp() {
        paymentReportConfig = new CardPaymentReportConfig("fromEmail", new String []{"toEmail"}, "emailSubject", "emailMessage", true);
    }

    @Test
    public void shouldDelegateToCardSearch()  {
        // given
        Date startDate = new Date();
        Date endDate = new Date();

        PaymentSearchCriteria criteria = PaymentSearchCriteria
            .searchCriteriaWith()
            .startDate(startDate)
            .endDate(endDate)
            .paymentMethod(PaymentMethodType.CARD.getType())
            .build();

        // when
        paymentsReportService.generateCsvAndSendEmail(startDate, endDate, PaymentMethodType.CARD, null, paymentReportConfig);

        // then
        verify(delegatingPaymentService).search(criteria);
    }

    @Test
    public void shouldDelegateToPbaSearch()  {
        // given
        Date startDate = new Date();
        Date endDate = new Date();

        PaymentSearchCriteria criteria = PaymentSearchCriteria
            .searchCriteriaWith()
            .startDate(startDate)
            .endDate(endDate)
            .paymentMethod(PaymentMethodType.PBA.getType())
            .serviceType("Divorce")
            .build();

        // when
        paymentsReportService.generateCsvAndSendEmail(startDate, endDate, PaymentMethodType.PBA, "Divorce", paymentReportConfig);

        // then
        verify(delegatingPaymentService).search(criteria);
    }

    @Test
    public void shouldDelegateToEmailService()  {
        // given
        paymentReportConfig = new CardPaymentReportConfig("fromEmail", new String []{"toEmail"}, "emailSubject", "emailMessage", true);

         // when
        paymentsReportService.generateCsvAndSendEmail(new Date(), new Date(), PaymentMethodType.CARD, null, paymentReportConfig);

        // then
        ArgumentCaptor<Email> argument = ArgumentCaptor.forClass(Email.class);
        verify(emailService).sendEmail(argument.capture());
        assertThat(argument.getValue().getFrom()).isEqualTo("fromEmail");
        assertThat(argument.getValue().getTo()).containsExactly("toEmail");
        assertThat(argument.getValue().getSubject()).isEqualTo("emailSubject");
        assertThat(argument.getValue().getMessage()).isEqualTo("emailMessage");
        assertThat(argument.getValue().hasAttachments()).isTrue();
        assertThat(argument.getValue().getAttachments().get(0).getFilename()).startsWith(paymentReportConfig.getCsvFileNamePrefix());
    }

}
