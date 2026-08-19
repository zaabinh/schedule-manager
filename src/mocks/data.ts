import type { AcademicWeek, AuditEntry, BusinessRole, Conversation, Department, Notification, Reminder, SchoolClass, Task, User, WeeklyPlan } from "@/types/domain";

export const currentUser: User = {
  id: "user-an", name: "Nguyễn Văn An", email: "an.nguyen@example.com", systemRole: "USER", status: "ACTIVE",
  department: "Tổ Toán", businessRoles: ["Giáo viên", "GVCN"], homeroomClass: "11A2",
};

export const adminUser: User = {
  id: "admin-1", name: "Trần Minh Hà", email: "hieu.truong@example.edu.vn", systemRole: "ADMIN", status: "ACTIVE",
  department: "Ban giám hiệu", businessRoles: ["Ban giám hiệu"], homeroomClass: null,
};

export const plan: WeeklyPlan = {
  id: "plan-12", weekId: "week-12", sequenceNumber: 14, displayLabel: "Tuần 12", startDate: "2026-11-09", endDate: "2026-11-15",
  status: "PUBLISHED", version: 4, morningDutyClass: "11A2", afternoonDutyClass: "10A10",
  sections: [
    { id: "s1", type: "ACADEMIC_AFFAIRS", title: "CT Chuyên môn", content: "Hoàn thành nhập điểm giữa học kỳ và rà soát tiến độ chương trình.", targets: [{ type: "ROLE", label: "Giáo viên" }], relevant: true },
    { id: "s2", type: "FACILITIES_OFFICE", title: "CT CSVC – TBDH – VP", content: "Kiểm tra thiết bị phòng thực hành; nộp đề xuất sửa chữa trước thứ Sáu.", targets: [{ type: "DEPARTMENT", label: "Văn phòng" }] },
    { id: "s3", type: "YOUTH_UNION", title: "Đoàn TN – Hội LHTN", content: "Triển khai tuần lễ tri ân thầy cô và vệ sinh khuôn viên.", targets: [{ type: "ROLE", label: "Đoàn TN" }] },
    { id: "s4", type: "HOMEROOM_TEACHERS", title: "GVCN", content: "Hoàn thành dự kiến xếp loại rèn luyện học kỳ I.", targets: [{ type: "ROLE", label: "GVCN" }], relevant: true },
    { id: "s5", type: "TEACHERS", title: "Giáo viên", content: "Ổn định nền nếp, cập nhật sổ đầu bài đầy đủ sau mỗi tiết.", targets: [{ type: "ALL", label: "Tất cả" }], relevant: true },
  ],
  days: [
    { date: "2026-11-09", dayLabel: "Thứ Hai", sessions: [
      { session: "MORNING", baseContent: "Học theo thời khóa biểu số 11", events: [{ id: "e1", content: "Chào cờ đầu tuần", startDate: "2026-11-09", session: "MORNING", startTime: "07:00", endTime: "07:45", location: "Sân trường", note: "Học sinh tập trung trước 06:50." }] },
      { session: "AFTERNOON", baseContent: "Học theo thời khóa biểu số 11", events: [{ id: "e2", content: "Họp tổ chuyên môn", startDate: "2026-11-09", session: "AFTERNOON", startTime: "14:00", endTime: "15:30", location: "Phòng họp 2" }] },
    ]},
    { date: "2026-11-10", dayLabel: "Thứ Ba", sessions: [
      { session: "MORNING", baseContent: "Học theo thời khóa biểu số 11", events: [] },
      { session: "AFTERNOON", baseContent: "Học theo thời khóa biểu số 11", events: [{ id: "e3", content: "Họp GVCN", startDate: "2026-11-10", session: "AFTERNOON", startTime: "14:00", location: "Phòng hội đồng" }] },
    ]},
    { date: "2026-11-11", dayLabel: "Thứ Tư", sessions: [
      { session: "MORNING", baseContent: "Học theo thời khóa biểu số 11", events: [] },
      { session: "AFTERNOON", baseContent: "Hoạt động chuyên môn", events: [{ id: "e4", content: "Chuyên đề đổi mới phương pháp dạy học", startDate: "2026-11-11", session: "AFTERNOON", startTime: "13:30", endTime: "16:00", location: "Phòng đa năng" }] },
    ]},
    { date: "2026-11-12", dayLabel: "Thứ Năm", sessions: [
      { session: "MORNING", baseContent: "Học theo thời khóa biểu số 11", events: [{ id: "e5", content: "Chấm thi khoa học kỹ thuật", startDate: "2026-11-12", endDate: "2026-11-13", location: "Nhà đa năng", note: "Sự kiện diễn ra trong hai ngày." }] },
      { session: "AFTERNOON", baseContent: "Học theo thời khóa biểu số 11", events: [] },
    ]},
    { date: "2026-11-13", dayLabel: "Thứ Sáu", sessions: [
      { session: "MORNING", baseContent: "Học theo thời khóa biểu số 11", events: [] },
      { session: "AFTERNOON", baseContent: "Sinh hoạt câu lạc bộ", events: [] },
    ]},
    { date: "2026-11-14", dayLabel: "Thứ Bảy", sessions: [
      { session: "MORNING", baseContent: "Học theo thời khóa biểu số 11", events: [{ id: "e6", content: "Công tác Đoàn: Ngày thứ Bảy xanh", startDate: "2026-11-14", session: "MORNING", startTime: "08:00", location: "Khuôn viên trường" }] },
      { session: "AFTERNOON", baseContent: "", events: [] },
    ]},
    { date: "2026-11-15", dayLabel: "Chủ Nhật", sessions: [
      { session: "MORNING", baseContent: "Nghỉ", events: [] }, { session: "AFTERNOON", baseContent: "Nghỉ", events: [] },
    ]},
  ],
};

export const draftPlan: WeeklyPlan = { ...plan, id: "plan-draft", weekId: "week-13", displayLabel: "Tuần 13", startDate: "2026-11-16", endDate: "2026-11-22", status: "DRAFT", version: 2, afternoonDutyClass: undefined };

export const weeks: AcademicWeek[] = [
  { id: "week-o1", label: "Tuần tựu trường 1", startDate: "2026-08-24", endDate: "2026-08-30", planStatus: "PUBLISHED" },
  { id: "week-o2", label: "Tuần tựu trường 2", startDate: "2026-08-31", endDate: "2026-09-06", planStatus: "PUBLISHED" },
  { id: "week-1", label: "Tuần 1", startDate: "2026-09-07", endDate: "2026-09-13", planStatus: "PUBLISHED" },
  { id: "week-12", label: "Tuần 12", startDate: "2026-11-09", endDate: "2026-11-15", planStatus: "PUBLISHED" },
  { id: "week-13", label: "Tuần 13", startDate: "2026-11-16", endDate: "2026-11-22", planStatus: "DRAFT" },
  { id: "week-14", label: "Tuần 14", startDate: "2026-11-23", endDate: "2026-11-29" },
];

export const tasks: Task[] = [
  { id: "t1", title: "Nộp báo cáo chuyên môn", description: "Tổng hợp tiến độ chương trình của tổ Toán.", dueAt: "2026-11-13T17:00:00+07:00", status: "TODO", weeklyPlanId: "plan-12" },
  { id: "t2", title: "Hoàn thành nhập điểm giữa kỳ", dueAt: "2026-11-12T17:00:00+07:00", status: "TODO", weeklyPlanId: "plan-12" },
  { id: "t3", title: "Kiểm tra hồ sơ chủ nhiệm", dueAt: "2026-11-10T16:00:00+07:00", status: "COMPLETED", weeklyPlanId: "plan-12" },
];

export const notifications: Notification[] = [
  { id: "n1", title: "Kế hoạch tuần 12 đã được công bố", description: "Xem các nội dung và sự kiện trong tuần này.", createdAt: "2026-11-08T19:30:00+07:00", type: "PLAN_PUBLISHED" },
  { id: "n2", title: "Bạn được giao nhiệm vụ mới", description: "Nộp báo cáo chuyên môn trước 17:00 thứ Sáu.", createdAt: "2026-11-09T08:30:00+07:00", type: "TASK_ASSIGNED" },
  { id: "n3", title: "Lớp 11A2 trực tuần buổi sáng", description: "Lớp bạn chủ nhiệm được phân công trực sáng tuần 12.", createdAt: "2026-11-08T19:31:00+07:00", type: "DUTY_CLASS" },
  { id: "n4", title: "Trao đổi có phản hồi mới", description: "Hiệu trưởng đã trả lời về lịch họp GVCN.", createdAt: "2026-11-07T15:20:00+07:00", readAt: "2026-11-07T16:00:00+07:00", type: "MESSAGE_NEW" },
];

export const reminders: Reminder[] = [
  { id: "r1", eventId: "e3", eventTitle: "Họp GVCN", remindAt: "2026-11-10T13:30:00+07:00", source: "USER", status: "PENDING" },
  { id: "r2", eventId: "e6", eventTitle: "Ngày thứ Bảy xanh", remindAt: "2026-11-13T08:00:00+07:00", source: "ADMIN", status: "PENDING" },
];

export const conversations: Conversation[] = [
  { id: "c1", subject: "Hỏi về lịch họp GVCN", category: "Kế hoạch tuần", status: "OPEN", createdBy: "user-an", updatedAt: "2026-11-09T09:20:00+07:00", messages: [
    { id: "m1", senderId: "user-an", senderName: "Nguyễn Văn An", content: "Thưa cô, cuộc họp GVCN chiều thứ Ba bắt đầu lúc 14:00 phải không ạ?", createdAt: "2026-11-09T08:45:00+07:00" },
    { id: "m2", senderId: "admin-1", senderName: "Trần Minh Hà", content: "Đúng rồi thầy An, cuộc họp bắt đầu lúc 14:00 tại phòng hội đồng.", createdAt: "2026-11-09T09:20:00+07:00" },
  ]},
  { id: "c2", subject: "Thắc mắc lịch trực", status: "CLOSED", createdBy: "user-an", updatedAt: "2026-11-02T10:00:00+07:00", messages: [{ id: "m3", senderId: "user-an", senderName: "Nguyễn Văn An", content: "Xin xác nhận lịch trực của lớp 11A2.", createdAt: "2026-11-02T08:00:00+07:00" }] },
];

export const departments: Department[] = [
  { id: "d1", name: "Ban giám hiệu", isActive: true, version: 0 }, { id: "d2", name: "Tổ Toán", isActive: true, version: 0 },
  { id: "d3", name: "Tổ Ngữ văn", isActive: true, version: 0 }, { id: "d4", name: "Tổ Ngoại ngữ", isActive: true, version: 0 },
  { id: "d5", name: "Văn phòng", isActive: true, version: 0 }, { id: "d6", name: "Tổ Vật lý", isActive: false, version: 0 },
];

export const businessRoles: BusinessRole[] = [
  { id: "b1", name: "Giáo viên", isActive: true, isProtected: true, version: 0 }, { id: "b2", name: "GVCN", isActive: true, isProtected: true, version: 0 },
  { id: "b3", name: "Tổ trưởng chuyên môn", isActive: true, isProtected: true, version: 0 }, { id: "b4", name: "Đoàn TN", isActive: true, isProtected: true, version: 0 },
  { id: "b5", name: "Hội LHTN", isActive: true, isProtected: true, version: 0 }, { id: "b6", name: "Phụ trách thư viện", isActive: true, isProtected: false, version: 0 },
];

export const classes: SchoolClass[] = [
  { id: "cl1", academicYearId: "y1", academicYearName: "2026-2027", name: "10A10", grade: 10, homeroomTeacherId: "u2", homeroomTeacher: "Lê Thu Mai", isActive: true, version: 0 },
  { id: "cl2", academicYearId: "y1", academicYearName: "2026-2027", name: "11A2", grade: 11, homeroomTeacherId: "user-an", homeroomTeacher: "Nguyễn Văn An", isActive: true, version: 0 },
  { id: "cl3", academicYearId: "y1", academicYearName: "2026-2027", name: "11B5", grade: 11, homeroomTeacherId: "u3", homeroomTeacher: "Phạm Minh Đức", isActive: true, version: 0 },
  { id: "cl4", academicYearId: "y1", academicYearName: "2026-2027", name: "12A1", grade: 12, homeroomTeacherId: null, homeroomTeacher: null, isActive: true, version: 0 },
];

export const users: User[] = [currentUser,
  { id: "u2", name: "Lê Thu Mai", email: "mai.le@example.com", systemRole: "USER", status: "ACTIVE", department: "Tổ Ngữ văn", businessRoles: ["Giáo viên", "GVCN"], homeroomClass: "10A10" },
  { id: "u3", name: "Phạm Minh Đức", email: "duc.pham@example.com", systemRole: "USER", status: "ACTIVE", department: "Tổ Ngoại ngữ", businessRoles: ["Giáo viên", "GVCN"], homeroomClass: "11B5" },
  { id: "u4", name: "Vũ Thanh Lan", email: "lan.vu@example.com", systemRole: "USER", status: "PENDING", department: null, businessRoles: [], homeroomClass: null, registeredAt: "2026-11-09T08:50:00+07:00" },
  { id: "u5", name: "Đặng Quốc Huy", email: "huy.dang@example.com", systemRole: "USER", status: "INACTIVE", department: "Tổ Toán", businessRoles: ["Giáo viên"], homeroomClass: null },
];

export const auditEntries: AuditEntry[] = [
  { id: "a1", createdAt: "2026-11-09T07:45:00+07:00", actor: "Trần Minh Hà", entityType: "WeeklyPlan", action: "UPDATE_EVENT", entityLabel: "Tuần 12 · Họp GVCN", oldValue: "Giờ bắt đầu: 15:00", newValue: "Giờ bắt đầu: 14:00" },
  { id: "a2", createdAt: "2026-11-08T19:30:00+07:00", actor: "Trần Minh Hà", entityType: "WeeklyPlan", action: "PUBLISH", entityLabel: "Kế hoạch tuần 12", newValue: "Trạng thái: PUBLISHED" },
  { id: "a3", createdAt: "2026-11-08T17:10:00+07:00", actor: "Trần Minh Hà", entityType: "Task", action: "CREATE", entityLabel: "Nộp báo cáo chuyên môn", newValue: "Người nhận: Nguyễn Văn An" },
];
