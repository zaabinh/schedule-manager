"use client";

import { BellRing, LockKeyhole, Trash2 } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { reminderService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";
import { formatDateTime } from "@/lib/utils";

export default function RemindersPage() { const client = useQueryClient(); const query = useQuery({ queryKey: ["reminders", "mine"], queryFn: () => reminderService.listMine() }); if (query.isLoading) return <LoadingSkeleton/>; if (query.isError) return <ErrorState retry={() => query.refetch()}/>; async function cancel(id: string) { await reminderService.cancel(id); await client.invalidateQueries({ queryKey: ["reminders"] }); } return <><PageHeader eyebrow="Email nhắc lịch" title="Nhắc lịch" description="Reminder của nhà trường và reminder cá nhân được quản lý độc lập."/>{query.data?.length ? <div className="grid gap-4 lg:grid-cols-2">{query.data.map((item) => <article key={item.id} className="card p-5"><div className="flex items-start gap-3"><span className="rounded-xl bg-[var(--primary-soft)] p-2.5 text-[var(--primary)]"><BellRing/></span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center justify-between gap-2"><h2 className="font-bold">{item.eventTitle}</h2><span className="rounded-full border px-2.5 py-1 text-xs font-bold">{item.source === "ADMIN" ? "Nhắc lịch của Admin" : "Cá nhân"}</span></div><p className="mt-2 text-sm text-slate-500">Gửi lúc <strong className="text-slate-700">{formatDateTime(item.remindAt)}</strong></p>{item.source === "ADMIN" ? <p className="mt-4 flex items-center gap-2 text-xs text-slate-500"><LockKeyhole size={14}/>Bạn không thể xóa reminder của Admin.</p> : <Button className="mt-4" size="sm" variant="secondary" onClick={() => cancel(item.id)}><Trash2 size={15}/>Xóa nhắc lịch</Button>}</div></div></article>)}</div> : <EmptyState title="Chưa có nhắc lịch" description="Mở một sự kiện trong kế hoạch tuần để tạo nhắc lịch qua email."/>}</>; }
