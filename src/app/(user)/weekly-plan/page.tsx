"use client";

import { CalendarDays, LayoutList, Printer, School } from "lucide-react";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { weeklyPlanService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { WeekNavigator } from "@/components/plan/week-navigator";
import { PlanSectionCard } from "@/components/plan/plan-section-card";
import { WeeklyPlanTable } from "@/components/plan/weekly-plan-table";
import { WeeklyCalendar } from "@/components/plan/weekly-calendar";
import { Button } from "@/components/ui/button";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";
import { cn } from "@/lib/utils";

export default function WeeklyPlanPage() {
  const [view, setView] = useState<"plan" | "calendar">("plan");
  const query = useQuery({ queryKey: ["weekly-plan", "current"], queryFn: () => weeklyPlanService.getCurrent() });
  if (query.isLoading) return <LoadingSkeleton rows={5}/>;
  if (query.isError) return <ErrorState retry={() => query.refetch()}/>;
  if (!query.data) return <EmptyState title="Chưa có kế hoạch tuần này" description="Kế hoạch sẽ hiển thị sau khi Hiệu trưởng công bố."/>;
  const value = query.data;

  return <div className="weekly-plan-page">
    <PageHeader eyebrow={value.displayLabel} title={`Kế hoạch ${value.displayLabel.toLowerCase()}`} description="09/11 – 15/11/2026" actions={<div className="print-hide flex flex-wrap gap-2"><WeekNavigator/><Button variant="secondary" size="sm" onClick={() => window.print()}><Printer size={16}/>In một trang</Button></div>}/>
    <div className="print-hide mb-3 inline-flex rounded-xl border bg-white p-1" role="tablist" aria-label="Chế độ xem"><Tab active={view === "plan"} onClick={() => setView("plan")} icon={<LayoutList/>}>Kế hoạch</Tab><Tab active={view === "calendar"} onClick={() => setView("calendar")} icon={<CalendarDays/>}>Lịch tuần</Tab></div>
    {view === "plan" ? <div className="space-y-4">
      <section className="plan-sections-screen"><h2 className="mb-2 text-sm font-bold">Phân công chung</h2><div className="grid min-w-0 gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">{value.sections.map((section) => <PlanSectionCard key={section.id} section={section}/>)}</div></section>
      <section><div className="mb-2.5 flex flex-col justify-between gap-2 sm:flex-row sm:items-end"><div><h2 className="text-lg font-bold">Bảng kế hoạch tuần</h2><p className="mt-0.5 text-xs text-slate-500 print:hidden">Bố cục ba cột, tối ưu để in một trang A4 ngang.</p></div><DutyRow morning={value.morningDutyClass} afternoon={value.afternoonDutyClass}/></div><WeeklyPlanTable value={value}/></section>
    </div> : <div><div className="mb-2.5 flex justify-end"><DutyRow morning={value.morningDutyClass} afternoon={value.afternoonDutyClass}/></div><WeeklyCalendar value={value}/></div>} 
  </div>;
}

function DutyRow({ morning, afternoon }: { morning?: string; afternoon?: string }) {
  return <div className="flex flex-wrap gap-1.5"><Duty label="Trực sáng" value={morning}/><Duty label="Trực chiều" value={afternoon}/></div>;
}

function Duty({ label, value }: { label: string; value?: string }) {
  return <span className="inline-flex items-center gap-1.5 rounded-md border bg-white px-2 py-1 text-[11px]"><School size={13} className="text-[var(--primary)]"/><span className="text-slate-500">{label}:</span><strong className="text-slate-800">{value ?? "Chưa chọn"}</strong></span>;
}

function Tab({ active, onClick, icon, children }: { active: boolean; onClick: () => void; icon: React.ReactElement; children: React.ReactNode }) {
  return <button role="tab" aria-selected={active} onClick={onClick} className={cn("flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold", active ? "bg-[var(--primary)] text-white" : "text-slate-500 hover:bg-slate-50")}>{icon}{children}</button>;
}
