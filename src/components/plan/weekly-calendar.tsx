import type { WeeklyPlan } from "@/types/domain";
import { EventDetailDialog } from "./event-detail-dialog";

export function WeeklyCalendar({ value }: { value: WeeklyPlan }) {
  return <div className="overflow-x-auto pb-2">
    <div className="grid min-w-[980px] grid-cols-[88px_repeat(7,minmax(126px,1fr))] overflow-hidden rounded-2xl border bg-white shadow-sm">
      <div className="bg-slate-50" />
      {value.days.map((day) => <div key={day.date} className="border-l bg-slate-50 px-2 py-3 text-center"><p className="text-sm font-extrabold text-slate-900">{day.dayLabel}</p><p className="mt-0.5 text-xs font-medium text-slate-600">{day.date.slice(8, 10)}/{day.date.slice(5, 7)}</p></div>)}
      {(["MORNING", "AFTERNOON"] as const).map((sessionName) => <div className="contents" key={sessionName}>
        <div className="border-t bg-slate-50 px-2 py-4 text-xs font-extrabold uppercase tracking-wide text-slate-700">{sessionName === "MORNING" ? "Sáng" : "Chiều"}</div>
        {value.days.map((day) => { const session = day.sessions.find((item) => item.session === sessionName)!; return <div key={`${day.date}-${sessionName}`} className="min-h-28 space-y-2 border-l border-t p-2.5"><p className="line-clamp-3 text-[13px] font-medium leading-5 text-slate-700">{session.baseContent || "Chưa có nội dung"}</p>{session.events.map((event) => <EventDetailDialog key={event.id} event={event} trigger={<button className="w-full rounded-lg border-l-3 border-l-[var(--primary)] bg-[var(--primary-soft)] px-2 py-2 text-left text-[13px] font-bold leading-[1.15rem] text-[var(--primary-strong)] transition hover:bg-emerald-100">{event.startTime && <span className="mb-0.5 block text-xs opacity-80">{event.startTime}</span>}{event.content}</button>} />)}</div>; })}
      </div>)}
    </div>
  </div>;
}
