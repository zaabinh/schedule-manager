"use client";
import { ArrowRight, CalendarDays, School, Sparkles } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { authService, dashboardService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";

export default function UserDashboard() {
  const query = useQuery({ queryKey: ["user-dashboard"], queryFn: async () => ({ user: await authService.getCurrentUser(), dashboard: await dashboardService.me() }) });
  if (query.isLoading) return <LoadingSkeleton rows={5}/>;
  if (query.isError || !query.data) return <ErrorState retry={() => query.refetch()}/>;
  const { user, dashboard } = query.data; const plan = dashboard.weeklyPlan;
  const todayEvents = dashboard.today?.sessions.flatMap((session) => session.events) ?? [];
  return <><PageHeader eyebrow="Cổng thông tin giáo viên" title={`Xin chào, ${user.name}`} description={[user.department, ...user.businessRoles].filter(Boolean).join(" · ")} />
    <section className="card mb-6 p-5 sm:p-6"><p className="eyebrow">Tuần hiện tại</p><h2 className="mt-1 text-2xl font-bold">{plan.displayLabel}</h2><p className="mt-1 text-sm text-slate-500">{plan.startDate} – {plan.endDate}</p></section>
    <section className="mb-7 rounded-2xl border border-emerald-200 bg-[var(--primary-soft)] p-5 sm:p-7"><div className="mb-5 flex items-center gap-3"><Sparkles className="text-[var(--primary)]"/><h2 className="text-xl font-bold">Dành cho bạn</h2></div>{dashboard.relevantToMe.length ? <div className="grid gap-3 lg:grid-cols-3">{dashboard.relevantToMe.map((item) => <Link href={item.deepLink} key={`${item.kind}-${item.entityId}`} className="rounded-xl border border-emerald-100 bg-white p-4"><div className="mb-2 flex items-center gap-2 text-[var(--primary)]"><School size={18}/><strong className="text-xs uppercase">{item.title}</strong></div><p className="text-sm leading-6 text-slate-700">{item.content}</p><small className="mt-2 block text-slate-400">Khớp: {item.matchedBy.join(", ")}</small></Link>)}</div> : <EmptyState title="Không có phân công riêng" description="Các nội dung chung vẫn có trong kế hoạch tuần."/>}</section>
    <section><div className="mb-3 flex items-center justify-between"><h2 className="text-lg font-bold">Hôm nay</h2><Link className="text-sm font-semibold text-[var(--primary)]" href="/weekly-plan">Xem kế hoạch</Link></div><div className="card divide-y">{todayEvents.length ? todayEvents.map((event) => <div key={event.id} className="flex gap-4 p-4"><time className="w-12 font-bold text-[var(--primary)]">{event.startTime ?? "—"}</time><div><p className="font-semibold">{event.content}</p><p className="text-xs text-slate-500">{event.location ?? "Theo kế hoạch tuần"}</p></div></div>) : <p className="p-5 text-sm text-slate-500">Không có sự kiện trong hôm nay.</p>}</div></section>
    <Link href="/weekly-plan" className="mt-6 flex items-center justify-between rounded-xl bg-[var(--sidebar)] p-5 text-white"><span className="flex items-center gap-3"><CalendarDays/><strong>Kế hoạch tuần đầy đủ</strong></span><ArrowRight/></Link></>;
}
