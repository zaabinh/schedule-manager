package vn.edu.school.schedule.task.api;
import java.util.List;
import vn.edu.school.schedule.auth.api.ResourceRef;
public record TaskOptions(List<ResourceRef> plans,List<ResourceRef> users) { }
