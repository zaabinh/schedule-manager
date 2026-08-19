import type { DaySession, WeeklyPlan } from "@/types/domain";
import { EventDetailDialog } from "./event-detail-dialog";

export function WeeklyPlanTable({ value }: { value: WeeklyPlan }) {
  return <div className="weekly-plan-sheet overflow-hidden rounded-xl border bg-white">
    <div className="overflow-x-auto">
      <table className="weekly-plan-table min-w-[760px]" aria-label={`Lịch ${value.displayLabel.toLowerCase()}`}>
        <thead><tr><th className="day-column" scope="col">Thứ / ngày</th><th className="session-column" scope="col">Sáng</th><th className="session-column" scope="col">Chiều</th></tr></thead>
        <tbody>{value.days.map((day) => <tr key={day.date}>
          <th className="day-column bg-slate-50 p-3" scope="row"><span className="block text-sm font-bold normal-case tracking-normal text-slate-900">{day.dayLabel}</span><time className="mt-1 block text-xs font-medium normal-case tracking-normal text-slate-500" dateTime={day.date}>{day.date.slice(8, 10)}/{day.date.slice(5, 7)}</time></th>
          <SessionCell session={day.sessions.find((item) => item.session === "MORNING")} />
          <SessionCell session={day.sessions.find((item) => item.session === "AFTERNOON")} />
        </tr>)}</tbody>
      </table>
    </div>
  </div>;
}

function SessionCell({ session }: { session?: DaySession }) {
  if (!session) return <td><span className="text-slate-400">Chưa có nội dung</span></td>;
  return <td><p className="font-semibold text-slate-700">{session.baseContent || "Chưa có nội dung chính"}</p>{session.events.length > 0 && <ul className="mt-2 space-y-1.5">{session.events.map((event) => <li key={event.id}><EventDetailDialog event={event} trigger={<button className="event-row block w-full rounded-md border-l-3 border-l-[var(--primary)] bg-[var(--primary-soft)] px-2 py-1.5 text-left text-xs text-[var(--primary-strong)] hover:bg-emerald-100"><span className="font-bold">{event.startTime && `${event.startTime} · `}{event.content}</span>{event.location && <span className="ml-1 text-[11px] opacity-75">— {event.location}</span>}</button>}/></li>)}</ul>}</td>;
}
