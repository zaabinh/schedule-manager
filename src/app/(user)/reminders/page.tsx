"use client";

import { useState } from "react";
import { BellRing, LockKeyhole, Trash2 } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { reminderService } from "@/services";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { LoadingSkeleton, ErrorState, EmptyState } from "@/components/ui/states";
import { formatDateTime } from "@/lib/utils";
import type { Reminder } from "@/types/domain";

const queryKey = ["reminders", "mine"] as const;

const statusLabels: Record<Reminder["status"], string> = {
  PENDING: "Đang chờ",
  PROCESSING: "Đang gửi",
  SENT: "Đã gửi",
  FAILED: "Gửi thất bại",
  CANCELLED: "Đã xóa",
};

export default function RemindersPage() {
  const client = useQueryClient();
  const [deletingId, setDeletingId] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const query = useQuery({ queryKey, queryFn: () => reminderService.listMine() });

  if (query.isLoading) return <LoadingSkeleton />;
  if (query.isError) return <ErrorState retry={() => query.refetch()} />;

  const reminders = (query.data ?? []).filter((item) => item.status !== "CANCELLED");

  async function cancel(id: string) {
    setDeletingId(id);
    setActionError(undefined);
    try {
      await reminderService.cancel(id);
      client.setQueryData<Reminder[]>(queryKey, (current = []) => current.filter((item) => item.id !== id));
      await client.invalidateQueries({ queryKey });
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : "Không thể xóa nhắc lịch. Vui lòng thử lại.");
    } finally {
      setDeletingId(undefined);
    }
  }

  return <>
    <PageHeader
      eyebrow="Email nhắc lịch"
      title="Nhắc lịch"
      description="Nhắc lịch của nhà trường và nhắc lịch cá nhân được quản lý độc lập."
    />
    {actionError && <p role="alert" className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{actionError}</p>}
    {reminders.length ? <div className="grid gap-4 lg:grid-cols-2">
      {reminders.map((item) => <article key={item.id} className="card p-5">
        <div className="flex items-start gap-3">
          <span className="rounded-xl bg-[var(--primary-soft)] p-2.5 text-[var(--primary)]"><BellRing /></span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <h2 className="font-bold">{item.eventTitle}</h2>
              <div className="flex flex-wrap justify-end gap-1.5">
                <span className="rounded-full border px-2.5 py-1 text-xs font-bold">{item.source === "ADMIN" ? "Nhắc lịch của Admin" : "Cá nhân"}</span>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">{statusLabels[item.status]}</span>
              </div>
            </div>
            <p className="mt-2 text-sm text-slate-500">Gửi lúc <strong className="text-slate-700">{formatDateTime(item.remindAt)}</strong></p>
            {item.source === "ADMIN"
              ? <p className="mt-4 flex items-center gap-2 text-xs text-slate-500"><LockKeyhole size={14} />Bạn không thể xóa nhắc lịch của Admin.</p>
              : item.status === "PENDING" && <Button type="button" className="mt-4" size="sm" variant="secondary" disabled={deletingId === item.id} onClick={() => void cancel(item.id)}>
                <Trash2 size={15} />{deletingId === item.id ? "Đang xóa..." : "Xóa nhắc lịch"}
              </Button>}
          </div>
        </div>
      </article>)}
    </div> : <EmptyState title="Chưa có nhắc lịch" description="Mở một sự kiện trong kế hoạch tuần để tạo nhắc lịch qua email." />}
  </>;
}
