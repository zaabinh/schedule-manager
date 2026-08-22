"use client";

import { Copy, Edit3, FileDown, FilePlus2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { academicYearService, weeklyPlanService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
import { formatDate } from "@/lib/utils";
import { SearchableSelect } from "@/components/ui/searchable-select";

export default function WeeklyPlansPage() {
  const router = useRouter();
  const yearsQuery = useQuery({ queryKey: ["weekly-plan-years"], queryFn: () => academicYearService.listYears() });
  const [yearId, setYearId] = useState("");
  const effectiveYear = yearId || yearsQuery.data?.find((item) => item.isActive)?.id || yearsQuery.data?.[0]?.id || "";
  const weeksQuery = useQuery({ queryKey: ["weekly-plan-weeks", effectiveYear], queryFn: () => weeklyPlanService.listWeeks(effectiveYear), enabled: Boolean(effectiveYear) });
  const [busyWeek, setBusyWeek] = useState<string>();
  const [error, setError] = useState<string>();
  if (yearsQuery.isLoading || weeksQuery.isLoading) return <LoadingSkeleton rows={5}/>;
  if (yearsQuery.isError || weeksQuery.isError) return <ErrorState retry={() => { yearsQuery.refetch(); weeksQuery.refetch(); }}/>;
  const years = yearsQuery.data ?? [];
  const weeks = weeksQuery.data ?? [];
  const selectedYear = years.find((item) => item.id === effectiveYear);

  async function create(weekId: string) {
    setBusyWeek(weekId); setError(undefined);
    try { await weeklyPlanService.create(weekId); router.push(`/admin/weekly-plans/${weekId}/edit`); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể tạo kế hoạch."); setBusyWeek(undefined); }
  }
  async function copyPrevious(weekId: string, sequence: number) {
    const source = [...weeks].reverse().find((item) => (item.sequenceNumber ?? 0) < sequence && item.planStatus);
    if (!source) { setError("Không có kế hoạch tuần trước để sao chép."); return; }
    setBusyWeek(weekId); setError(undefined);
    try { await weeklyPlanService.copyPrevious(weekId, source.id, crypto.randomUUID()); router.push(`/admin/weekly-plans/${weekId}/edit`); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể sao chép kế hoạch."); setBusyWeek(undefined); }
  }
  async function exportExcel(weekId: string) {
    setBusyWeek(weekId); setError(undefined);
    try { const plan=await weeklyPlanService.getByWeekId(weekId); const blob=await weeklyPlanService.exportExcel(plan.id); const url=URL.createObjectURL(blob); const link=document.createElement("a"); link.href=url; link.download=`ke-hoach-${weekId}.xlsx`; link.click(); URL.revokeObjectURL(url); }
    catch(caught){setError(caught instanceof Error?caught.message:"Không thể xuất Excel.");}
    finally{setBusyWeek(undefined);}
  }

  return <><PageHeader eyebrow={selectedYear ? `Năm học ${selectedYear.name}` : "Lịch năm học"} title="Kế hoạch tuần" description="Tạo DRAFT mới hoặc sao chép nội dung nền từ tuần trước." actions={<div className="w-full sm:w-64"><SearchableSelect ariaLabel="Năm học" value={effectiveYear} onValueChange={setYearId} placeholder="Chọn năm học" searchPlaceholder="Tìm năm học…" options={years.map((year) => ({ value: year.id, label: year.name, description: year.isActive ? "Đang sử dụng" : undefined }))}/></div>}/>
  {error && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
  {years.length === 0 ? <div className="rounded-xl border border-dashed bg-white p-10 text-center text-sm text-slate-500">Chưa có năm học và tuần học. Hãy cấu hình tại mục Năm học.</div> : <div className="space-y-3">{weeks.map((week) => <article key={week.id} className="card flex flex-col justify-between gap-4 p-5 md:flex-row md:items-center"><div><div className="flex flex-wrap items-center gap-3"><h2 className="font-bold">{week.label}</h2>{week.planStatus ? <StatusBadge status={week.planStatus}/> : <span className="rounded-full border px-2.5 py-1 text-xs font-bold text-slate-500">Chưa tạo</span>}</div><p className="mt-2 text-sm text-slate-500">{formatDate(week.startDate)} – {formatDate(week.endDate)}</p></div><div className="flex flex-wrap gap-2">{week.planStatus ? <><Button size="sm" variant="secondary" asChild><Link href={`/admin/weekly-plans/${week.id}/edit`}><Edit3 size={15}/>Chỉnh sửa</Link></Button><Button size="sm" variant="secondary" disabled={busyWeek===week.id} onClick={()=>void exportExcel(week.id)}><FileDown size={15}/>Excel</Button></> : <><Button size="sm" variant="secondary" disabled={busyWeek === week.id} onClick={() => void create(week.id)}><FilePlus2 size={15}/>Tạo DRAFT</Button><Button size="sm" disabled={busyWeek === week.id} onClick={() => void copyPrevious(week.id, week.sequenceNumber ?? 0)}><Copy size={15}/>Copy tuần trước</Button></>}</div></article>)}</div>}</>;
}
