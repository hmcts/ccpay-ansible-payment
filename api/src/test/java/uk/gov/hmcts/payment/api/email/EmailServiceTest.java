package uk.gov.hmcts.payment.api.email;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ContextConfiguration;

@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public class EmailServiceTest {

    private static final String EMAIL_FROM = "no-reply@testing.com";
    private static final String[] EMAIL_TO = "tester@testing.com".split(",");
    private static final String EMAIL_SUBJECT = "Test Subject";
    private static final String EMAIL_MESSAGE = "Test Message";

    @InjectMocks
    private EmailService emailService;

    @Mock
    private JavaMailSenderImpl javaMailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Before
    public void beforeEachTest() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    public void testSendEmailSuccess() throws MessagingException {
        Email emailData = SampleEmailData.getDefault();
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        emailService.sendEmail(emailData);
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    public void testSendEmailWithNoAttachments() throws MessagingException {
        Email testEmail = Email.emailWith()
            .from(EMAIL_FROM)
            .to(EMAIL_TO)
            .subject(EMAIL_SUBJECT)
            .message(EMAIL_MESSAGE)
            .build();
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        emailService.sendEmail(testEmail);
        verify(javaMailSender).send(mimeMessage);
    }

    @Test(expected = RuntimeException.class)
    public void testSendEmailThrowsMailException() throws MessagingException {
        Email emailData = SampleEmailData.getDefault();
        doThrow(mock(MailException.class)).when(javaMailSender).send(any(MimeMessage.class));
        emailService.sendEmail(emailData);
    }

    @Test(expected = EmailFailedException.class)
    public void testSendEmailThrowsInvalidArgumentExceptionForInvalidTo() {
        Email emailData = Email.emailWith()
            .from(EMAIL_FROM)
            .to(null)
            .subject(EMAIL_SUBJECT)
            .message(EMAIL_MESSAGE)
            .build();
        emailService.sendEmail(emailData);
    }

    @Test(expected = EmailFailedException.class)
    public void testSendEmailThrowsInvalidArgumentExceptionForInvalidSubject() {
        Email emailData = Email.emailWith()
            .from(EMAIL_FROM)
            .to(EMAIL_TO)
            .subject(null)
            .message(EMAIL_MESSAGE)
            .build();
        emailService.sendEmail(emailData);
    }

    public static class SampleEmailData {

        static Email getDefault() {
            List<EmailAttachment> emailAttachmentList = new ArrayList<>();
            EmailAttachment emailAttachment =
                EmailAttachment.csv("hello".getBytes(), "Hello.csv");
            emailAttachmentList.add(emailAttachment);
            Email testEmail = Email.emailWith()
                .from(EMAIL_FROM)
                .to(EMAIL_TO)
                .subject(EMAIL_SUBJECT)
                .message(EMAIL_MESSAGE)
                .build();
            testEmail.setAttachments(emailAttachmentList);
            return testEmail;
        }
    }
}


