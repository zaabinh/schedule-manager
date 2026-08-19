package vn.edu.school.schedule.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        int separator = recipient.indexOf('@');
        String domain = separator >= 0 ? recipient.substring(separator + 1) : "invalid";
        log.info(
                "Email delivery simulated recipientDomain={} subjectLength={} bodyLength={}",
                domain,
                subject.length(),
                body.length());
    }
}
