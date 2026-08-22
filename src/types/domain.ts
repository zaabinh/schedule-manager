export type SystemRole = "ADMIN" | "USER";
export type AccountStatus = "PENDING" | "ACTIVE" | "INACTIVE";
export type PlanStatus = "DRAFT" | "PUBLISHED";
export type Session = "MORNING" | "AFTERNOON";
export type TaskStatus = "TODO" | "COMPLETED";
export type DisplayTaskStatus = TaskStatus | "OVERDUE";
export type TargetType = "ALL" | "ROLE" | "DEPARTMENT" | "USER";
export type SectionType = "ACADEMIC_AFFAIRS" | "FACILITIES_OFFICE" | "YOUTH_UNION" | "HOMEROOM_TEACHERS" | "TEACHERS";
export type WeekType = "ORIENTATION" | "STUDY";

export interface User {
  id: string; name: string; email: string; systemRole: SystemRole; status: AccountStatus;
  department: string | null; businessRoles: string[]; homeroomClass: string | null; registeredAt?: string; version?: number;
}
export interface PlanTarget { type: TargetType; id?: string; label: string }
export interface PlanSection { id: string; type: SectionType; title: string; content: string; targets: PlanTarget[]; relevant?: boolean }
export interface EventItem {
  id: string; content: string; startDate?: string; endDate?: string; session?: Session;
  startTime?: string; endTime?: string; location?: string; note?: string; version?: number;
}
export interface DaySession { session: Session; baseContent: string; events: EventItem[] }
export interface PlanDay { date: string; dayLabel: string; sessions: DaySession[] }
export interface WeeklyPlan {
  id: string; weekId: string; sequenceNumber: number; displayLabel: string; startDate: string; endDate: string;
  status: PlanStatus; version: number; morningDutyClassId?: string; morningDutyClass?: string;
  publishedAt?: string; publishedBy?: string;
  afternoonDutyClassId?: string; afternoonDutyClass?: string;
  sections: PlanSection[]; days: PlanDay[];
}
export interface Task { id: string; title: string; description?: string; dueAt: string; status: TaskStatus; displayStatus?: DisplayTaskStatus; weeklyPlanId: string; assigneeId?: string; assigneeName?: string; completedAt?: string; version?: number; attachmentCount?: number }
export interface TaskAttachment { id: string; taskId: string; originalName: string; contentType: string; fileSize: number; checksum?: string; createdAt: string }
export interface Notification { id: string; title: string; description: string; createdAt: string; readAt?: string; type: string }
export interface Reminder { id: string; eventId: string; eventTitle: string; remindAt: string; source: "ADMIN" | "USER"; status: "PENDING" | "PROCESSING" | "SENT" | "FAILED" | "CANCELLED" }
export interface Message { id: string; senderId: string; senderName: string; content: string; createdAt: string }
export interface Conversation { id: string; subject: string; category?: string; status: "OPEN" | "CLOSED"; createdBy: string; updatedAt: string; version?: number; messages: Message[] }
export interface Department { id: string; name: string; description?: string; isActive: boolean; version: number }
export interface BusinessRole { id: string; name: string; description?: string; isActive: boolean; isProtected: boolean; version: number }
export interface SchoolClass {
  id: string; academicYearId: string; academicYearName: string; name: string; grade: 10 | 11 | 12;
  homeroomTeacherId: string | null; homeroomTeacher: string | null; isActive: boolean; version: number;
}
export interface AcademicYear { id: string; name: string; startDate: string; isActive: boolean; version: number; weekCount: number }
export interface AcademicWeek {
  id: string; label: string; academicYearId?: string; sequenceNumber?: number; displayNumber?: number;
  weekType?: WeekType; startDate: string; endDate: string; version?: number; warnings?: string[]; planStatus?: PlanStatus;
}
export interface AuditEntry { id: string; createdAt: string; actor: string; entityType: string; action: string; entityLabel: string; oldValue?: string; newValue?: string }
