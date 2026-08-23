package vn.edu.school.schedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

class SmtpEmailSenderTest {
    @Test
    void sendsUtf8MultipartAlternativeWithHtmlAndPlainText() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@example.edu.vn");
        EmailMessage message = new EmailMessage("Kế hoạch tuần", "Nội dung dạng chữ", "<html><body><strong>Nội dung HTML</strong></body></html>");

        sender.send("teacher@example.edu.vn", message);

        ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(mailSender).send(captor.capture());
        captor.getValue().prepare(mimeMessage);
        mimeMessage.saveChanges();

        assertThat(mimeMessage.getSubject()).isEqualTo("Kế hoạch tuần");
        assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("teacher@example.edu.vn");
        assertThat(content(mimeMessage, "text/plain")).contains("Nội dung dạng chữ");
        assertThat(content(mimeMessage, "text/html")).contains("<strong>Nội dung HTML</strong>");
    }

    private String content(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) return String.valueOf(part.getContent());
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                String found = content(child, mimeType);
                if (found != null) return found;
            }
        }
        return null;
    }
}
