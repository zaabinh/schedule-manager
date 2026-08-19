import type { WeeklyPlan } from "@/types/domain";
import { EventDetailDialog } from "./event-detail-dialog";

export function WeeklyCalendar({ value }: { value: WeeklyPlan }) {
  return <div className="overflow-x-auto pb-2">
    <div className="grid min-w-[820px] grid-cols-[72px_repeat(7,minmax(104px,1fr))] overflow-hidden rounded-xl border bg-white">
      <div className="bg-slate-50" />
      {value.days.map((day) => <div key={day.date} className="border-l bg-slate-50 px-2 py-2.5 text-center"><p className="text-xs font-bold">{day.dayLabel}</p><p className="text-[11px] text-slate-500">{day.date.slice(8, 10)}/{day.date.slice(5, 7)}</p></div>)}
      {(["MORNING", "AFTERNOON"] as const).map((sessionName) => <div className="contents" key={sessionName}>
        <div className="border-t bg-slate-50 px-2 py-3 text-[11px] font-bold uppercase text-slate-500">{sessionName === "MORNING" ? "Sáng" : "Chiều"}</div>
        {value.days.map((day) => { const session = day.sessions.find((item) => item.session === sessionName)!; return <div key={`${day.date}-${sessionName}`} className="min-h-28 space-y-1.5 border-l border-t p-1.5"><p className="line-clamp-2 text-[11px] leading-4 text-slate-500">{session.baseContent || "—"}</p>{session.events.map((event) => <EventDetailDialog key={event.id} event={event} trigger={<button className="w-full rounded-md bg-[var(--primary-soft)] px-1.5 py-1.5 text-left text-[11px] font-semibold leading-4 text-[var(--primary-strong)] hover:bg-emerald-100">{event.startTime && <span className="block text-[10px] opacity-70">{event.startTime}</span>}{event.content}</button>} />)}</div>; })}
      </div>)}
    </div>
  </div>;
}
