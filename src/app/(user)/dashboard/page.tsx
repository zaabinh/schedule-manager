"use client";

import { AlertTriangle, ArrowRight, Bell, CalendarClock, CalendarDays, ClipboardList, ListTodo, School, Sparkles } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { authService, dashboardService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { ErrorState, LoadingSkeleton } from "@/components/ui/states";
import type { DaySession } from "@/types/domain";

export default function UserDashboard() {
  const query = useQuery({
    queryKey: ["user-dashboard"],
    queryFn: async () => ({ user: await authService.getCurrentUser(), dashboard: await dashboardService.me() }),
  });
  if (query.isLoading) return <LoadingSkeleton rows={5}/>;
  if (query.isError || !query.data) return <ErrorState retry={() => query.refetch()}/>;

  const { user, dashboard } = query.data;
  const plan = dashboard.weeklyPlan;
  const todaySessions = dashboard.today?.sessions ?? [];
  const todayScheduleCount = todaySessions.reduce((count, session) =>
    count + session.events.length + (session.baseContent.trim() ? 1 : 0), 0);
  const summary = dashboard.taskSummary;

  return <>
    <PageHeader eyebrow="Cổng thông tin giáo viên" title={`Xin chào, ${user.name}`} description={[user.department, ...user.businessRoles].filter(Boolean).join(" · ")} />

    <section aria-label="Tổng quan nhanh" className="mb-6 grid grid-cols-2 gap-3 xl:grid-cols-4">
      <SummaryCard icon={ClipboardList} label="Tổng nhiệm vụ" value={summary.total} href="/assignments" tone="primary"/>
      <SummaryCard icon={ListTodo} label="Chưa hoàn thành" value={summary.incomplete} href="/assignments" tone="warning"/>
      <SummaryCard icon={AlertTriangle} label="Quá hạn" value={summary.overdue} href="/assignments" tone="danger"/>
      <SummaryCard icon={CalendarClock} label="Lịch trình hôm nay" value={todayScheduleCount} href="/weekly-plan" tone="neutral"/>
    </section>

    {plan ? <section className="card mb-6 flex flex-col justify-between gap-4 p-5 sm:flex-row sm:items-center sm:p-6">
      <div><p className="eyebrow">Kế hoạch tuần hiện tại</p><h2 className="mt-1 text-2xl font-bold">{plan.displayLabel}</h2><p className="mt-1 text-sm text-slate-500">{plan.startDate} – {plan.endDate}</p></div>
      <Link href="/weekly-plan" className="inline-flex items-center gap-2 self-start rounded-lg bg-[var(--primary-soft)] px-4 py-2 text-sm font-bold text-[var(--primary-strong)]">Xem kế hoạch <ArrowRight size={16}/></Link>
    </section> : <NoPlanCard/>}

    <section className="mb-7 rounded-2xl border border-emerald-200 bg-[var(--primary-soft)] p-5 sm:p-7">
      <div className="mb-5 flex items-center gap-3"><Sparkles className="text-[var(--primary)]"/><h2 className="text-xl font-bold">Dành cho bạn</h2></div>
      {dashboard.relevantToMe.length ? (
        <div className="grid gap-3 lg:grid-cols-3">{dashboard.relevantToMe.map((item) => <Link href={item.deepLink} key={`${item.kind}-${item.entityId}`} className="rounded-xl border border-emerald-100 bg-white p-4 transition-shadow hover:shadow-sm"><div className="mb-2 flex items-center gap-2 text-[var(--primary)]"><School size={18}/><strong className="text-xs uppercase">{item.title}</strong></div><p className="text-sm leading-6 text-slate-700">{item.content}</p><small className="mt-2 block text-slate-400">Khớp: {item.matchedBy.join(", ")}</small></Link>)}</div>
      ) : (
        <EmptyDashboardMessage text={plan ? "Chưa có phân công riêng trong tuần này." : "Chưa có kế hoạch."}/>
      )}
    </section>

    <section>
      <div className="mb-3 flex items-center justify-between"><h2 className="text-lg font-bold">Lịch trình hôm nay</h2>{plan && <Link className="text-sm font-semibold text-[var(--primary)]" href="/weekly-plan">Xem kế hoạch</Link>}</div>
      {todaySessions.length ? <div className="grid gap-4 lg:grid-cols-2">{todaySessions.map((session) => <TodaySession key={session.session} session={session}/>)}</div> : <div className="card p-6"><EmptyDashboardMessage text={plan ? "Chưa có lịch trình hôm nay." : "Chưa có kế hoạch."}/></div>}
    </section>

    <section className="mt-6 grid gap-3 sm:grid-cols-2">
      <Link href="/assignments" className="flex items-center justify-between rounded-xl bg-[var(--sidebar)] p-5 text-white"><span className="flex items-center gap-3"><ClipboardList/><strong>Xem nhiệm vụ của tôi</strong></span><ArrowRight/></Link>
      <Link href="/notifications" className="flex items-center justify-between rounded-xl border bg-white p-5 text-slate-800"><span className="flex items-center gap-3"><Bell className="text-[var(--primary)]"/><strong>{dashboard.notificationSummary.unreadCount} thông báo chưa đọc</strong></span><ArrowRight/></Link>
    </section>
  </>;
}

type IconComponent = typeof ClipboardList;

function SummaryCard({ icon: Icon, label, value, href, tone }: { icon: IconComponent; label: string; value: number; href: string; tone: "primary" | "warning" | "danger" | "neutral" }) {
  const tones = {
    primary: "bg-emerald-50 text-emerald-700",
    warning: "bg-amber-50 text-amber-700",
    danger: "bg-red-50 text-red-700",
    neutral: "bg-slate-100 text-slate-600",
  };
  return <Link href={href} className="card group flex min-h-28 items-center gap-3 p-4 transition-shadow hover:shadow-md sm:p-5"><span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${tones[tone]}`}><Icon size={20}/></span><span className="min-w-0"><strong className="block text-2xl leading-none text-slate-900">{value}</strong><span className="mt-1 block text-xs font-semibold leading-snug text-slate-500 group-hover:text-slate-700">{label}</span></span></Link>;
}

function NoPlanCard() {
  return <section className="card mb-6 flex items-center gap-4 border-dashed p-6"><span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-slate-100 text-slate-500"><CalendarDays/></span><div><p className="font-bold text-slate-800">Chưa có kế hoạch</p><p className="mt-1 text-sm text-slate-500">Kế hoạch tuần sẽ xuất hiện tại đây sau khi được công bố.</p></div></section>;
}

function EmptyDashboardMessage({ text }: { text: string }) {
  return <div className="rounded-xl border border-dashed bg-white/70 px-5 py-7 text-center text-sm font-medium text-slate-500">{text}</div>;
}

function TodaySession({ session }: { session: DaySession }) {
  const hasContent = Boolean(session.baseContent.trim() || session.events.length);
  return <article className="card overflow-hidden"><header className="border-b bg-slate-50 px-4 py-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-600">{session.session === "MORNING" ? "Buổi sáng" : "Buổi chiều"}</p></header>{hasContent ? <div className="p-4">{session.baseContent.trim() && <p className="mb-3 text-sm font-semibold text-slate-700">{session.baseContent}</p>}{session.events.length > 0 && <ul className="space-y-2">{session.events.map((event) => <li key={event.id} className="flex gap-3 rounded-lg bg-[var(--primary-soft)] p-3"><time className="w-11 shrink-0 text-sm font-bold text-[var(--primary)]">{event.startTime ?? "—"}</time><div className="min-w-0"><p className="font-semibold text-slate-800">{event.content}</p><p className="mt-0.5 text-xs text-slate-500">{event.location ?? "Theo kế hoạch tuần"}</p></div></li>)}</ul>}</div> : <p className="p-5 text-sm text-slate-500">Chưa có lịch trình.</p>}</article>;
}
