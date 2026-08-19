package vn.edu.school.schedule.weeklyplan.api;

import java.util.List;
import java.util.UUID;

public record PlanSectionResponse(UUID id, String sectionType, String title, String content,
                                  short displayOrder, List<PlanTargetResponse> targets) { }
