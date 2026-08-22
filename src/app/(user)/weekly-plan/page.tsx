"use client";

import { CalendarDays, LayoutList, Printer, School } from "lucide-react";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/layout/page-header";
import { PlanSectionCard } from "@/components/plan/plan-section-card";
import { WeekNavigator } from "@/components/plan/week-navigator";
import { WeeklyCalendar } from "@/components/plan/weekly-calendar";
import { WeeklyPlanTable } from "@/components/plan/weekly-plan-table";
import { Button } from "@/components/ui/button";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/components/ui/states";
import { cn, formatDate } from "@/lib/utils";
import { weeklyPlanService } from "@/services";

export default function WeeklyPlanPage() {
  const [view, setView] = useState<"plan" | "calendar">("calendar");
  const [weekId, setWeekId] = useState("");
  const weeksQuery = useQuery({ queryKey: ["weekly-plans", "published"], queryFn: () => weeklyPlanService.listPublished() });
  const weeks = weeksQuery.data ?? [];
  const today = localIsoDate();
  const current = weeks.find((week) => week.startDate <= today && week.endDate >= today);
  const effectiveWeekId = weekId || current?.id || weeks[0]?.id || "";
  const planQuery = useQuery({
    queryKey: ["weekly-plan", "published", effectiveWeekId],
    queryFn: () => weeklyPlanService.getByWeekId(effectiveWeekId),
    enabled: Boolean(effectiveWeekId),
  });

  if (weeksQuery.isLoading) return <LoadingSkeleton rows={5}/>;
  if (weeksQuery.isError) return <ErrorState retry={() => weeksQuery.refetch()}/>;
  if (weeks.length === 0) return <EmptyState title="Chưa có kế hoạch đã xuất bản" description="Kế hoạch sẽ hiển thị sau khi Hiệu trưởng công bố."/>;
  if (planQuery.isError) return <ErrorState retry={() => planQuery.refetch()}/>;
  if (planQuery.isLoading || !planQuery.data) return <LoadingSkeleton rows={5}/>;

  const value = planQuery.data;
  const selectedIndex = weeks.findIndex((week) => week.id === effectiveWeekId);
  const selectWeek = (id: string) => setWeekId(id);
  const printPlan = () => {
    setView("plan");
    window.setTimeout(() => window.print(), 0);
  };

  return <div className="weekly-plan-page">
    <PageHeader
      eyebrow="Kế hoạch đã xuất bản"
      title={`Kế hoạch ${value.displayLabel.toLowerCase()}`}
      description={`${formatDate(value.startDate)} – ${formatDate(value.endDate)}`}
      actions={<Button className="print-hide" variant="secondary" size="sm" onClick={printPlan}><Printer size={16}/>In kế hoạch</Button>}
    />

    <section className="print-hide mb-4 rounded-2xl border bg-white p-3 shadow-sm sm:p-4" aria-label="Chọn tuần cần xem">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0 flex-1 lg:max-w-xl">
          <div className="mb-1.5 flex items-center justify-between gap-3"><span className="field-label !mb-0">Tuần cần xem</span><span className="text-xs font-medium text-slate-500">{weeks.length} tuần đã xuất bản</span></div>
          <SearchableSelect
            ariaLabel="Tuần cần xem"
            value={effectiveWeekId}
            onValueChange={selectWeek}
            searchPlaceholder="Tìm theo tên tuần hoặc ngày…"
            options={weeks.map((week) => ({ value: week.id, label: week.label, description: `${formatDate(week.startDate)} – ${formatDate(week.endDate)}` }))}
          />
        </div>
        <WeekNavigator
          onPrevious={() => selectWeek(weeks[selectedIndex + 1].id)}
          onNext={() => selectWeek(weeks[selectedIndex - 1].id)}
          previousDisabled={selectedIndex < 0 || selectedIndex >= weeks.length - 1}
          nextDisabled={selectedIndex <= 0}
        />
      </div>
    </section>

    <div className="print-hide mb-4 inline-flex rounded-xl border bg-white p-1" role="tablist" aria-label="Chế độ xem">
      <Tab active={view === "calendar"} onClick={() => setView("calendar")} icon={<CalendarDays/>}>Tổng quan</Tab>
      <Tab active={view === "plan"} onClick={() => setView("plan")} icon={<LayoutList/>}>Chi tiết</Tab>
    </div>

    {view === "plan" ? <div className="space-y-4">
      <section className="plan-sections-screen"><h2 className="mb-2 text-base font-bold">Phân công chung</h2><div className="grid min-w-0 gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">{value.sections.map((section) => <PlanSectionCard key={section.id} section={section}/>)}</div></section>
      <section><div className="mb-2.5 flex flex-col justify-between gap-2 sm:flex-row sm:items-end"><div><h2 className="text-lg font-bold">Bảng kế hoạch tuần</h2><p className="mt-0.5 text-sm text-slate-600 print:hidden">Hai buổi được đặt cạnh nhau để dễ đối chiếu.</p></div><DutyRow morning={value.morningDutyClass} afternoon={value.afternoonDutyClass}/></div><WeeklyPlanTable value={value}/></section>
    </div> : <div>
      <div className="mb-3 flex flex-col justify-between gap-2 sm:flex-row sm:items-end"><div><h2 className="text-lg font-bold">Lịch cả tuần</h2><p className="mt-0.5 text-sm text-slate-600">Toàn bộ 7 ngày và hai buổi trong một khung nhìn.</p></div><DutyRow morning={value.morningDutyClass} afternoon={value.afternoonDutyClass}/></div>
      <WeeklyCalendar value={value}/>
    </div>}
  </div>;
}

function localIsoDate() {
  const value = new Date();
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function DutyRow({ morning, afternoon }: { morning?: string; afternoon?: string }) {
  return <div className="flex flex-wrap gap-2"><Duty label="Trực sáng" value={morning}/><Duty label="Trực chiều" value={afternoon}/></div>;
}

function Duty({ label, value }: { label: string; value?: string }) {
  return <span className="inline-flex items-center gap-1.5 rounded-lg border bg-white px-2.5 py-1.5 text-xs shadow-sm"><School size={14} className="text-[var(--primary)]"/><span className="text-slate-600">{label}:</span><strong className="text-slate-900">{value ?? "Chưa chọn"}</strong></span>;
}

function Tab({ active, onClick, icon, children }: { active: boolean; onClick: () => void; icon: React.ReactElement; children: React.ReactNode }) {
  return <button role="tab" aria-selected={active} onClick={onClick} className={cn("flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition sm:px-4", active ? "bg-[var(--primary)] text-white shadow-sm" : "text-slate-600 hover:bg-slate-50")}>{icon}{children}</button>;
}
