package vn.edu.school.schedule.reminder;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.school.schedule.notification.EmailSender;

@Component
public class ReminderWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmailSender email;
    private final MeterRegistry metrics;
    private final int maxAttempts;

    public ReminderWorker(JdbcTemplate jdbc, TransactionTemplate transactions, EmailSender email,
                          MeterRegistry metrics, @Value("${app.reminder.max-attempts:3}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.email = email;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.reminder.poll-delay-ms:60000}",
            initialDelayString = "${app.reminder.initial-delay-ms:60000}")
    public void poll() {
        jdbc.update("""
                UPDATE reminders SET status='PENDING',processing_lease_until=NULL,updated_at=now()
                WHERE status='PROCESSING' AND processing_lease_until<now()
                """);
        drain();
    }

    public int drain() {
        int processed = 0;
        for (int index = 0; index < 100; index++) {
            Claim claim = claim();
            if (claim == null) break;
            deliver(claim);
            processed++;
        }
        return processed;
    }

    private Claim claim() {
        return transactions.execute(status -> {
            List<Claim> rows = jdbc.query("""
                    SELECT r.id,u.email,e.content,r.attempt_count FROM reminders r
                    JOIN users u ON u.id=r.owner_user_id JOIN events e ON e.id=r.event_id
                    WHERE r.status='PENDING' AND COALESCE(r.next_attempt_at,r.remind_at)<=now()
                      AND u.status='ACTIVE'
                    ORDER BY COALESCE(r.next_attempt_at,r.remind_at),r.id
                    FOR UPDATE OF r SKIP LOCKED LIMIT 1
                    """, (rs, row) -> new Claim(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4)));
            if (rows.isEmpty()) return null;
            Claim value = rows.getFirst();
            jdbc.update("UPDATE reminders SET status='PROCESSING',processing_lease_until=now()+interval '2 minutes',updated_at=now() WHERE id=?", value.id());
            return value;
        });
    }

    private void deliver(Claim claim) {
        try {
            email.send(claim.email(), "Nhắc lịch: " + claim.eventTitle(), "Sự kiện sắp diễn ra: " + claim.eventTitle());
            jdbc.update("""
                    UPDATE reminders SET status='SENT',sent_at=now(),attempt_count=attempt_count+1,
                    processing_lease_until=NULL,last_error_code=NULL,updated_at=now() WHERE id=?
                    """, claim.id());
            metrics.counter("schedule.reminder.sent").increment();
        } catch (Exception failure) {
            int attempt = claim.attempts() + 1;
            String status = attempt >= maxAttempts ? "FAILED" : "PENDING";
            jdbc.update("""
                    UPDATE reminders SET status=?,attempt_count=?,next_attempt_at=now()+interval '1 minute',
                    processing_lease_until=NULL,last_error_code=?,updated_at=now() WHERE id=?
                    """, status, attempt, failure.getClass().getSimpleName(), claim.id());
            metrics.counter("schedule.reminder.failed").increment();
        }
    }

    private record Claim(UUID id, String email, String eventTitle, int attempts) { }
}
