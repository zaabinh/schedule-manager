"use client";

import { AlertTriangle, CalendarPlus, Edit3, Plus, Power } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { academicYearService } from "@/services";
import type { AcademicWeek, AcademicYear, WeekType } from "@/types/domain";
import { PageHeader } from "@/components/layout/page-header";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { StatusBadge } from "@/components/ui/status-badge";

export default function AcademicYearsPage() {
  const yearsQuery = useQuery({ queryKey: ["academic-years"], queryFn: () => academicYearService.listYears() });
  const [selectedId, setSelectedId] = useState("");
  const effectiveId = selectedId || yearsQuery.data?.[0]?.id || "";
  const weeksQuery = useQuery({ queryKey: ["academic-weeks", effectiveId], queryFn: () => academicYearService.listWeeks(effectiveId), enabled: Boolean(effectiveId) });
  const [editingYear, setEditingYear] = useState<AcademicYear | null>();
  const [editingWeek, setEditingWeek] = useState<AcademicWeek>();
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState("");
  const [active, setActive] = useState(false);
  const [generate, setGenerate] = useState(true);
  const [displayNumber, setDisplayNumber] = useState(1);
  const [weekType, setWeekType] = useState<WeekType>("STUDY");
  const [weekStart, setWeekStart] = useState("");
  const [weekEnd, setWeekEnd] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  if (yearsQuery.isLoading) return <LoadingSkeleton/>;
  if (yearsQuery.isError || !yearsQuery.data) return <ErrorState retry={() => yearsQuery.refetch()}/>;
  const years = yearsQuery.data;
  const selected = years.find((item) => item.id === effectiveId);

  function openYear(value?: AcademicYear) {
    setEditingYear(value ?? null);
    setName(value?.name ?? "");
    setStartDate(value?.startDate ?? "");
    setActive(value?.isActive ?? !years.some((item) => item.isActive));
    setGenerate(!value);
    setError(undefined);
  }

  function openWeek(value: AcademicWeek) {
    setEditingWeek(value);
    setDisplayNumber(value.displayNumber ?? 1);
    setWeekType(value.weekType ?? "STUDY");
    setWeekStart(value.startDate);
    setWeekEnd(value.endDate);
    setError(undefined);
  }

  async function saveYear(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(undefined);
    try {
      const saved = editingYear
        ? await academicYearService.update(editingYear.id, { name, startDate, isActive: active, version: editingYear.version })
        : await academicYearService.create({ name, startDate, isActive: active, generateWeeks: generate });
      setEditingYear(undefined); setSelectedId(saved.id); await yearsQuery.refetch(); await weeksQuery.refetch();
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể lưu năm học."); }
    finally { setBusy(false); }
  }

  async function toggleYear(value: AcademicYear) {
    setError(undefined);
    try {
      await academicYearService.update(value.id, { name: value.name, startDate: value.startDate, isActive: !value.isActive, version: value.version });
      await yearsQuery.refetch();
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể thay đổi trạng thái năm học."); }
  }

  async function generateWeeks() {
    if (!selected) return;
    setBusy(true); setError(undefined);
    try { await academicYearService.generateWeeks(selected.id); await yearsQuery.refetch(); await weeksQuery.refetch(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể sinh tuần học."); }
    finally { setBusy(false); }
  }

  async function saveWeek(event: React.FormEvent) {
    event.preventDefault(); if (!editingWeek) return;
    setBusy(true); setError(undefined);
    try {
      await academicYearService.updateWeek(editingWeek.id, { displayNumber, weekType, startDate: weekStart,
        endDate: weekEnd, version: editingWeek.version ?? 0 });
      setEditingWeek(undefined); await weeksQuery.refetch();
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể lưu tuần học."); }
    finally { setBusy(false); }
  }

  return <>
    <PageHeader eyebrow="Lịch năm học" title="Năm học và tuần học" description="Sinh 39 tuần chuẩn, sau đó điều chỉnh độc lập ngày và loại của từng tuần."/>
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap gap-2">{years.map((year) => <button key={year.id} onClick={() => setSelectedId(year.id)} className={`rounded-lg border px-3 py-2 text-left text-sm ${year.id === effectiveId ? "border-emerald-700 bg-emerald-50 text-emerald-900" : "bg-white text-slate-700"}`}><strong>{year.name}</strong><span className="ml-2 text-xs">{year.weekCount}/39 tuần</span>{year.isActive && <span className="ml-2 text-xs font-semibold text-emerald-700">Đang dùng</span>}</button>)}</div>
      <Button onClick={() => openYear()}><Plus size={16}/>Thêm năm học</Button>
    </div>
    {error && editingYear === undefined && editingWeek === undefined && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {years.length === 0 ? <div className="rounded-xl border border-dashed bg-white p-10 text-center text-sm text-slate-500">Chưa có năm học. Tạo năm đầu tiên và chọn sinh tự động 39 tuần.</div> : selected && <>
      <section className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-white p-4 shadow-sm"><div><div className="flex items-center gap-2"><h2 className="text-lg font-bold text-slate-900">{selected.name}</h2><StatusBadge status={selected.isActive ? "ACTIVE" : "INACTIVE"}/></div><p className="mt-1 text-sm text-slate-500">Bắt đầu {selected.startDate} · {selected.weekCount}/39 tuần</p></div><div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={() => openYear(selected)}><Edit3 size={15}/>Sửa</Button><Button variant="ghost" onClick={() => void toggleYear(selected)}><Power size={15}/>{selected.isActive ? "Vô hiệu hóa" : "Kích hoạt"}</Button>{selected.weekCount === 0 && <Button disabled={busy} onClick={() => void generateWeeks()}><CalendarPlus size={16}/>Sinh 39 tuần</Button>}</div></section>
      {weeksQuery.isLoading ? <LoadingSkeleton/> : weeksQuery.isError ? <ErrorState retry={() => weeksQuery.refetch()}/> : <div className="table-wrap"><table className="data-table"><thead><tr><th>Thứ tự</th><th>Loại · Số hiển thị</th><th>Khoảng ngày</th><th>Cảnh báo</th><th>Thao tác</th></tr></thead><tbody>{(weeksQuery.data ?? []).map((week) => <tr key={week.id}><td>#{week.sequenceNumber}</td><td><strong>{week.weekType === "ORIENTATION" ? "Định hướng" : "Học tập"} {week.displayNumber}</strong></td><td>{week.startDate} → {week.endDate}</td><td>{week.warnings?.includes("WEEK_OVERLAP") ? <span className="inline-flex items-center gap-1 text-xs font-semibold text-amber-700"><AlertTriangle size={14}/>Trùng khoảng ngày</span> : <span className="text-slate-400">—</span>}</td><td><Button size="sm" variant="secondary" onClick={() => openWeek(week)}><Edit3 size={14}/>Sửa</Button></td></tr>)}{(weeksQuery.data ?? []).length === 0 && <tr><td colSpan={5} className="py-10 text-center text-sm text-slate-500">Chưa sinh tuần học.</td></tr>}</tbody></table></div>}
    </>}

    <Dialog open={editingYear !== undefined} onOpenChange={(value) => !value && setEditingYear(undefined)} title={`${editingYear ? "Sửa" : "Thêm"} năm học`} description="Tên có dạng 2026-2027; hệ thống chỉ cho một năm học hoạt động."><form className="space-y-4" onSubmit={saveYear}><label><span className="field-label">Tên năm học *</span><input className="field" required pattern="\d{4}-\d{4}" placeholder="2026-2027" value={name} onChange={(event) => setName(event.target.value)}/></label><label><span className="field-label">Ngày bắt đầu *</span><input className="field" required type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)}/></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)}/>Năm học đang hoạt động</label>{!editingYear && <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={generate} onChange={(event) => setGenerate(event.target.checked)}/>Sinh ngay 39 tuần chuẩn</label>}{error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}<div className="dialog-actions"><Button type="button" variant="secondary" onClick={() => setEditingYear(undefined)}>Hủy</Button><Button type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</Button></div></form></Dialog>

    <Dialog open={editingWeek !== undefined} onOpenChange={(value) => !value && setEditingWeek(undefined)} title={`Sửa tuần #${editingWeek?.sequenceNumber ?? ""}`} description="Thứ tự nội bộ không thay đổi. Khoảng ngày trùng tuần khác sẽ được lưu kèm cảnh báo."><form className="space-y-4" onSubmit={saveWeek}><div className="grid gap-4 sm:grid-cols-2"><label><span className="field-label">Loại tuần *</span><select className="field" value={weekType} onChange={(event) => setWeekType(event.target.value as WeekType)}><option value="ORIENTATION">Định hướng</option><option value="STUDY">Học tập</option></select></label><label><span className="field-label">Số hiển thị *</span><input className="field" type="number" min={1} required value={displayNumber} onChange={(event) => setDisplayNumber(Number(event.target.value))}/></label></div><div className="grid gap-4 sm:grid-cols-2"><label><span className="field-label">Ngày bắt đầu *</span><input className="field" type="date" required value={weekStart} onChange={(event) => setWeekStart(event.target.value)}/></label><label><span className="field-label">Ngày kết thúc *</span><input className="field" type="date" required value={weekEnd} onChange={(event) => setWeekEnd(event.target.value)}/></label></div>{error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}<div className="dialog-actions"><Button type="button" variant="secondary" onClick={() => setEditingWeek(undefined)}>Hủy</Button><Button type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</Button></div></form></Dialog>
  </>;
}
