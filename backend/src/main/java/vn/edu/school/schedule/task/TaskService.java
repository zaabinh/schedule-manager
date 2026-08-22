package vn.edu.school.schedule.task;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.auth.api.ResourceRef;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.task.api.TaskResponse;
import vn.edu.school.schedule.task.api.TaskSummary;
import vn.edu.school.schedule.task.api.TaskWriteRequest;
import vn.edu.school.schedule.task.api.TaskOptions;

@Service
public class TaskService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final Clock clock;
    public TaskService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) { this.jdbc=jdbc; this.json=json; this.clock=clock; }

    public List<TaskResponse> listAll() { return jdbc.query(taskSql(""), (rs,row)->map(rs)); }
    public List<TaskResponse> listMine(AuthenticatedUser actor) { return jdbc.query(taskSql("WHERE t.assignee_user_id=?"),(rs,row)->map(rs),actor.id()); }
    public TaskOptions options(){return new TaskOptions(
            jdbc.query("SELECT p.id,'Tuần '||w.display_number FROM weekly_plans p JOIN school_weeks w ON w.id=p.school_week_id ORDER BY w.start_date DESC",(rs,row)->new ResourceRef(rs.getObject(1,UUID.class),rs.getString(2))),
            jdbc.query("SELECT id,display_name FROM users WHERE status='ACTIVE' AND system_role='USER' ORDER BY display_name",(rs,row)->new ResourceRef(rs.getObject(1,UUID.class),rs.getString(2))));}
    public TaskSummary summary() {
        Map<String,Object> values=jdbc.queryForMap("""
                SELECT count(*) total,count(*) FILTER (WHERE status='COMPLETED') completed,
                count(*) FILTER (WHERE status='TODO') incomplete,
                count(*) FILTER (WHERE status='TODO' AND due_at<?) overdue FROM tasks
                """, java.sql.Timestamp.from(clock.instant()));
        return new TaskSummary(number(values,"total"),number(values,"completed"),number(values,"incomplete"),number(values,"overdue"));
    }
    @Transactional public TaskResponse create(TaskWriteRequest request, AuthenticatedUser actor) {
        requirePlan(request.weeklyPlanId()); requireActiveUser(request.assigneeUserId());
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO tasks(id,weekly_plan_id,assignee_user_id,title,description,due_at,created_by) VALUES (?,?,?,?,?,?,?)",
                id,request.weeklyPlanId(),request.assigneeUserId(),request.title().trim(),clean(request.description()),java.sql.Timestamp.from(request.dueAt()),actor.id());
        TaskResponse result=task(id); audit(actor.id(),id,"TASK_CREATED",null,result); enqueue(result,"TASK_ASSIGNED"); return result;
    }
    @Transactional public TaskResponse update(UUID id, TaskWriteRequest request, AuthenticatedUser actor) {
        TaskResponse before=task(id); requirePlan(request.weeklyPlanId()); requireActiveUser(request.assigneeUserId());
        if(request.version()==null) throw validation("TASK_VERSION_REQUIRED","Task version is required.");
        int changed=jdbc.update("""
                UPDATE tasks SET weekly_plan_id=?,assignee_user_id=?,title=?,description=?,due_at=?,version=version+1,updated_at=now()
                WHERE id=? AND version=?
                """,request.weeklyPlanId(),request.assigneeUserId(),request.title().trim(),clean(request.description()),java.sql.Timestamp.from(request.dueAt()),id,request.version());
        if(changed!=1) throw conflict("VERSION_CONFLICT","Task was changed by another request.");
        TaskResponse result=task(id); audit(actor.id(),id,"TASK_UPDATED",before,result); enqueue(result,"TASK_UPDATED"); return result;
    }
    @Transactional public TaskResponse complete(UUID id,long version,AuthenticatedUser actor) {
        TaskResponse before=taskForOwner(id,actor.id()); if("COMPLETED".equals(before.status())) return before;
        int changed=jdbc.update("UPDATE tasks SET status='COMPLETED',completed_at=?,version=version+1,updated_at=now() WHERE id=? AND assignee_user_id=? AND version=? AND status='TODO'",
                java.sql.Timestamp.from(clock.instant()),id,actor.id(),version);
        if(changed!=1) throw conflict("VERSION_CONFLICT","Task was changed by another request.");
        TaskResponse result=task(id); audit(actor.id(),id,"TASK_COMPLETED",before,result); return result;
    }
    private String taskSql(String where){return """
            SELECT t.id,t.weekly_plan_id,u.id,u.display_name,t.title,t.description,t.due_at,t.status,t.completed_at,t.version,
                   (SELECT count(*) FROM task_attachments a WHERE a.task_id=t.id AND a.deleted_at IS NULL) attachment_count
            FROM tasks t JOIN users u ON u.id=t.assignee_user_id %s ORDER BY t.due_at,t.id
            """.formatted(where);}
    private TaskResponse task(UUID id){List<TaskResponse> rows=jdbc.query(taskSql("WHERE t.id=?"),(rs,row)->map(rs),id);if(rows.isEmpty())throw notFound();return rows.getFirst();}
    private TaskResponse taskForOwner(UUID id,UUID owner){List<TaskResponse> rows=jdbc.query(taskSql("WHERE t.id=? AND t.assignee_user_id=?"),(rs,row)->map(rs),id,owner);if(rows.isEmpty())throw notFound();return rows.getFirst();}
    private TaskResponse map(java.sql.ResultSet rs)throws java.sql.SQLException{Instant due=rs.getTimestamp(7).toInstant();String status=rs.getString(8);String display="TODO".equals(status)&&due.isBefore(clock.instant())?"OVERDUE":status;return new TaskResponse(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),new ResourceRef(rs.getObject(3,UUID.class),rs.getString(4)),rs.getString(5),rs.getString(6),due,status,display,rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),rs.getLong(10),rs.getLong(11));}
    private void requirePlan(UUID id){Long n=jdbc.queryForObject("SELECT count(*) FROM weekly_plans WHERE id=?",Long.class,id);if(n==null||n!=1)throw notFound();}
    private void requireActiveUser(UUID id){Long n=jdbc.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE' AND system_role='USER'",Long.class,id);if(n==null||n!=1)throw validation("ASSIGNEE_INVALID","Assignee must be an active User.");}
    private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
    private long number(Map<String,Object> values,String key){return ((Number)values.get(key)).longValue();}
    private void enqueue(TaskResponse task,String type){Map<String,Object> p=Map.of("taskId",task.id(),"assigneeUserId",task.assignee().id(),"version",task.version());jdbc.update("INSERT INTO outbox_messages(id,event_type,aggregate_type,aggregate_id,deduplication_key,payload) VALUES (?,?,'Task',?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING",UUID.randomUUID(),type,task.id(),type+":"+task.id()+":"+task.version(),string(p));}
    private void audit(UUID actor,UUID id,String action,Object before,Object after){UUID correlation;try{correlation=UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));}catch(Exception e){correlation=UUID.randomUUID();}jdbc.update("INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,old_value,new_value,correlation_id) VALUES (?,?,'USER','Task',?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)",UUID.randomUUID(),actor,id,action,before==null?null:string(before),after==null?null:string(after),correlation);}
    private String string(Object v){try{return json.writeValueAsString(v);}catch(JacksonException e){throw new IllegalStateException(e);}}
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"TASK_NOT_FOUND","Không tìm thấy nhiệm vụ.");}
    private ApiException validation(String c,String m){return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,c,m);}
    private ApiException conflict(String c,String m){return new ApiException(HttpStatus.CONFLICT,c,m);}
}
