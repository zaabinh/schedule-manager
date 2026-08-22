"use client";

import { CalendarClock, Check } from "lucide-react";
import type { DisplayTaskStatus, Task } from "@/types/domain";
import { formatDateTime } from "@/lib/utils";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { TaskAttachments } from "@/components/task/task-attachments";

export function getDisplayStatus(task: Task): DisplayTaskStatus {
  if (task.displayStatus) return task.displayStatus;
  if (task.status === "COMPLETED") return "COMPLETED";
  return new Date(task.dueAt).getTime() < Date.now() ? "OVERDUE" : "TODO";
}

export function TaskCard({ task, onComplete, admin = false }: { task: Task; onComplete?: (id: string) => void; admin?: boolean }) {
  const status = getDisplayStatus(task);
  return <article className="card p-5">
    <div className="flex flex-col justify-between gap-3 sm:flex-row">
      <div><h3 className="font-bold text-slate-900">{task.title}</h3>{task.description && <p className="mt-1 text-sm text-slate-500">{task.description}</p>}</div>
      <StatusBadge status={status}/>
    </div>
    <div className="mt-4 flex flex-col justify-between gap-3 border-t pt-4 sm:flex-row sm:items-center">
      <span className="flex items-center gap-2 text-sm text-slate-600"><CalendarClock size={17}/><span><span className="text-xs text-slate-500">Hạn hoàn thành</span><br/><strong>{formatDateTime(task.dueAt)}</strong></span></span>
      {task.status !== "COMPLETED" && onComplete && <Button size="sm" onClick={() => onComplete(task.id)}><Check size={16}/>Đánh dấu hoàn thành</Button>}
    </div>
    <TaskAttachments task={task} admin={admin}/>
  </article>;
}
