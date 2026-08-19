"use client";

import * as Popover from "@radix-ui/react-popover";
import { Bell } from "lucide-react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationService } from "@/services";
import { NotificationItem } from "./notification-item";

export function NotificationPopover({ admin = false }: { admin?: boolean }) {
  const queryClient = useQueryClient();
  const { data = [] } = useQuery({ queryKey: ["notifications"], queryFn: () => notificationService.list() });
  const unread = data.filter((item) => !item.readAt).length;
  async function markAll() { await notificationService.markAllRead(); await queryClient.invalidateQueries({ queryKey: ["notifications"] }); }
  async function markOne(id: string) { await notificationService.markRead(id); await queryClient.invalidateQueries({ queryKey: ["notifications"] }); }
  return <Popover.Root><Popover.Trigger aria-label={`Thông báo, ${unread} chưa đọc`} className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"><Bell size={20} />{unread > 0 && <span className="absolute right-0 top-0 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">{unread}</span>}</Popover.Trigger><Popover.Portal><Popover.Content align="end" sideOffset={10} className="z-50 w-[min(92vw,380px)] overflow-hidden rounded-2xl border bg-white shadow-xl"><div className="flex items-center justify-between p-4"><div><p className="font-bold">Thông báo</p><p className="text-xs text-slate-500">{unread} thông báo chưa đọc</p></div><button onClick={markAll} className="text-xs font-semibold text-[var(--primary)] hover:underline">Đánh dấu tất cả đã đọc</button></div><div className="max-h-96 overflow-y-auto">{data.slice(0, 3).map((item) => <NotificationItem key={item.id} item={item} compact onRead={markOne} />)}</div><Link href={admin ? "/admin/notifications" : "/notifications"} className="block border-t p-3 text-center text-sm font-semibold text-[var(--primary)] hover:bg-slate-50">Xem tất cả</Link><Popover.Arrow className="fill-white" /></Popover.Content></Popover.Portal></Popover.Root>;
}
