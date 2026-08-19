package vn.edu.school.schedule.dashboard.api;

import java.util.List;
import java.util.UUID;

public record RelevantItem(String kind, UUID entityId, String title, String content,
                           List<String> matchedBy, String deepLink) { }
