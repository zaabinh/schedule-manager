"use client";

import { Plus, Save, Send, School, Trash2 } from "lucide-react";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { weeklyPlanService, type PlanValidation } from "@/services";
import type { EventItem, WeeklyPlan } from "@/types/domain";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { LoadingSkeleton, ErrorState } from "@/components/ui/states";
import { TargetSelector } from "./target-selector";
import { EventFormDialog } from "./event-form-dialog";
import { PublishReviewDialog } from "./publish-review-dialog";
import { PublishedPlanUpdateDialog } from "./published-plan-update-dialog";
import { SearchableSelect } from "@/components/ui/searchable-select";

export function WeeklyPlanEditor({ weekId }: { weekId: string }) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ["weekly-plan", weekId], queryFn: () => weeklyPlanService.getByWeekId(weekId) });
  const optionsQuery = useQuery({ queryKey: ["weekly-plan-options", query.data?.id], queryFn: () => weeklyPlanService.options(query.data!.id), enabled: Boolean(query.data?.id) });
  const [draft, setDraft] = useState<WeeklyPlan>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [review, setReview] = useState<PlanValidation>();
  const [reviewOpen, setReviewOpen] = useState(false);
  const [publishedSaveOpen, setPublishedSaveOpen] = useState(false);
  const [notificationAction, setNotificationAction] = useState<((website: boolean, email: boolean) => Promise<void>)>();
  const [eventDate, setEventDate] = useState<string>();
  const value = draft ?? query.data;
  if (query.isLoading || optionsQuery.isLoading) return <LoadingSkeleton rows={7}/>;
  if (query.isError || optionsQuery.isError || !value || !optionsQuery.data) return <ErrorState retry={() => { query.refetch(); optionsQuery.refetch(); }}/>;
  const options = optionsQuery.data;

  function update(transform: (copy: WeeklyPlan) => void) {
    const next = structuredClone(value!); transform(next); setDraft(next);
  }
  async function save() {
    if (value!.status === "PUBLISHED") { setPublishedSaveOpen(true); return; }
    setBusy(true); setError(undefined);
    try {
      const saved = await weeklyPlanService.updateDraft(value!);
      setDraft(undefined); client.setQueryData(["weekly-plan", weekId], saved);
      await client.invalidateQueries({ queryKey: ["weekly-plan-weeks"] });
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể lưu kế hoạch tuần."); }
    finally { setBusy(false); }
  }
  async function savePublished(website: boolean, email: boolean) {
    setPublishedSaveOpen(false); setBusy(true); setError(undefined);
    try { const saved = await weeklyPlanService.updatePublished(value!, website, email); setDraft(undefined); client.setQueryData(["weekly-plan", weekId], saved); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể lưu kế hoạch đã công bố."); }
    finally { setBusy(false); }
  }
  async function reviewPublish() {
    if (draft) { setError("Vui lòng lưu các thay đổi DRAFT trước khi công bố."); return; }
    setBusy(true); setError(undefined);
    try { setReview(await weeklyPlanService.validate(value!.id)); setReviewOpen(true); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể kiểm tra kế hoạch."); }
    finally { setBusy(false); }
  }
  async function publish() {
    setBusy(true); setError(undefined);
    try { const saved = await weeklyPlanService.publish(value!.id, value!.version, Boolean(review?.warnings.length), crypto.randomUUID()); setReviewOpen(false); setDraft(undefined); client.setQueryData(["weekly-plan", weekId], saved); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể công bố kế hoạch."); }
    finally { setBusy(false); }
  }
  async function addEvent(event: Omit<EventItem, "id" | "version">) {
    if (value!.status === "PUBLISHED") { setEventDate(undefined); setNotificationAction(() => async (website: boolean, email: boolean) => { await weeklyPlanService.addEvent(value!.id, event, website, email); await query.refetch(); }); setPublishedSaveOpen(true); return; }
    try { await weeklyPlanService.addEvent(value!.id, event); setEventDate(undefined); await query.refetch(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể thêm sự kiện."); }
  }
  async function removeEvent(event: EventItem) {
    if (!window.confirm(`Xóa sự kiện “${event.content}”?`)) return;
    if (value!.status === "PUBLISHED") { setNotificationAction(() => async (website: boolean, email: boolean) => { await weeklyPlanService.deleteEvent(event, website, email); await query.refetch(); }); setPublishedSaveOpen(true); return; }
    try { await weeklyPlanService.deleteEvent(event); await query.refetch(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Không thể xóa sự kiện."); }
  }

  return <><div className="sticky top-16 z-10 -mx-4 mb-12 border-b bg-[var(--background)]/95 px-4 py-4 backdrop-blur sm:-mx-6 sm:px-6"><div className="flex flex-col justify-between gap-4 xl:flex-row xl:items-center"><div><div className="flex items-center gap-3"><h1 className="text-2xl font-bold">Kế hoạch {value.displayLabel.toLowerCase()}</h1><StatusBadge status={value.status}/></div><p className="mt-1 text-sm text-slate-500">{value.startDate} – {value.endDate} · Phiên bản {value.version}{draft && " · Có thay đổi chưa lưu"}</p></div><div className="flex gap-2"><Button type="button" variant="secondary" onClick={() => void save()} disabled={busy}><Save size={17}/>{busy ? "Đang xử lý…" : "Lưu"}</Button>{value.status === "DRAFT" && <Button type="button" onClick={() => void reviewPublish()} disabled={busy}><Send size={17}/>Kiểm tra & công bố</Button>}</div></div></div>
  {error && <p role="alert" className="mb-5 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
  <section className="mb-8 pt-1"><h2 className="mb-4 text-lg font-bold leading-tight">Phân công chung</h2><div className="grid gap-4 xl:grid-cols-2">{value.sections.map((section, index) => <article className="card p-5" key={section.id}><p className="eyebrow">Mục {index + 1}</p><h3 className="mt-1 font-bold">{section.title}</h3><label className="mt-4 block"><span className="field-label">Nội dung</span><textarea className="field min-h-28" maxLength={20000} value={section.content} onChange={(event) => update((copy) => { copy.sections[index].content = event.target.value; })}/></label><div className="mt-4"><TargetSelector value={section.targets} options={options} onChange={(targets) => update((copy) => { copy.sections[index].targets = targets; })}/></div></article>)}</div></section>
  <section className="card mb-8 p-5"><div><h2 className="text-lg font-bold">Lớp trực</h2><p className="mt-1 text-sm text-slate-500">Chọn nhanh bằng cách nhập tên lớp.</p></div><div className="mt-4 grid gap-4 sm:grid-cols-2"><div><span className="field-label">Buổi sáng</span><SearchableSelect ariaLabel="Lớp trực buổi sáng" value={value.morningDutyClassId ?? ""} placeholder="Chưa chọn" searchPlaceholder="Tìm lớp…" clearable options={options.dutyClasses.map((item) => ({ value: item.id, label: item.name }))} onValueChange={(selectedId) => update((copy) => { const selected = options.dutyClasses.find((item) => item.id === selectedId); copy.morningDutyClassId = selected?.id; copy.morningDutyClass = selected?.name; })}/></div><div><span className="field-label">Buổi chiều</span><SearchableSelect ariaLabel="Lớp trực buổi chiều" value={value.afternoonDutyClassId ?? ""} placeholder="Chưa chọn" searchPlaceholder="Tìm lớp…" clearable options={options.dutyClasses.map((item) => ({ value: item.id, label: item.name }))} onValueChange={(selectedId) => update((copy) => { const selected = options.dutyClasses.find((item) => item.id === selectedId); copy.afternoonDutyClassId = selected?.id; copy.afternoonDutyClass = selected?.name; })}/></div></div></section>
  <section><h2 className="mb-4 text-lg font-bold">Lịch tuần</h2><div className="space-y-4">{value.days.map((day, dayIndex) => <article className="card overflow-hidden" key={day.date}><header className="flex items-center justify-between border-b bg-slate-50 p-4"><div><p className="font-bold uppercase">{day.dayLabel}</p><p className="text-xs text-slate-500">{day.date}</p></div><School className="text-slate-300"/></header><div className="grid md:grid-cols-2 md:divide-x">{day.sessions.map((session, sessionIndex) => <section className="p-4" key={session.session}><div className="flex items-center justify-between"><p className="eyebrow">{session.session === "MORNING" ? "Sáng" : "Chiều"}</p><Button size="sm" variant="ghost" onClick={() => setEventDate(day.date)}><Plus size={14}/>Sự kiện</Button></div><label className="mt-3 block"><span className="field-label">Nội dung chính</span><textarea className="field min-h-20" maxLength={20000} value={session.baseContent} onChange={(event) => update((copy) => { copy.days[dayIndex].sessions[sessionIndex].baseContent = event.target.value; })}/></label>{session.events.length > 0 && <ul className="mt-3 space-y-2">{session.events.map((item) => <li className="flex items-start justify-between gap-2 rounded-lg bg-[var(--primary-soft)] p-2 text-sm" key={item.id}><span><strong>{item.startTime && `${item.startTime} · `}{item.content}</strong>{item.location && <small className="block text-slate-500">{item.location}</small>}</span><button aria-label={`Xóa ${item.content}`} className="p-1 text-red-700" onClick={() => void removeEvent(item)}><Trash2 size={15}/></button></li>)}</ul>}</section>)}</div></article>)}</div></section>
  <EventFormDialog open={Boolean(eventDate)} onOpenChange={(open) => !open && setEventDate(undefined)} defaultDate={eventDate} onSubmit={addEvent}/>
  <PublishReviewDialog open={reviewOpen} onOpenChange={setReviewOpen} review={review} busy={busy} onPublish={() => void publish()}/>
  <PublishedPlanUpdateDialog open={publishedSaveOpen} onOpenChange={(open) => { setPublishedSaveOpen(open); if (!open) setNotificationAction(undefined); }} onSave={(website, email) => { if (notificationAction) { setPublishedSaveOpen(false); setBusy(true); setError(undefined); void notificationAction(website, email).catch((caught) => setError(caught instanceof Error ? caught.message : "Không thể cập nhật sự kiện.")).finally(() => { setBusy(false); setNotificationAction(undefined); }); } else void savePublished(website, email); }}/></>;
}
