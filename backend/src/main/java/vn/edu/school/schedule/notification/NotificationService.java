package vn.edu.school.schedule.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.school.schedule.notification.api.MarkAllResult;
import vn.edu.school.schedule.notification.api.NotificationResponse;
import vn.edu.school.schedule.notification.api.UnreadCount;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc; public NotificationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<NotificationResponse> list(AuthenticatedUser actor,Boolean read){String condition=read==null?"":read?" AND r.read_at IS NOT NULL":" AND r.read_at IS NULL";return jdbc.query("""
            SELECT n.id,n.type,n.title,n.body,n.entity_id,n.created_at,r.read_at FROM notification_recipients r
            JOIN notifications n ON n.id=r.notification_id WHERE r.user_id=? %s ORDER BY n.created_at DESC,n.id DESC LIMIT 100
            """.formatted(condition),(rs,row)->new NotificationResponse(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant()),actor.id());}
    public UnreadCount unread(AuthenticatedUser actor){Long n=jdbc.queryForObject("SELECT count(*) FROM notification_recipients WHERE user_id=? AND read_at IS NULL",Long.class,actor.id());return new UnreadCount(n==null?0:n);}
    @Transactional public NotificationResponse markRead(UUID id,AuthenticatedUser actor){int changed=jdbc.update("UPDATE notification_recipients SET read_at=COALESCE(read_at,now()) WHERE notification_id=? AND user_id=?",id,actor.id());if(changed!=1)throw new ApiException(HttpStatus.NOT_FOUND,"NOTIFICATION_NOT_FOUND","Không tìm thấy thông báo.");return list(actor,null).stream().filter(item->item.id().equals(id)).findFirst().orElseThrow();}
    @Transactional public MarkAllResult markAll(AuthenticatedUser actor){return new MarkAllResult(jdbc.update("UPDATE notification_recipients SET read_at=now() WHERE user_id=? AND read_at IS NULL",actor.id()));}
}
