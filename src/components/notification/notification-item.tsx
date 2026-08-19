"use client";

import { formatDistanceToNow } from "date-fns";
import { vi } from "date-fns/locale";
import type { Notification } from "@/types/domain";
import { cn } from "@/lib/utils";

export function NotificationItem({ item, compact = false, onRead }: { item: Notification; compact?: boolean; onRead?: (id: string) => void }) {
  return <button onClick={() => onRead?.(item.id)} className={cn("block w-full rounded-xl border p-4 text-left hover:bg-slate-50", !item.readAt && "border-emerald-200 bg-emerald-50/45", compact && "rounded-none border-x-0 border-b-0 px-4 py-3")}>
    <div className="flex gap-3"><span aria-hidden className={cn("mt-1 h-2 w-2 shrink-0 rounded-full", item.readAt ? "bg-transparent" : "bg-[var(--primary)]")} /><span><span className="block text-sm font-semibold text-slate-900">{item.title}</span>{!compact && <span className="mt-1 block text-sm leading-5 text-slate-600">{item.description}</span>}<time className="mt-1 block text-xs text-slate-500" dateTime={item.createdAt}>{formatDistanceToNow(new Date(item.createdAt), { addSuffix: true, locale: vi })}</time></span></div>
  </button>;
}
