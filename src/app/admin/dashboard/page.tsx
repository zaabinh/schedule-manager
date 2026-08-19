"use client";
import { AlertCircle, ArrowRight, MessageSquareText, UserCheck, UsersRound } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { dashboardService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";

export default function AdminDashboard() {
  const query = useQuery({ queryKey: ["admin-dashboard"], queryFn: () => dashboardService.admin() });
  if (query.isLoading) return <LoadingSkeleton rows={5}/>;
  if (query.isError || !query.data) return <ErrorState retry={() => query.refetch()}/>;
  const { currentPlan, needsAttention } = query.data;
  const cards = [["Tài khoản chờ duyệt",needsAttention.pendingUsers,"/admin/users?status=PENDING",<UserCheck key="u"/>],["Trao đổi đang mở",needsAttention.openConversations,"/admin/conversations",<MessageSquareText key="c"/>],["Nhiệm vụ chưa xong",needsAttention.incompleteTasks,"/admin/tasks",<UsersRound key="t"/>],["Kế hoạch chưa công bố",needsAttention.unpublishedPlans,"/admin/weekly-plans",<AlertCircle key="p"/>]] as const;
  return <><PageHeader eyebrow="Quản trị" title="Tổng quan quản trị" description="Dữ liệu cần xử lý được tổng hợp trực tiếp từ hệ thống."/>{currentPlan ? <section className="mb-7 rounded-2xl bg-[var(--sidebar)] p-6 text-white"><p className="eyebrow !text-emerald-100/60">Kế hoạch gần hiện tại</p><div className="mt-2 flex items-center gap-3"><h2 className="text-3xl font-bold">{currentPlan.displayLabel}</h2><StatusBadge status={currentPlan.status}/></div><Link className="mt-5 inline-flex items-center gap-2 font-semibold" href={`/admin/weekly-plans/${currentPlan.weekId}/edit`}>Mở kế hoạch<ArrowRight size={16}/></Link></section> : <EmptyState title="Chưa có kế hoạch" description="Hãy tạo kế hoạch tuần đầu tiên."/>}<section><h2 className="mb-4 text-lg font-bold">Cần xử lý</h2><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards.map(([label,value,href,icon]) => <Link href={href} key={label} className="card p-5"><span className="text-[var(--primary)]">{icon}</span><strong className="mt-4 block text-3xl">{value}</strong><span className="text-sm text-slate-500">{label}</span></Link>)}</div></section></>;
}
