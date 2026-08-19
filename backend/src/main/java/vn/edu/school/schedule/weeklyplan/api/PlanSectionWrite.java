package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlanSectionWrite(
        @NotBlank @Pattern(regexp = "ACADEMIC_AFFAIRS|FACILITIES_OFFICE|YOUTH_UNION|HOMEROOM_TEACHERS|TEACHERS") String sectionType,
        @Size(max = 20000) String content,
        @Min(1) @Max(5) short displayOrder,
        @NotNull List<@Valid PlanTargetWrite> targets) { }
