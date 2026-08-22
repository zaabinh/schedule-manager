import type { AcademicWeek, AcademicYear, AuditEntry, BusinessRole, Conversation, Department, EventItem, Notification, PlanStatus, Reminder, SchoolClass, SectionType, Session, TargetType, Task, User, WeekType, WeeklyPlan } from "@/types/domain";
import { apiDownload, apiRequest, clearCsrfToken } from "@/services/http";

export interface AuthService { getCurrentUser(): Promise<User>; register(input: { email: string; password: string; displayName: string }): Promise<{ id: string; status: string }>; login(email: string, password: string): Promise<User>; logout(): Promise<void> }
export interface ApprovalOptions { departments: { id: string; name: string }[]; businessRoles: { id: string; name: string }[]; classes: { id: string; name: string }[] }
export interface UserService { list(status?: User["status"]): Promise<User[]>; approvalOptions(): Promise<ApprovalOptions>; approve(id: string, input: { departmentId: string; businessRoleIds: string[]; homeroomClassId?: string; version: number }): Promise<User>; setStatus(id: string, status: "ACTIVE" | "INACTIVE", version: number): Promise<User> }
export interface WeeklyPlanOptions { dutyClasses: ApiRef[]; departments: ApiRef[]; businessRoles: ApiRef[]; users: ApiRef[] }
export interface PlanIssue { code: string; message: string }
export interface PlanValidation { valid: boolean; errors: PlanIssue[]; warnings: PlanIssue[] }
export interface RelevantItem { kind: "SECTION" | "TASK" | "HOMEROOM_CLASS"; entityId: string; title: string; content: string; matchedBy: string[]; deepLink: string }
export interface UserDashboardData { currentWeek: WeeklyPlan | null; relevantToMe: RelevantItem[]; today: WeeklyPlan["days"][number] | null; weeklyPlan: WeeklyPlan | null; notificationSummary: { unreadCount: number }; taskSummary: { total: number; completed: number; incomplete: number; overdue: number } }
export interface AdminDashboardData { currentPlan: WeeklyPlan | null; needsAttention: { pendingUsers: number; openConversations: number; incompleteTasks: number; unpublishedPlans: number } }
export interface DashboardService { me(weekId?: string): Promise<UserDashboardData>; admin(): Promise<AdminDashboardData> }
export interface WeeklyPlanService {
  getCurrent(): Promise<WeeklyPlan>; getByWeekId(weekId: string): Promise<WeeklyPlan>;
  create(weekId: string): Promise<WeeklyPlan>; copyPrevious(weekId: string, sourceWeekId: string, idempotencyKey: string): Promise<{ plan: WeeklyPlan; warnings: string[] }>;
  updateDraft(value: WeeklyPlan): Promise<WeeklyPlan>; listWeeks(academicYearId: string): Promise<AcademicWeek[]>;
  options(planId: string): Promise<WeeklyPlanOptions>; validate(id: string): Promise<PlanValidation>;
  publish(id: string, version: number, publishWithWarnings: boolean, idempotencyKey: string): Promise<WeeklyPlan>;
  updatePublished(value: WeeklyPlan, notifyWebsite: boolean, notifyEmail: boolean): Promise<WeeklyPlan>;
  addEvent(planId: string, event: Omit<EventItem, "id" | "version">, notifyWebsite?: boolean, notifyEmail?: boolean): Promise<EventItem>;
  updateEvent(event: EventItem, notifyWebsite?: boolean, notifyEmail?: boolean): Promise<EventItem>;
  deleteEvent(event: EventItem, notifyWebsite?: boolean, notifyEmail?: boolean): Promise<void>;
  exportExcel(id: string): Promise<Blob>;
}
export interface AcademicYearWrite { name: string; startDate: string; isActive: boolean; generateWeeks?: boolean }
export interface AcademicWeekWrite { displayNumber: number; weekType: WeekType; startDate: string; endDate: string; version: number }
export interface AcademicYearService {
  listYears(): Promise<AcademicYear[]>; create(input: AcademicYearWrite): Promise<AcademicYear>;
  update(id: string, input: Omit<AcademicYearWrite, "generateWeeks"> & { version: number }): Promise<AcademicYear>;
  generateWeeks(id: string): Promise<AcademicWeek[]>; listWeeks(yearId: string): Promise<AcademicWeek[]>;
  updateWeek(id: string, input: AcademicWeekWrite): Promise<AcademicWeek>;
}
export interface ResourceWrite { name: string; description?: string }
export interface ClassWrite { academicYearId: string; name: string; grade: 10 | 11 | 12; homeroomTeacherId?: string }
export interface OrganizationOptions { academicYears: ApiRef[]; availableTeachers: ApiRef[] }
export interface ClassService { list(): Promise<SchoolClass[]>; options(): Promise<OrganizationOptions>; create(input: ClassWrite): Promise<SchoolClass>; update(id: string, input: ClassWrite & { isActive: boolean; version: number }): Promise<SchoolClass> }
export interface DepartmentService { list(): Promise<Department[]>; create(input: ResourceWrite): Promise<Department>; update(id: string, input: ResourceWrite & { isActive: boolean; version: number }): Promise<Department> }
export interface BusinessRoleService { list(): Promise<BusinessRole[]>; create(input: ResourceWrite): Promise<BusinessRole>; update(id: string, input: ResourceWrite & { isActive: boolean; version: number }): Promise<BusinessRole> }
export interface TaskWrite { weeklyPlanId: string; assigneeUserId: string; title: string; description?: string; dueAt: string }
export interface TaskService { listMine(): Promise<Task[]>; listAll(): Promise<Task[]>; complete(id: string): Promise<Task>; create(input: TaskWrite): Promise<Task>; summary(): Promise<{total:number;completed:number;incomplete:number;overdue:number}>; options(): Promise<{plans:ApiRef[];users:ApiRef[]}> }
export interface NotificationService { list(read?: boolean): Promise<Notification[]>; unreadCount(): Promise<number>; markRead(id: string): Promise<void>; markAllRead(): Promise<number> }
export interface ReminderService { listMine(): Promise<Reminder[]>; create(event: EventItem, preset: "MINUTES_15" | "MINUTES_30" | "HOUR_1" | "DAY_1" | "CUSTOM", remindAt?: string): Promise<Reminder>; cancel(id: string): Promise<void> }
export interface ConversationService { list(): Promise<Conversation[]>; create(subject: string, category: string | undefined, message: string): Promise<Conversation>; sendMessage(id: string, content: string): Promise<Conversation>; close(id: string): Promise<Conversation> }
export interface AuditService { list(): Promise<AuditEntry[]> }

type ApiRef = { id: string; name: string };
type ApiUser = { id: string; email: string; displayName: string; systemRole: User["systemRole"]; status: User["status"]; department: ApiRef | null; businessRoles: ApiRef[]; homeroomClass: ApiRef | null; version: number };
const mapUser = (value: ApiUser): User => ({ id: value.id, email: value.email, name: value.displayName,
  systemRole: value.systemRole, status: value.status, department: value.department?.name ?? null,
  businessRoles: value.businessRoles.map((role) => role.name), homeroomClass: value.homeroomClass?.name ?? null,
  version: value.version });
type ApiDepartment = { id: string; name: string; description: string | null; isActive: boolean; version: number };
type ApiBusinessRole = ApiDepartment & { isProtected: boolean };
type ApiSchoolClass = { id: string; academicYear: ApiRef; name: string; grade: 10 | 11 | 12; homeroomTeacher: ApiRef | null; isActive: boolean; version: number };
type ApiAcademicYear = { id: string; name: string; startDate: string; isActive: boolean; version: number; weekCount: number };
type ApiAcademicWeek = { id: string; academicYearId: string; sequenceNumber: number; displayNumber: number; weekType: WeekType; startDate: string; endDate: string; version: number; warnings: string[] };
type ApiTask = { id:string;weeklyPlanId:string;assignee:ApiRef;title:string;description:string|null;dueAt:string;status:Task["status"];displayStatus:Task["displayStatus"];completedAt:string|null;version:number };
type ApiPlanWeek = { id: string; academicYearId: string; sequenceNumber: number; label: string; startDate: string; endDate: string; planStatus: PlanStatus | null };
type ApiWeeklyPlan = { id: string; weekId: string; sequenceNumber: number; displayLabel: string; startDate: string; endDate: string; status: PlanStatus; version: number;
  publishedAt: string | null; publishedBy: string | null;
  morningDutyClass: ApiRef | null; afternoonDutyClass: ApiRef | null;
  sections: { id: string; sectionType: SectionType; title: string; content: string; displayOrder: number; targets: { targetType: TargetType; targetId: string | null; label: string }[] }[];
  days: { date: string; dayLabel: string; sessions: { session: Session; baseContent: string; events: EventItem[] }[] }[] };
const mapDepartment = (value: ApiDepartment): Department => ({ ...value, description: value.description ?? undefined });
const mapRole = (value: ApiBusinessRole): BusinessRole => ({ ...value, description: value.description ?? undefined });
const mapClass = (value: ApiSchoolClass): SchoolClass => ({ id: value.id, academicYearId: value.academicYear.id,
  academicYearName: value.academicYear.name, name: value.name, grade: value.grade,
  homeroomTeacherId: value.homeroomTeacher?.id ?? null, homeroomTeacher: value.homeroomTeacher?.name ?? null,
  isActive: value.isActive, version: value.version });
const mapAcademicWeek = (value: ApiAcademicWeek): AcademicWeek => ({ ...value,
  label: value.weekType === "ORIENTATION" ? `Tuần định hướng ${value.displayNumber}` : `Tuần ${value.displayNumber}` });
const mapTask=(value:ApiTask):Task=>({id:value.id,weeklyPlanId:value.weeklyPlanId,assigneeId:value.assignee.id,assigneeName:value.assignee.name,title:value.title,description:value.description??undefined,dueAt:value.dueAt,status:value.status,displayStatus:value.displayStatus,completedAt:value.completedAt??undefined,version:value.version});
const mapWeeklyPlan = (value: ApiWeeklyPlan): WeeklyPlan => ({ id: value.id, weekId: value.weekId,
  sequenceNumber: value.sequenceNumber, displayLabel: value.displayLabel, startDate: value.startDate, endDate: value.endDate,
  status: value.status, version: value.version, morningDutyClassId: value.morningDutyClass?.id,
  publishedAt: value.publishedAt ?? undefined, publishedBy: value.publishedBy ?? undefined,
  morningDutyClass: value.morningDutyClass?.name, afternoonDutyClassId: value.afternoonDutyClass?.id,
  afternoonDutyClass: value.afternoonDutyClass?.name,
  sections: value.sections.map((section) => ({ id: section.id, type: section.sectionType, title: section.title,
    content: section.content, targets: section.targets.map((target) => ({ type: target.targetType,
      id: target.targetId ?? undefined, label: target.label })) })), days: value.days });
const planWrite = (value: WeeklyPlan) => ({
  version: value.version,
  sections: value.sections.map((section, index) => ({ sectionType: section.type, content: section.content,
    displayOrder: index + 1, targets: section.targets.map((target) => ({ targetType: target.type, targetId: target.id })) })),
  dutyClasses: { morningClassId: value.morningDutyClassId, afternoonClassId: value.afternoonDutyClassId },
  daySessions: value.days.flatMap((day) => day.sessions.map((session) => ({ date: day.date,
    session: session.session, baseContent: session.baseContent }))),
});
const eventWrite = (value: Partial<EventItem> & { content: string }, extra: Record<string, unknown>) => ({
  content: value.content,
  startDate: value.startDate || undefined,
  endDate: value.endDate || undefined,
  session: value.session || undefined,
  startTime: value.startTime || undefined,
  endTime: value.endTime || undefined,
  location: value.location || undefined,
  note: value.note || undefined,
  ...extra,
});

export const authService: AuthService = {
  async getCurrentUser() { return mapUser(await apiRequest<ApiUser>("/auth/me")); },
  async register(input) { return apiRequest("/auth/register", { method: "POST", body: JSON.stringify(input) }); },
  async login(email, password) { return mapUser(await apiRequest<ApiUser>("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) })); },
  async logout() { await apiRequest<void>("/auth/logout", { method: "POST" }); clearCsrfToken(); },
};
export const userService: UserService = {
  async list(status) { const query = status ? `?status=${status}` : ""; const users = await apiRequest<ApiUser[]>(`/users${query}`); return users.map(mapUser); },
  async approvalOptions() { return apiRequest("/users/approval-options"); },
  async approve(id, input) { return mapUser(await apiRequest<ApiUser>(`/users/${id}/approval`, { method: "PATCH", body: JSON.stringify(input) })); },
  async setStatus(id, status, version) { return mapUser(await apiRequest<ApiUser>(`/users/${id}/status`, { method: "PATCH", body: JSON.stringify({ status, version }) })); },
};
export const weeklyPlanService: WeeklyPlanService = {
  async getCurrent() { return mapWeeklyPlan(await apiRequest<ApiWeeklyPlan>("/weekly-plans/current")); },
  async getByWeekId(id) { return mapWeeklyPlan(await apiRequest<ApiWeeklyPlan>(`/weeks/${id}/plan`)); },
  async create(weekId) { return mapWeeklyPlan(await apiRequest<ApiWeeklyPlan>(`/weeks/${weekId}/plan`, { method: "POST" })); },
  async copyPrevious(weekId, sourceWeekId, idempotencyKey) { const result = await apiRequest<{ plan: ApiWeeklyPlan; warnings: string[] }>(`/weeks/${weekId}/plan/copy`, { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify({ sourceWeekId }) }); return { plan: mapWeeklyPlan(result.plan), warnings: result.warnings }; },
  async updateDraft(value) { return mapWeeklyPlan(await apiRequest<ApiWeeklyPlan>(`/weekly-plans/${value.id}`, { method: "PATCH", body: JSON.stringify(planWrite(value)) })); },
  async listWeeks(academicYearId) { return (await apiRequest<ApiPlanWeek[]>(`/weekly-plans?academicYearId=${academicYearId}`)).map((week) => ({ ...week, planStatus: week.planStatus ?? undefined })); },
  async options(planId) { return apiRequest(`/weekly-plans/${planId}/options`); },
  async validate(id) { return apiRequest(`/weekly-plans/${id}/validation`); },
  async publish(id, version, publishWithWarnings, idempotencyKey) { return mapWeeklyPlan(await apiRequest<ApiWeeklyPlan>(`/weekly-plans/${id}/publish`, { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify({ version, publishWithWarnings }) })); },
  async updatePublished(value, notifyWebsite, notifyEmail) { return mapWeeklyPlan(await apiRequest<ApiWeeklyPlan>(`/weekly-plans/${value.id}/published-content`, { method: "PATCH", body: JSON.stringify({ ...planWrite(value), notifyWebsite, notifyEmail }) })); },
  async addEvent(planId, value, notifyWebsite, notifyEmail) { return apiRequest<EventItem>(`/weekly-plans/${planId}/events`, { method: "POST", body: JSON.stringify(eventWrite(value, { notifyWebsite, notifyEmail })) }); },
  async updateEvent(value, notifyWebsite, notifyEmail) { return apiRequest<EventItem>(`/events/${value.id}`, { method: "PATCH", body: JSON.stringify(eventWrite(value, { version: value.version ?? 0, notifyWebsite, notifyEmail })) }); },
  async deleteEvent(value, notifyWebsite, notifyEmail) { const query = new URLSearchParams({ version: String(value.version ?? 0) }); if (notifyWebsite !== undefined) query.set("notifyWebsite", String(notifyWebsite)); if (notifyEmail !== undefined) query.set("notifyEmail", String(notifyEmail)); await apiRequest<void>(`/events/${value.id}?${query}`, { method: "DELETE" }); },
  async exportExcel(id) { return apiDownload(`/weekly-plans/${id}/export`); },
};
export const dashboardService: DashboardService = {
  async me(weekId) { const raw = await apiRequest<Omit<UserDashboardData, "currentWeek" | "weeklyPlan"> & { currentWeek: ApiWeeklyPlan | null; weeklyPlan: ApiWeeklyPlan | null }>(`/dashboard/me${weekId ? `?weekId=${weekId}` : ""}`); return { ...raw, currentWeek: raw.currentWeek ? mapWeeklyPlan(raw.currentWeek) : null, weeklyPlan: raw.weeklyPlan ? mapWeeklyPlan(raw.weeklyPlan) : null }; },
  async admin() { const raw = await apiRequest<{ currentPlan: ApiWeeklyPlan | null; needsAttention: AdminDashboardData["needsAttention"] }>("/dashboard/admin"); return { ...raw, currentPlan: raw.currentPlan ? mapWeeklyPlan(raw.currentPlan) : null }; },
};
export const academicYearService: AcademicYearService = {
  async listYears() { return apiRequest<ApiAcademicYear[]>("/academic-years"); },
  async create(input) { return apiRequest<ApiAcademicYear>("/academic-years", { method: "POST", body: JSON.stringify(input) }); },
  async update(id, input) { return apiRequest<ApiAcademicYear>(`/academic-years/${id}`, { method: "PATCH", body: JSON.stringify(input) }); },
  async generateWeeks(id) { return (await apiRequest<ApiAcademicWeek[]>(`/academic-years/${id}/weeks/generate`, { method: "POST", body: JSON.stringify({ count: 39 }) })).map(mapAcademicWeek); },
  async listWeeks(yearId) { return (await apiRequest<ApiAcademicWeek[]>(`/academic-years/${yearId}/weeks`)).map(mapAcademicWeek); },
  async updateWeek(id, input) { return mapAcademicWeek(await apiRequest<ApiAcademicWeek>(`/weeks/${id}`, { method: "PATCH", body: JSON.stringify(input) })); },
};
export const classService: ClassService = {
  async list() { return (await apiRequest<ApiSchoolClass[]>("/classes")).map(mapClass); },
  async options() { return apiRequest("/organization/options"); },
  async create(input) { return mapClass(await apiRequest<ApiSchoolClass>("/classes", { method: "POST", body: JSON.stringify(input) })); },
  async update(id, input) { return mapClass(await apiRequest<ApiSchoolClass>(`/classes/${id}`, { method: "PATCH", body: JSON.stringify(input) })); },
};
export const departmentService: DepartmentService = {
  async list() { return (await apiRequest<ApiDepartment[]>("/departments")).map(mapDepartment); },
  async create(input) { return mapDepartment(await apiRequest<ApiDepartment>("/departments", { method: "POST", body: JSON.stringify(input) })); },
  async update(id, input) { return mapDepartment(await apiRequest<ApiDepartment>(`/departments/${id}`, { method: "PATCH", body: JSON.stringify(input) })); },
};
export const businessRoleService: BusinessRoleService = {
  async list() { return (await apiRequest<ApiBusinessRole[]>("/business-roles")).map(mapRole); },
  async create(input) { return mapRole(await apiRequest<ApiBusinessRole>("/business-roles", { method: "POST", body: JSON.stringify(input) })); },
  async update(id, input) { return mapRole(await apiRequest<ApiBusinessRole>(`/business-roles/${id}`, { method: "PATCH", body: JSON.stringify(input) })); },
};
export const taskService: TaskService = {
  async listMine() { return (await apiRequest<ApiTask[]>("/tasks/me")).map(mapTask); }, async listAll() { return (await apiRequest<ApiTask[]>("/tasks")).map(mapTask); },
  async complete(id) { const current=(await this.listMine()).find((item)=>item.id===id); if(!current) throw new Error("Không tìm thấy nhiệm vụ"); return mapTask(await apiRequest<ApiTask>(`/tasks/${id}/complete`,{method:"PATCH",body:JSON.stringify({version:current.version??0})})); },
  async create(input){return mapTask(await apiRequest<ApiTask>("/tasks",{method:"POST",body:JSON.stringify(input)}));},
  async summary(){return apiRequest("/tasks/summary");}, async options(){return apiRequest("/tasks/options");},
};
export const notificationService: NotificationService = {
  async list(read) { return apiRequest<Notification[]>(`/notifications${read===undefined?"":`?read=${read}`}`); },
  async unreadCount(){return (await apiRequest<{count:number}>("/notifications/unread-count")).count;},
  async markRead(id) { await apiRequest(`/notifications/${id}/read`,{method:"PATCH",body:"{}"}); },
  async markAllRead() { return (await apiRequest<{updatedCount:number}>("/notifications/read-all",{method:"PATCH",body:"{}"})).updatedCount; },
};
export const reminderService: ReminderService = {
  async listMine() { return apiRequest<Reminder[]>("/reminders/me"); },
  async create(event, preset, remindAt) { const values=await apiRequest<Reminder[]>(`/events/${event.id}/reminders`,{method:"POST",body:JSON.stringify({preset,remindAt})}); return values[0]; },
  async cancel(id) { await apiRequest(`/reminders/${id}`,{method:"DELETE"}); },
};
export const conversationService: ConversationService = {
  async list() { return apiRequest<Conversation[]>("/conversations"); },
  async create(subject, category, message) { return apiRequest<Conversation>("/conversations",{method:"POST",body:JSON.stringify({subject,category,message})}); },
  async sendMessage(id, content) { return apiRequest<Conversation>(`/conversations/${id}/messages`,{method:"POST",body:JSON.stringify({content})}); },
  async close(id) { const item=(await this.list()).find(value=>value.id===id); if(!item)throw new Error("Không tìm thấy trao đổi"); return apiRequest<Conversation>(`/conversations/${id}/close`,{method:"PATCH",body:JSON.stringify({version:item.version??0})}); },
};
export const auditService: AuditService = { async list() { const rows=await apiRequest<{id:string;createdAt:string;actor:string;entityType:string;entityId:string;action:string;oldValue:string|null;newValue:string|null}[]>("/audit-logs"); return rows.map(item=>({...item,entityLabel:`${item.entityType} · ${item.entityId}`,oldValue:item.oldValue??undefined,newValue:item.newValue??undefined})); } };
